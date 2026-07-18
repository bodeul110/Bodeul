const {getApp} = require("firebase-admin/app");
const {FieldPath, FieldValue, getFirestore} = require("firebase-admin/firestore");
const {getStorage} = require("firebase-admin/storage");
const logger = require("firebase-functions/logger");
const {defineBoolean, defineSecret} = require("firebase-functions/params");
const {onSchedule} = require("firebase-functions/v2/scheduler");
const postgres = require("postgres");

const RETENTION_DATABASE_URL = defineSecret("RETENTION_DATABASE_URL");
const RETENTION_APPLY_ENABLED = defineBoolean("RETENTION_APPLY_ENABLED", {
  default: false,
  description: "자동 파기 예약 작업의 실제 삭제 활성화 여부",
});

const RETENTION_SCHEDULE_OPTIONS = {
  region: "asia-northeast3",
  schedule: "45 4 * * *",
  timeZone: "Asia/Seoul",
  timeoutSeconds: 540,
  memory: "256MiB",
  maxInstances: 1,
  retryCount: 1,
  secrets: [RETENTION_DATABASE_URL],
};

const RETENTION_MONTHLY_REPORT_OPTIONS = {
  region: "asia-northeast3",
  schedule: "15 5 1 * *",
  timeZone: "Asia/Seoul",
  timeoutSeconds: 120,
  memory: "256MiB",
  maxInstances: 1,
  retryCount: 1,
  secrets: [RETENTION_DATABASE_URL],
};

const DAY_IN_MILLIS = 24 * 60 * 60 * 1000;
const MANAGER_DOCUMENT_RETENTION_MILLIS = 30 * DAY_IN_MILLIS;
const POSTGRES_BATCH_SIZE = 500;
const MANAGER_DOCUMENT_KEYS = [
  "idCard",
  "license",
  "healthCertificate",
  "criminalRecord",
];
const MANAGER_DOCUMENT_LEGACY_PATH_KEYS = {
  idCard: "managerIdCardStoragePath",
  license: "managerLicenseStoragePath",
  healthCertificate: null,
  criminalRecord: "managerCriminalRecordStoragePath",
};
const REVIEWED_MANAGER_DOCUMENT_STATUSES = new Set(["APPROVED", "REJECTED"]);

class PostgresRetentionRepository {
  constructor(databaseUrl) {
    if (!sanitizeText(databaseUrl)) {
      throw createRetentionError("DATABASE_CONFIG_MISSING");
    }
    this.sql = postgres(databaseUrl, {
      max: 1,
      prepare: false,
      ssl: {rejectUnauthorized: true},
      connect_timeout: 10,
      idle_timeout: 5,
      max_lifetime: 60,
      connection: {application_name: "bodeul-retention-job"},
    });
  }

  async beginJob(mode, asOf, startedAt) {
    const rows = await this.sql`
      select bodeul.begin_retention_job(
        ${mode},
        ${asOf.toISOString()}::timestamptz,
        ${startedAt.toISOString()}::timestamptz
      ) as job_id
    `;
    return sanitizeText(rows[0]?.job_id);
  }

  async preview(asOf) {
    const rows = await this.sql`
      select *
      from bodeul.preview_expired_companion_data(${asOf.toISOString()}::timestamptz)
    `;
    const row = rows[0] || {};
    return {
      messageCandidates: toCount(row.message_candidates),
      attachmentCandidates: toCount(row.attachment_candidates),
      locationCandidates: toCount(row.location_candidates),
      legalHoldSkips: toCount(row.legal_hold_skips),
    };
  }

  async claimAttachments(asOf, limit) {
    const rows = await this.sql`
      select *
      from bodeul.claim_expired_companion_attachments(
        ${asOf.toISOString()}::timestamptz,
        ${limit}
      )
    `;
    return rows.map((row) => ({
      id: sanitizeText(row.attachment_id),
      storagePath: sanitizeText(row.storage_path),
    }));
  }

  async markAttachmentDeleted(attachment, deletedAt) {
    const rows = await this.sql`
      select bodeul.mark_companion_attachment_deleted(
        ${attachment.id}::uuid,
        ${attachment.storagePath},
        ${deletedAt.toISOString()}::timestamptz
      ) as marked
    `;
    return rows[0]?.marked === true;
  }

  async purgeCompanionRecords(asOf, limit) {
    const rows = await this.sql`
      select *
      from bodeul.purge_expired_companion_records(
        ${asOf.toISOString()}::timestamptz,
        ${limit}
      )
    `;
    const row = rows[0] || {};
    return {
      messagesRedacted: toCount(row.messages_redacted),
      locationsDeleted: toCount(row.locations_deleted),
    };
  }

  async finishJob(jobId, status, finishedAt, summary, failureStage = null) {
    const counts = retentionCounts(summary);
    const rows = await this.sql`
      select bodeul.finish_retention_job(
        ${jobId}::uuid,
        ${status},
        ${finishedAt.toISOString()}::timestamptz,
        ${JSON.stringify(counts)}::jsonb,
        ${failureStage}
      ) as finished
    `;
    return rows[0]?.finished === true;
  }

  async monthlySummary(monthStart) {
    const rows = await this.sql`
      select *
      from bodeul.retention_monthly_summary(${formatDate(monthStart)}::date)
    `;
    const row = rows[0] || {};
    return {
      month: formatDate(monthStart).slice(0, 7),
      runCount: toCount(row.run_count),
      failedRunCount: toCount(row.failed_run_count),
      messageCandidates: toCount(row.message_candidates),
      attachmentCandidates: toCount(row.attachment_candidates),
      locationCandidates: toCount(row.location_candidates),
      managerDocumentCandidates: toCount(row.manager_document_candidates),
      messagesRedacted: toCount(row.messages_redacted),
      attachmentsDeleted: toCount(row.attachments_deleted),
      attachmentDeleteFailures: toCount(row.attachment_delete_failures),
      locationsDeleted: toCount(row.locations_deleted),
      managerDocumentsDeleted: toCount(row.manager_documents_deleted),
      managerDocumentDeleteFailures: toCount(row.manager_document_delete_failures),
      legalHoldSkips: toCount(row.legal_hold_skips),
    };
  }

  async close() {
    await this.sql.end({timeout: 5});
  }
}

class FirebaseManagerDocumentStore {
  constructor(firestore) {
    this.firestore = firestore;
  }

  async preview(asOf) {
    const snapshot = await this.firestore
        .collection("users")
        .where("role", "==", "MANAGER")
        .get();
    const result = {
      candidates: [],
      legalHoldSkips: 0,
    };
    for (const document of snapshot.docs) {
      const evaluation = evaluateManagerDocument(
          document.id,
          document.data(),
          asOf,
      );
      result.candidates.push(...evaluation.candidates);
      result.legalHoldSkips += evaluation.legalHoldSkips;
    }
    return result;
  }

  async isStillEligible(candidate, asOf) {
    const snapshot = await this.firestore.collection("users").doc(candidate.managerId).get();
    if (!snapshot.exists) {
      return false;
    }
    return evaluateManagerDocument(snapshot.id, snapshot.data(), asOf)
        .candidates
        .some((current) => sameManagerDocumentCandidate(current, candidate));
  }

  async clearReference(candidate, deletedAt) {
    const reference = this.firestore.collection("users").doc(candidate.managerId);
    return this.firestore.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(reference);
      if (!snapshot.exists) {
        return false;
      }
      const currentCandidate = evaluateManagerDocument(
          snapshot.id,
          snapshot.data(),
          deletedAt,
      ).candidates.find((item) => sameManagerDocumentCandidate(item, candidate));
      if (!currentCandidate) {
        return false;
      }

      const updates = {
        [`managerDocumentFiles.${candidate.documentKey}`]: FieldValue.delete(),
        [`managerDocumentFilePaths.${candidate.documentKey}`]: FieldValue.delete(),
        managerDocumentOriginalsDeletedAt: FieldValue.serverTimestamp(),
      };
      const legacyKey = MANAGER_DOCUMENT_LEGACY_PATH_KEYS[candidate.documentKey];
      if (legacyKey && sanitizeText(snapshot.data()?.[legacyKey]) === candidate.storagePath) {
        updates[legacyKey] = FieldValue.delete();
      }
      transaction.update(reference, updates);
      return true;
    });
  }
}

class FirebaseLegacyCompanionStore {
  constructor(firestore) {
    this.firestore = firestore;
  }

  async preview(asOf) {
    const collection = this.firestore.collection("companionSessions");
    const sessions = [];
    const summary = {
      messageCandidates: 0,
      attachmentCandidates: 0,
      locationCandidates: 0,
      legalHoldSkips: 0,
    };
    let lastDocument = null;
    do {
      let query = collection.orderBy(FieldPath.documentId()).limit(500);
      if (lastDocument) {
        query = query.startAfter(lastDocument);
      }
      const snapshot = await query.get();
      for (const document of snapshot.docs) {
        const evaluation = evaluateLegacyCompanionSession(
            document.id,
            document.data(),
            asOf,
        );
        if (evaluation.hasWork) {
          sessions.push({sessionId: document.id});
        }
        summary.messageCandidates += evaluation.messageCandidates;
        summary.attachmentCandidates += evaluation.attachments.length;
        summary.locationCandidates += evaluation.locationEligible ? 1 : 0;
        summary.legalHoldSkips += evaluation.legalHoldSkips;
      }
      lastDocument = snapshot.docs.length === 500
        ? snapshot.docs[snapshot.docs.length - 1]
        : null;
    } while (lastDocument);
    return {sessions, ...summary};
  }

  async applySession(candidate, asOf, storage) {
    const reference = this.firestore.collection("companionSessions").doc(candidate.sessionId);
    const snapshot = await reference.get();
    if (!snapshot.exists) {
      return emptyLegacyApplyResult();
    }
    const evaluation = evaluateLegacyCompanionSession(snapshot.id, snapshot.data(), asOf);
    if (!evaluation.hasWork) {
      return emptyLegacyApplyResult();
    }

    const deletedPaths = new Set();
    let attachmentDeleteFailures = 0;
    for (const attachment of evaluation.attachments) {
      try {
        await storage.deleteChatAttachment(attachment.storagePath);
        deletedPaths.add(attachment.storagePath);
      } catch (error) {
        attachmentDeleteFailures += 1;
      }
    }

    const result = await this.firestore.runTransaction(async (transaction) => {
      const currentSnapshot = await transaction.get(reference);
      if (!currentSnapshot.exists) {
        return emptyLegacyApplyResult();
      }
      const currentData = currentSnapshot.data();
      const currentEvaluation = evaluateLegacyCompanionSession(
          currentSnapshot.id,
          currentData,
          asOf,
      );
      if (!currentEvaluation.hasWork) {
        return emptyLegacyApplyResult();
      }

      const removablePaths = new Set(
          currentEvaluation.attachments
              .map((item) => item.storagePath)
              .filter((storagePath) => deletedPaths.has(storagePath)),
      );
      const redacted = redactLegacyChatMessages(
          currentData.chatMessages,
          removablePaths,
          currentEvaluation.messageCandidates > 0,
      );
      const updates = {};
      if (redacted.changed) {
        updates.chatMessages = redacted.messages;
      }
      if (currentEvaluation.locationEligible) {
        updates.sharedLatitude = FieldValue.delete();
        updates.sharedLongitude = FieldValue.delete();
        updates.sharedLocationUpdatedAt = FieldValue.delete();
        updates.sharedLocationHistory = FieldValue.delete();
        updates.liveLocationSharingActive = false;
        updates.liveLocationSharingStartedAt = FieldValue.delete();
      }
      if (!Object.keys(updates).length) {
        return emptyLegacyApplyResult();
      }
      updates.retentionUpdatedAt = FieldValue.serverTimestamp();
      transaction.update(reference, updates);
      return {
        messagesRedacted: redacted.messagesRedacted,
        attachmentsDeleted: redacted.attachmentsRemoved,
        locationsCleared: currentEvaluation.locationEligible ? 1 : 0,
      };
    });

    return {...result, attachmentDeleteFailures};
  }
}

class FirebaseStorageGateway {
  constructor(bucket) {
    this.bucket = bucket;
  }

  async deleteChatAttachment(storagePath) {
    if (!isAllowedChatAttachmentPath(storagePath)) {
      throw createRetentionError("CHAT_STORAGE_PATH_INVALID");
    }
    await this.bucket.file(storagePath).delete({ignoreNotFound: true});
  }

  async deleteManagerDocument(storagePath) {
    if (!isAllowedManagerDocumentPath(storagePath)) {
      throw createRetentionError("MANAGER_STORAGE_PATH_INVALID");
    }
    await this.bucket.file(storagePath).delete({ignoreNotFound: true});
  }
}

async function runRetentionJob({
  database,
  legacyStore,
  managerStore,
  storage,
  apply,
  now = new Date(),
}) {
  const asOf = new Date(now.getTime());
  const mode = apply ? "APPLY" : "DRY_RUN";
  const summary = emptyRetentionSummary(mode, asOf);
  let jobId = "";
  let failureStage = "BEGIN";

  try {
    jobId = await database.beginJob(mode, asOf, now);
    if (!jobId) {
      throw createRetentionError("JOB_ID_MISSING");
    }

    failureStage = "PREVIEW_POSTGRES";
    const postgresPreview = await database.preview(asOf);
    summary.postgresMessageCandidates = postgresPreview.messageCandidates;
    summary.postgresAttachmentCandidates = postgresPreview.attachmentCandidates;
    summary.postgresLocationCandidates = postgresPreview.locationCandidates;
    summary.postgresLegalHoldSkips = postgresPreview.legalHoldSkips;

    failureStage = "PREVIEW_FIRESTORE";
    const firestorePreview = await legacyStore.preview(asOf);
    summary.firestoreMessageCandidates = firestorePreview.messageCandidates;
    summary.firestoreAttachmentCandidates = firestorePreview.attachmentCandidates;
    summary.firestoreLocationCandidates = firestorePreview.locationCandidates;
    summary.firestoreLegalHoldSkips = firestorePreview.legalHoldSkips;

    failureStage = "PREVIEW_MANAGER_DOCUMENTS";
    const managerPreview = await managerStore.preview(asOf);
    summary.managerDocumentCandidates = managerPreview.candidates.length;
    summary.managerDocumentLegalHoldSkips = managerPreview.legalHoldSkips;

    if (apply) {
      failureStage = "DELETE_CHAT_ATTACHMENTS";
      const attachments = await database.claimAttachments(asOf, POSTGRES_BATCH_SIZE);
      for (const attachment of attachments) {
        try {
          await storage.deleteChatAttachment(attachment.storagePath);
          const marked = await database.markAttachmentDeleted(attachment, asOf);
          if (marked) {
            summary.attachmentsDeleted += 1;
          } else {
            summary.attachmentDeleteFailures += 1;
          }
        } catch (error) {
          summary.attachmentDeleteFailures += 1;
        }
      }

      failureStage = "PURGE_POSTGRES";
      const purged = await database.purgeCompanionRecords(asOf, POSTGRES_BATCH_SIZE);
      summary.messagesRedacted = purged.messagesRedacted;
      summary.locationsDeleted = purged.locationsDeleted;

      failureStage = "PURGE_FIRESTORE";
      for (const session of firestorePreview.sessions) {
        const result = await legacyStore.applySession(session, asOf, storage);
        summary.firestoreMessagesRedacted += result.messagesRedacted;
        summary.firestoreAttachmentsDeleted += result.attachmentsDeleted;
        summary.firestoreAttachmentDeleteFailures += result.attachmentDeleteFailures;
        summary.firestoreLocationsCleared += result.locationsCleared;
      }

      failureStage = "DELETE_MANAGER_DOCUMENTS";
      for (const candidate of managerPreview.candidates) {
        try {
          if (!await managerStore.isStillEligible(candidate, asOf)) {
            continue;
          }
          await storage.deleteManagerDocument(candidate.storagePath);
          const cleared = await managerStore.clearReference(candidate, asOf);
          if (cleared) {
            summary.managerDocumentsDeleted += 1;
          } else {
            summary.managerDocumentDeleteFailures += 1;
          }
        } catch (error) {
          summary.managerDocumentDeleteFailures += 1;
        }
      }
    }

    failureStage = "FINISH";
    const finished = await database.finishJob(jobId, "COMPLETED", new Date(), summary);
    if (!finished) {
      throw createRetentionError("JOB_FINISH_REJECTED");
    }
    return summary;
  } catch (error) {
    if (jobId) {
      try {
        await database.finishJob(
            jobId,
            "FAILED",
            new Date(),
            summary,
            failureStage,
        );
      } catch (recordingError) {
        logger.error("자동 파기 실패 기록을 저장하지 못했습니다.", {
          failureStage,
          errorCode: retentionErrorCode(recordingError),
        });
      }
    }
    const retainedError = error instanceof Error
      ? error
      : createRetentionError("RETENTION_JOB_FAILED");
    retainedError.retentionFailureStage = failureStage;
    throw retainedError;
  }
}

function evaluateManagerDocument(managerId, data, asOf) {
  const result = {candidates: [], legalHoldSkips: 0};
  if (sanitizeText(data?.role) !== "MANAGER") {
    return result;
  }
  const status = sanitizeText(data?.managerDocumentStatus);
  if (!REVIEWED_MANAGER_DOCUMENT_STATUSES.has(status)) {
    return result;
  }

  const reviewedAtMillis = toMillis(data?.managerDocumentReviewedAt);
  const updatedAtMillis = toMillis(data?.managerDocumentUpdatedAt);
  const cutoffMillis = asOf.getTime() - MANAGER_DOCUMENT_RETENTION_MILLIS;
  if (!reviewedAtMillis || reviewedAtMillis > cutoffMillis) {
    return result;
  }
  if (!updatedAtMillis || updatedAtMillis > reviewedAtMillis) {
    return result;
  }

  const references = collectManagerDocumentReferences(data);
  if (!references.length) {
    return result;
  }
  const legalHoldUntilMillis = toMillis(data?.managerDocumentLegalHoldUntil);
  if (hasTimestampValue(data, "managerDocumentLegalHoldUntil") && !legalHoldUntilMillis) {
    result.legalHoldSkips = references.length;
    return result;
  }
  if (legalHoldUntilMillis > asOf.getTime()) {
    result.legalHoldSkips = references.length;
    return result;
  }

  for (const reference of references) {
    const uploadedAtMillis = toMillis(reference.uploadedAt);
    if (uploadedAtMillis && uploadedAtMillis > reviewedAtMillis) {
      continue;
    }
    result.candidates.push({
      managerId,
      documentKey: reference.documentKey,
      storagePath: reference.storagePath,
    });
  }
  return result;
}

function collectManagerDocumentReferences(data) {
  const fileMap = isPlainObject(data?.managerDocumentFiles)
    ? data.managerDocumentFiles
    : {};
  const pathMap = isPlainObject(data?.managerDocumentFilePaths)
    ? data.managerDocumentFilePaths
    : {};
  const references = [];

  for (const documentKey of MANAGER_DOCUMENT_KEYS) {
    const metadata = isPlainObject(fileMap[documentKey]) ? fileMap[documentKey] : {};
    const legacyKey = MANAGER_DOCUMENT_LEGACY_PATH_KEYS[documentKey];
    const paths = Array.from(new Set([
      sanitizeText(metadata.fullPath),
      sanitizeText(pathMap[documentKey]),
      legacyKey ? sanitizeText(data?.[legacyKey]) : "",
    ].filter(Boolean)));
    if (paths.length !== 1 || !isAllowedManagerDocumentPath(paths[0])) {
      continue;
    }
    references.push({
      documentKey,
      storagePath: paths[0],
      uploadedAt: metadata.uploadedAt,
    });
  }
  return references;
}

function evaluateLegacyCompanionSession(sessionId, data, asOf) {
  const empty = {
    sessionId,
    hasWork: false,
    messageCandidates: 0,
    attachments: [],
    locationEligible: false,
    legalHoldSkips: 0,
  };
  const status = sanitizeText(data?.currentStatus);
  if (status !== "COMPLETED" && status !== "CANCELED") {
    return empty;
  }
  const terminalAtMillis = toMillis(
      data?.completedAt || data?.canceledAt || data?.updatedAt,
  );
  if (!terminalAtMillis) {
    return empty;
  }

  const locationExpired = terminalAtMillis + DAY_IN_MILLIS <= asOf.getTime();
  const attachmentExpired = terminalAtMillis + (30 * DAY_IN_MILLIS) <= asOf.getTime();
  const messageExpired = terminalAtMillis + (180 * DAY_IN_MILLIS) <= asOf.getTime();
  const messages = Array.isArray(data?.chatMessages) ? data.chatMessages : [];
  const attachments = attachmentExpired
    ? collectLegacyChatAttachments(messages)
    : [];
  const messageCandidates = messageExpired
    ? messages.filter(hasLegacyMessageBody).length
    : 0;
  const locationEligible = locationExpired && hasLegacyPreciseLocation(data);
  const totalCandidates = attachments.length + messageCandidates + (locationEligible ? 1 : 0);
  const legalHoldUntilMillis = toMillis(data?.legalHoldUntil);
  if (hasTimestampValue(data, "legalHoldUntil") && !legalHoldUntilMillis) {
    return {...empty, legalHoldSkips: totalCandidates};
  }
  if (legalHoldUntilMillis > asOf.getTime()) {
    return {...empty, legalHoldSkips: totalCandidates};
  }
  return {
    sessionId,
    hasWork: totalCandidates > 0,
    messageCandidates,
    attachments,
    locationEligible,
    legalHoldSkips: 0,
  };
}

function collectLegacyChatAttachments(messages) {
  const attachments = [];
  const paths = new Set();
  for (const message of messages) {
    if (!isPlainObject(message)) {
      continue;
    }
    const values = [];
    if (Array.isArray(message.attachments)) {
      values.push(...message.attachments);
    }
    if (isPlainObject(message.attachment)) {
      values.push(message.attachment);
    }
    for (const value of values) {
      const storagePath = sanitizeText(value?.fullPath);
      if (!isAllowedChatAttachmentPath(storagePath) || paths.has(storagePath)) {
        continue;
      }
      paths.add(storagePath);
      attachments.push({storagePath});
    }
  }
  return attachments;
}

function redactLegacyChatMessages(rawMessages, removablePaths, redactBodies) {
  const messages = Array.isArray(rawMessages) ? rawMessages : [];
  let messagesRedacted = 0;
  let attachmentsRemoved = 0;
  let changed = false;
  const countedAttachmentPaths = new Set();
  const redactedMessages = messages.map((rawMessage) => {
    if (!isPlainObject(rawMessage)) {
      return rawMessage;
    }
    const message = {...rawMessage};
    if (redactBodies && hasLegacyMessageBody(message)) {
      if (Object.prototype.hasOwnProperty.call(message, "body")) {
        message.body = "";
      }
      if (Object.prototype.hasOwnProperty.call(message, "message")) {
        message.message = "";
      }
      messagesRedacted += 1;
      changed = true;
    }
    if (Array.isArray(message.attachments)) {
      const remaining = message.attachments.filter((attachment) => {
        const remove = removablePaths.has(sanitizeText(attachment?.fullPath));
        const storagePath = sanitizeText(attachment?.fullPath);
        if (remove && !countedAttachmentPaths.has(storagePath)) {
          attachmentsRemoved += 1;
          countedAttachmentPaths.add(storagePath);
        }
        return !remove;
      });
      if (remaining.length !== message.attachments.length) {
        message.attachments = remaining;
        changed = true;
      }
    }
    if (isPlainObject(message.attachment) &&
        removablePaths.has(sanitizeText(message.attachment.fullPath))) {
      const storagePath = sanitizeText(message.attachment.fullPath);
      delete message.attachment;
      if (!countedAttachmentPaths.has(storagePath)) {
        attachmentsRemoved += 1;
        countedAttachmentPaths.add(storagePath);
      }
      changed = true;
    }
    return message;
  });
  return {messages: redactedMessages, messagesRedacted, attachmentsRemoved, changed};
}

function hasLegacyMessageBody(message) {
  return Boolean(sanitizeText(message?.body) || sanitizeText(message?.message));
}

function hasLegacyPreciseLocation(data) {
  return (typeof data?.sharedLatitude === "number" && Number.isFinite(data.sharedLatitude)) ||
    (typeof data?.sharedLongitude === "number" && Number.isFinite(data.sharedLongitude)) ||
    toMillis(data?.sharedLocationUpdatedAt) > 0 ||
    (Array.isArray(data?.sharedLocationHistory) && data.sharedLocationHistory.length > 0) ||
    Boolean(data?.liveLocationSharingActive) ||
    toMillis(data?.liveLocationSharingStartedAt) > 0;
}

function emptyLegacyApplyResult() {
  return {
    messagesRedacted: 0,
    attachmentsDeleted: 0,
    attachmentDeleteFailures: 0,
    locationsCleared: 0,
  };
}

function sameManagerDocumentCandidate(left, right) {
  return left.managerId === right.managerId &&
    left.documentKey === right.documentKey &&
    left.storagePath === right.storagePath;
}

function isAllowedChatAttachmentPath(value) {
  return /^companion-chat-attachments\/[A-Za-z0-9_-]{1,128}\/[^/]+$/
      .test(sanitizeText(value));
}

function isAllowedManagerDocumentPath(value) {
  return /^manager-documents\/[^/]+\/(idCard|license|healthCertificate|criminalRecord)\/[^/]+$/
      .test(sanitizeText(value));
}

function emptyRetentionSummary(mode, asOf) {
  return {
    mode,
    asOf: asOf.toISOString(),
    postgresMessageCandidates: 0,
    postgresAttachmentCandidates: 0,
    postgresLocationCandidates: 0,
    postgresLegalHoldSkips: 0,
    firestoreMessageCandidates: 0,
    firestoreAttachmentCandidates: 0,
    firestoreLocationCandidates: 0,
    firestoreLegalHoldSkips: 0,
    managerDocumentCandidates: 0,
    managerDocumentLegalHoldSkips: 0,
    messagesRedacted: 0,
    attachmentsDeleted: 0,
    attachmentDeleteFailures: 0,
    locationsDeleted: 0,
    firestoreMessagesRedacted: 0,
    firestoreAttachmentsDeleted: 0,
    firestoreAttachmentDeleteFailures: 0,
    firestoreLocationsCleared: 0,
    managerDocumentsDeleted: 0,
    managerDocumentDeleteFailures: 0,
  };
}

function retentionCounts(summary) {
  const counts = {...summary};
  delete counts.mode;
  delete counts.asOf;
  return counts;
}

function resolveStorageBucketName() {
  const projectId = sanitizeText(process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT);
  if (!projectId) {
    throw createRetentionError("FIREBASE_PROJECT_MISSING");
  }
  return `${projectId}.firebasestorage.app`;
}

function createRuntimeDependencies(databaseUrl) {
  const database = new PostgresRetentionRepository(databaseUrl);
  const legacyStore = new FirebaseLegacyCompanionStore(getFirestore());
  const managerStore = new FirebaseManagerDocumentStore(getFirestore());
  const bucket = getStorage(getApp()).bucket(resolveStorageBucketName());
  const storage = new FirebaseStorageGateway(bucket);
  return {database, legacyStore, managerStore, storage};
}

async function runConfiguredRetentionJob({databaseUrl, apply, now = new Date()}) {
  const dependencies = createRuntimeDependencies(databaseUrl);
  try {
    return await runRetentionJob({...dependencies, apply, now});
  } finally {
    await dependencies.database.close();
  }
}

function previousMonthStart(now) {
  const koreaTime = new Date(now.getTime() + (9 * 60 * 60 * 1000));
  return new Date(Date.UTC(koreaTime.getUTCFullYear(), koreaTime.getUTCMonth() - 1, 1));
}

function formatDate(value) {
  return value.toISOString().slice(0, 10);
}

function toMillis(value) {
  if (value instanceof Date) {
    return value.getTime();
  }
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (value && typeof value.toMillis === "function") {
    return Number(value.toMillis()) || 0;
  }
  if (typeof value === "string") {
    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }
  return 0;
}

function hasTimestampValue(data, key) {
  return isPlainObject(data) &&
    Object.prototype.hasOwnProperty.call(data, key) &&
    data[key] !== null &&
    data[key] !== "";
}

function toCount(value) {
  const number = Number(value);
  return Number.isSafeInteger(number) && number >= 0 ? number : 0;
}

function sanitizeText(value) {
  return typeof value === "string" ? value.trim() : "";
}

function isPlainObject(value) {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function createRetentionError(code) {
  const error = new Error(code);
  error.code = code;
  return error;
}

function retentionErrorCode(error) {
  const value = sanitizeText(error?.code || error?.name || "RETENTION_JOB_FAILED");
  const normalized = value.replace(/[^A-Za-z0-9_.-]/g, "_").slice(0, 64);
  return normalized || "RETENTION_JOB_FAILED";
}

const cleanupExpiredData = onSchedule(RETENTION_SCHEDULE_OPTIONS, async () => {
  try {
    const summary = await runConfiguredRetentionJob({
      databaseUrl: RETENTION_DATABASE_URL.value(),
      apply: RETENTION_APPLY_ENABLED.value(),
    });
    logger.info("개인정보 자동 파기 작업을 마쳤습니다.", summary);
  } catch (error) {
    logger.error("개인정보 자동 파기 작업이 실패했습니다.", {
      failureStage: sanitizeText(error?.retentionFailureStage) || "UNKNOWN",
      errorCode: retentionErrorCode(error),
    });
    throw error;
  }
});

const reportMonthlyRetention = onSchedule(
    RETENTION_MONTHLY_REPORT_OPTIONS,
    async () => {
      const database = new PostgresRetentionRepository(RETENTION_DATABASE_URL.value());
      try {
        const summary = await database.monthlySummary(previousMonthStart(new Date()));
        logger.info("월간 개인정보 파기 집계를 생성했습니다.", summary);
      } finally {
        await database.close();
      }
    },
);

module.exports = {
  cleanupExpiredData,
  reportMonthlyRetention,
  PostgresRetentionRepository,
  FirebaseManagerDocumentStore,
  FirebaseLegacyCompanionStore,
  FirebaseStorageGateway,
  collectManagerDocumentReferences,
  emptyRetentionSummary,
  evaluateManagerDocument,
  evaluateLegacyCompanionSession,
  isAllowedChatAttachmentPath,
  isAllowedManagerDocumentPath,
  previousMonthStart,
  retentionErrorCode,
  runConfiguredRetentionJob,
  runRetentionJob,
  toMillis,
};
