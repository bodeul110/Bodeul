const {Timestamp} = require("firebase-admin/firestore");

const {
  FirebaseLegacyCompanionStore,
  FirebaseManagerDocumentStore,
  FirebaseStorageGateway,
  evaluateLegacyCompanionSession,
  evaluateManagerDocument,
  isAllowedChatAttachmentPath,
  isAllowedManagerDocumentPath,
  runRetentionJob,
} = require("../../src/retention");

const FIXTURE_OWNER = "bodeul110/Bodeul";
const FIXTURE_ISSUE = "222";
const DAY_IN_MILLIS = 24 * 60 * 60 * 1000;
const DEVELOPMENT_PROFILE = createFixtureProfile({
  projectId: "bodeul-dev",
  marker: "bodeul-retention-firebase-v1",
  idPrefix: "retention-fixture",
  messagePrefix: "개발",
});
const PRODUCTION_PROFILE = createFixtureProfile({
  projectId: "bodeul-prod-110",
  marker: "bodeul-retention-firebase-production-v1",
  idPrefix: "retention-fixture-production",
  messagePrefix: "production",
});
const DEVELOPMENT_PROJECT_ID = DEVELOPMENT_PROFILE.projectId;
const FIXTURE_MARKER = DEVELOPMENT_PROFILE.marker;
const SESSION_IDS = DEVELOPMENT_PROFILE.sessionIds;
const MANAGER_IDS = DEVELOPMENT_PROFILE.managerIds;
const OBJECT_PATHS = DEVELOPMENT_PROFILE.objectPaths;

function createFixtureProfile({projectId, marker, idPrefix, messagePrefix}) {
  const sessionExpiredId = `${idPrefix}-firestore-expired-v1`;
  const sessionHeldId = `${idPrefix}-firestore-held-v1`;
  const managerExpiredId = `${idPrefix}-manager-expired-v1`;
  const managerHeldId = `${idPrefix}-manager-held-v1`;
  return Object.freeze({
    projectId,
    marker,
    owner: FIXTURE_OWNER,
    issue: FIXTURE_ISSUE,
    messagePrefix,
    sessionIds: Object.freeze([sessionExpiredId, sessionHeldId]),
    managerIds: Object.freeze([managerExpiredId, managerHeldId]),
    documentIds: Object.freeze({
      sessionExpired: sessionExpiredId,
      sessionHeld: sessionHeldId,
      managerExpired: managerExpiredId,
      managerHeld: managerHeldId,
    }),
    objectPaths: Object.freeze({
      sessionExpired: `companion-chat-attachments/${sessionExpiredId}/fixture.pdf`,
      sessionHeld: `companion-chat-attachments/${sessionHeldId}/fixture.pdf`,
      managerExpired: `manager-documents/${managerExpiredId}/idCard/fixture.pdf`,
      managerHeld: `manager-documents/${managerHeldId}/nursingLicense/fixture.pdf`,
    }),
  });
}

function buildFixtureDefinition(now = new Date(), profile = DEVELOPMENT_PROFILE) {
  assertValidDate(now);
  assertFixtureProfile(profile);
  const {documentIds, objectPaths} = profile;
  const createdAt = Timestamp.fromDate(new Date(now.getTime()));
  const sessionExpiredAt = Timestamp.fromDate(
      new Date(now.getTime() - (200 * DAY_IN_MILLIS)),
  );
  const managerExpiredAt = Timestamp.fromDate(
      new Date(now.getTime() - (31 * DAY_IN_MILLIS)),
  );
  const heldUntil = Timestamp.fromDate(
      new Date(now.getTime() + (7 * DAY_IN_MILLIS)),
  );
  const marker = {
    name: profile.marker,
    owner: profile.owner,
    issue: profile.issue,
    createdAt,
  };

  return {
    documents: {
      sessionExpired: {
        path: `companionSessions/${documentIds.sessionExpired}`,
        data: {
          bodeulFixture: marker,
          currentStatus: "COMPLETED",
          completedAt: sessionExpiredAt,
          updatedAt: sessionExpiredAt,
          chatMessages: [{
            body: `${profile.messagePrefix} 파기 픽스처 메시지`,
            attachments: [{fullPath: objectPaths.sessionExpired}],
          }],
          sharedLatitude: 37.5665,
          sharedLongitude: 126.978,
          sharedLocationUpdatedAt: sessionExpiredAt,
          sharedLocationHistory: [{latitude: 37.5665, longitude: 126.978}],
          liveLocationSharingActive: true,
          liveLocationSharingStartedAt: sessionExpiredAt,
        },
      },
      sessionHeld: {
        path: `companionSessions/${documentIds.sessionHeld}`,
        data: {
          bodeulFixture: marker,
          currentStatus: "COMPLETED",
          completedAt: sessionExpiredAt,
          updatedAt: sessionExpiredAt,
          legalHoldUntil: heldUntil,
          chatMessages: [{
            body: `${profile.messagePrefix} legal hold 픽스처 메시지`,
            attachments: [{fullPath: objectPaths.sessionHeld}],
          }],
          sharedLatitude: 37.5665,
          sharedLongitude: 126.978,
          sharedLocationHistory: [{latitude: 37.5665, longitude: 126.978}],
          liveLocationSharingActive: true,
        },
      },
      managerExpired: {
        path: `users/${documentIds.managerExpired}`,
        data: {
          bodeulFixture: marker,
          role: "MANAGER",
          email: "retention-fixture-expired@bodeul.invalid",
          managerDocumentStatus: "APPROVED",
          managerDocumentReviewedAt: managerExpiredAt,
          managerDocumentUpdatedAt: managerExpiredAt,
          managerDocumentFiles: {
            idCard: {
              fullPath: objectPaths.managerExpired,
              uploadedAt: managerExpiredAt,
            },
          },
          managerDocumentFilePaths: {idCard: objectPaths.managerExpired},
          managerIdCardStoragePath: objectPaths.managerExpired,
        },
      },
      managerHeld: {
        path: `users/${documentIds.managerHeld}`,
        data: {
          bodeulFixture: marker,
          role: "MANAGER",
          email: "retention-fixture-held@bodeul.invalid",
          managerDocumentStatus: "APPROVED",
          managerDocumentReviewedAt: managerExpiredAt,
          managerDocumentUpdatedAt: managerExpiredAt,
          managerDocumentLegalHoldUntil: heldUntil,
          managerDocumentFiles: {
            nursingLicense: {
              fullPath: objectPaths.managerHeld,
              uploadedAt: managerExpiredAt,
            },
          },
          managerDocumentFilePaths: {nursingLicense: objectPaths.managerHeld},
        },
      },
    },
    objects: Object.values(objectPaths),
  };
}

async function setupFixture({
  firestore,
  bucket,
  now = new Date(),
  profile = DEVELOPMENT_PROFILE,
}) {
  const before = await inspectFixture({firestore, bucket, now, profile});
  if (before.phase !== "ABSENT") {
    throw new Error(
        `픽스처가 비어 있지 않습니다(${before.phase}). cleanup 후 다시 실행해 주세요.`,
    );
  }

  const fixture = buildFixtureDefinition(now, profile);
  for (const objectPath of fixture.objects) {
    await bucket.file(objectPath).save(
        Buffer.from(`fixture:${profile.marker}:${objectPath}`),
        {
          resumable: false,
          metadata: {
            contentType: "application/pdf",
            metadata: {
              bodeulFixture: profile.marker,
              bodeulFixtureOwner: profile.owner,
              bodeulFixtureIssue: profile.issue,
            },
          },
          preconditionOpts: {ifGenerationMatch: 0},
        },
    );
  }

  const batch = firestore.batch();
  for (const document of Object.values(fixture.documents)) {
    batch.create(firestore.doc(document.path), document.data);
  }
  await batch.commit();

  const after = await inspectFixture({firestore, bucket, now, profile});
  assertFixturePhase(after, "READY");
  return after;
}

async function runFixtureRetention({
  firestore,
  bucket,
  apply,
  now = new Date(),
  profile = DEVELOPMENT_PROFILE,
}) {
  const before = await inspectFixture({firestore, bucket, now, profile});
  assertFixturePhase(before, "READY");

  const summary = await runRetentionJob({
    database: createFixtureDatabase(profile),
    legacyStore: createScopedLegacyStore(firestore, profile),
    managerStore: createScopedManagerStore(firestore, profile),
    storage: createScopedStorageGateway(bucket, profile),
    apply,
    now,
  });
  assertExpectedSummary(summary, apply);

  const after = await inspectFixture({firestore, bucket, now, profile});
  assertFixturePhase(after, apply ? "APPLIED" : "READY");
  return {summary, status: after};
}

function createScopedLegacyStore(firestore, profile = DEVELOPMENT_PROFILE) {
  const {sessionIds} = profile;
  const delegate = new FirebaseLegacyCompanionStore(firestore, {
    documentGuard: (documentId, data) =>
      sessionIds.includes(documentId) &&
      isFixtureMarker(data?.bodeulFixture, profile),
  });
  return {
    async preview(asOf) {
      const documents = await getDocumentsById(
          firestore.collection("companionSessions"),
          sessionIds,
      );
      const summary = {
        sessions: [],
        messageCandidates: 0,
        attachmentCandidates: 0,
        locationCandidates: 0,
        legalHoldSkips: 0,
      };
      for (const document of documents) {
        assertFixtureDocument(document, sessionIds, "동행 세션", profile);
        const evaluation = evaluateLegacyCompanionSession(
            document.id,
            document.data(),
            asOf,
        );
        if (evaluation.hasWork) {
          summary.sessions.push({sessionId: document.id});
        }
        summary.messageCandidates += evaluation.messageCandidates;
        summary.attachmentCandidates += evaluation.attachments.length;
        summary.locationCandidates += evaluation.locationEligible ? 1 : 0;
        summary.legalHoldSkips += evaluation.legalHoldSkips;
      }
      return summary;
    },
    async applySession(candidate, asOf, storage) {
      assertAllowedId(candidate?.sessionId, sessionIds, "동행 세션");
      return delegate.applySession(candidate, asOf, storage);
    },
  };
}

function createScopedManagerStore(firestore, profile = DEVELOPMENT_PROFILE) {
  const {managerIds} = profile;
  const delegate = new FirebaseManagerDocumentStore(firestore, {
    documentGuard: (documentId, data) =>
      managerIds.includes(documentId) &&
      isFixtureMarker(data?.bodeulFixture, profile),
  });
  return {
    async preview(asOf) {
      const documents = await getDocumentsById(
          firestore.collection("users"),
          managerIds,
      );
      const result = {candidates: [], legalHoldSkips: 0};
      for (const document of documents) {
        assertFixtureDocument(document, managerIds, "매니저", profile);
        const evaluation = evaluateManagerDocument(
            document.id,
            document.data(),
            asOf,
        );
        result.candidates.push(...evaluation.candidates);
        result.legalHoldSkips += evaluation.legalHoldSkips;
      }
      return result;
    },
    async isStillEligible(candidate, asOf) {
      assertAllowedId(candidate?.managerId, managerIds, "매니저");
      return delegate.isStillEligible(candidate, asOf);
    },
    async clearReference(candidate, deletedAt) {
      assertAllowedId(candidate?.managerId, managerIds, "매니저");
      return delegate.clearReference(candidate, deletedAt);
    },
  };
}

function createScopedStorageGateway(bucket, profile = DEVELOPMENT_PROFILE) {
  const allowedPaths = new Set(Object.values(profile.objectPaths));
  const scopedBucket = {
    file(storagePath) {
      assertAllowedStoragePath(storagePath, allowedPaths);
      return {
        async delete(options) {
          if (!isAllowedChatAttachmentPath(storagePath) &&
              !isAllowedManagerDocumentPath(storagePath)) {
            throw new Error("파기 허용 목록에 없는 Storage 경로입니다.");
          }
          const file = bucket.file(storagePath);
          const [metadata] = await file.getMetadata();
          if (!isFixtureObjectMetadata(metadata?.metadata, profile)) {
            throw new Error("Storage 객체의 픽스처 표식이 일치하지 않습니다.");
          }
          return bucket.file(storagePath, {generation: metadata.generation}).delete(options);
        },
      };
    },
  };
  return new FirebaseStorageGateway(scopedBucket);
}

async function getDocumentsById(collection, documentIds) {
  const snapshots = await Promise.all(
      documentIds.map((documentId) => collection.doc(documentId).get()),
  );
  return snapshots.filter((snapshot) => snapshot.exists);
}

function assertAllowedId(documentId, allowedIds, label) {
  if (!allowedIds.includes(String(documentId || ""))) {
    throw new Error(`${label} 픽스처 범위를 벗어난 문서는 처리하지 않습니다.`);
  }
}

function assertFixtureDocument(document, allowedIds, label, profile) {
  assertAllowedId(document?.id, allowedIds, label);
  if (!isFixtureMarker(document?.data()?.bodeulFixture, profile)) {
    throw new Error(`${label} 문서의 픽스처 표식이 일치하지 않습니다.`);
  }
}

function assertAllowedStoragePath(storagePath, allowedPaths) {
  if (!allowedPaths.has(String(storagePath || ""))) {
    throw new Error("픽스처 범위를 벗어난 Storage 객체는 처리하지 않습니다.");
  }
}

async function cleanupFixture({
  firestore,
  bucket,
  now = new Date(),
  profile = DEVELOPMENT_PROFILE,
}) {
  const before = await inspectFixture({firestore, bucket, now, profile});
  const ownership = await assertOwnedArtifacts({firestore, bucket, profile});

  for (const objectPath of Object.values(profile.objectPaths)) {
    const generation = ownership.objectGenerations[objectPath];
    if (generation) {
      await bucket.file(objectPath, {generation}).delete({ignoreNotFound: true});
    }
  }
  const batch = firestore.batch();
  for (const documentPath of fixtureDocumentPaths(profile)) {
    const lastUpdateTime = ownership.documentUpdateTimes[documentPath];
    if (lastUpdateTime) {
      batch.delete(firestore.doc(documentPath), {lastUpdateTime});
    }
  }
  await batch.commit();

  const after = await inspectFixture({firestore, bucket, now, profile});
  assertFixturePhase(after, "ABSENT");
  return {before, after};
}

async function inspectFixture({
  firestore,
  bucket,
  now = new Date(),
  profile = DEVELOPMENT_PROFILE,
}) {
  assertValidDate(now);
  assertFixtureProfile(profile);
  const documentEntries = Object.entries(
      buildFixtureDefinition(now, profile).documents,
  );
  const documentSnapshots = await Promise.all(
      documentEntries.map(([, document]) => firestore.doc(document.path).get()),
  );
  const objectEntries = Object.entries(profile.objectPaths);
  const objectStates = await Promise.all(objectEntries.map(async ([key, objectPath]) => {
    const file = bucket.file(objectPath);
    const [exists] = await file.exists();
    if (!exists) {
      return [key, {exists: false, owned: false}];
    }
    const [metadata] = await file.getMetadata();
    return [key, {
      exists: true,
      owned: isFixtureObjectMetadata(metadata?.metadata, profile),
    }];
  }));

  const documents = Object.fromEntries(documentEntries.map(([key], index) => {
    const snapshot = documentSnapshots[index];
    return [key, {
      exists: snapshot.exists,
      owned: snapshot.exists &&
        isFixtureMarker(snapshot.data()?.bodeulFixture, profile),
      data: snapshot.exists ? snapshot.data() : null,
    }];
  }));
  const objects = Object.fromEntries(objectStates);
  const evaluations = evaluateFixtureDocuments(documents, now, profile);

  return {
    projectId: profile.projectId,
    marker: profile.marker,
    phase: resolveFixturePhase(documents, objects, evaluations),
    documents: summarizeArtifacts(documents),
    objects: summarizeArtifacts(objects),
    evaluations,
  };
}

function evaluateFixtureDocuments(documents, now, profile = DEVELOPMENT_PROFILE) {
  const {documentIds} = profile;
  return {
    sessionExpired: documents.sessionExpired.exists &&
      documents.sessionExpired.owned
      ? evaluateLegacyCompanionSession(
          documentIds.sessionExpired,
          documents.sessionExpired.data,
          now,
      )
      : null,
    sessionHeld: documents.sessionHeld.exists &&
      documents.sessionHeld.owned
      ? evaluateLegacyCompanionSession(
          documentIds.sessionHeld,
          documents.sessionHeld.data,
          now,
      )
      : null,
    managerExpired: documents.managerExpired.exists &&
      documents.managerExpired.owned
      ? evaluateManagerDocument(
          documentIds.managerExpired,
          documents.managerExpired.data,
          now,
      )
      : null,
    managerHeld: documents.managerHeld.exists &&
      documents.managerHeld.owned
      ? evaluateManagerDocument(
          documentIds.managerHeld,
          documents.managerHeld.data,
          now,
      )
      : null,
  };
}

function resolveFixturePhase(documents, objects, evaluations) {
  const documentValues = Object.values(documents);
  const objectValues = Object.values(objects);
  if (documentValues.every((item) => !item.exists) &&
      objectValues.every((item) => !item.exists)) {
    return "ABSENT";
  }
  if (!documentValues.every((item) => item.exists && item.owned)) {
    return "PARTIAL";
  }
  if (!objectValues.every((item) => !item.exists || item.owned)) {
    return "PARTIAL";
  }

  const ready = objectValues.every((item) => item.exists && item.owned) &&
    evaluations.sessionExpired?.messageCandidates === 1 &&
    evaluations.sessionExpired?.attachments.length === 1 &&
    evaluations.sessionExpired?.locationEligible === true &&
    evaluations.sessionHeld?.legalHoldSkips === 3 &&
    evaluations.managerExpired?.candidates.length === 1 &&
    evaluations.managerHeld?.legalHoldSkips === 1;
  if (ready) {
    return "READY";
  }

  const applied = !objects.sessionExpired.exists &&
    objects.sessionHeld.exists &&
    !objects.managerExpired.exists &&
    objects.managerHeld.exists &&
    evaluations.sessionExpired?.hasWork === false &&
    evaluations.sessionHeld?.legalHoldSkips === 3 &&
    evaluations.managerExpired?.candidates.length === 0 &&
    evaluations.managerHeld?.legalHoldSkips === 1;
  return applied ? "APPLIED" : "PARTIAL";
}

async function assertOwnedArtifacts({
  firestore,
  bucket,
  profile = DEVELOPMENT_PROFILE,
}) {
  const documentUpdateTimes = {};
  const objectGenerations = {};
  for (const documentPath of fixtureDocumentPaths(profile)) {
    const snapshot = await firestore.doc(documentPath).get();
    if (snapshot.exists &&
        !isFixtureMarker(snapshot.data()?.bodeulFixture, profile)) {
      throw new Error(`픽스처 표식이 다른 문서는 삭제하지 않습니다: ${documentPath}`);
    }
    if (snapshot.exists) {
      documentUpdateTimes[documentPath] = snapshot.updateTime;
    }
  }
  for (const objectPath of Object.values(profile.objectPaths)) {
    const file = bucket.file(objectPath);
    const [exists] = await file.exists();
    if (!exists) {
      continue;
    }
    const [metadata] = await file.getMetadata();
    if (!isFixtureObjectMetadata(metadata?.metadata, profile)) {
      throw new Error(`픽스처 표식이 다른 객체는 삭제하지 않습니다: ${objectPath}`);
    }
    objectGenerations[objectPath] = metadata.generation;
  }
  return {documentUpdateTimes, objectGenerations};
}

function createFixtureDatabase(profile = DEVELOPMENT_PROFILE) {
  return {
    async beginJob() {
      return `${profile.marker}-local-job`;
    },
    async preview() {
      return {
        messageCandidates: 0,
        attachmentCandidates: 0,
        locationCandidates: 0,
        legalHoldSkips: 0,
        adminAuditCandidates: 0,
      };
    },
    async claimAttachments() {
      return [];
    },
    async purgeCompanionRecords() {
      return {messagesRedacted: 0, locationsDeleted: 0};
    },
    async purgeAdminAudits() {
      return 0;
    },
    async finishJob() {
      return true;
    },
  };
}

function assertExpectedSummary(summary, apply) {
  const expected = {
    postgresMessageCandidates: 0,
    postgresAttachmentCandidates: 0,
    postgresLocationCandidates: 0,
    postgresLegalHoldSkips: 0,
    firestoreMessageCandidates: 1,
    firestoreAttachmentCandidates: 1,
    firestoreLocationCandidates: 1,
    firestoreLegalHoldSkips: 3,
    managerDocumentCandidates: 1,
    managerDocumentLegalHoldSkips: 1,
    adminAuditCandidates: 0,
    messagesRedacted: 0,
    attachmentsDeleted: 0,
    attachmentDeleteFailures: 0,
    locationsDeleted: 0,
    firestoreMessagesRedacted: apply ? 1 : 0,
    firestoreAttachmentsDeleted: apply ? 1 : 0,
    firestoreAttachmentDeleteFailures: 0,
    firestoreLocationsCleared: apply ? 1 : 0,
    managerDocumentsDeleted: apply ? 1 : 0,
    managerDocumentDeleteFailures: 0,
    adminAuditsDeleted: 0,
  };
  for (const [key, value] of Object.entries(expected)) {
    if (summary[key] !== value) {
      throw new Error(`픽스처 파기 집계가 예상과 다릅니다: ${key}`);
    }
  }
}

function assertFixturePhase(status, expectedPhase) {
  if (status.phase !== expectedPhase) {
    throw new Error(
        `픽스처 상태가 예상과 다릅니다: ${status.phase} (expected ${expectedPhase})`,
    );
  }
}

function fixtureDocumentPaths(profile = DEVELOPMENT_PROFILE) {
  const {documentIds} = profile;
  return [
    `companionSessions/${documentIds.sessionExpired}`,
    `companionSessions/${documentIds.sessionHeld}`,
    `users/${documentIds.managerExpired}`,
    `users/${documentIds.managerHeld}`,
  ];
}

function summarizeArtifacts(artifacts) {
  return Object.fromEntries(Object.entries(artifacts).map(([key, value]) => [
    key,
    {exists: value.exists, owned: value.owned},
  ]));
}

function isFixtureMarker(value, profile = DEVELOPMENT_PROFILE) {
  return value?.name === profile.marker &&
    value?.owner === profile.owner &&
    String(value?.issue || "") === profile.issue;
}

function isFixtureObjectMetadata(value, profile = DEVELOPMENT_PROFILE) {
  return value?.bodeulFixture === profile.marker &&
    value?.bodeulFixtureOwner === profile.owner &&
    String(value?.bodeulFixtureIssue || "") === profile.issue;
}

function assertFixtureProfile(profile) {
  if (!profile || !profile.projectId || !profile.marker ||
      !profile.documentIds || !profile.objectPaths) {
    throw new Error("Firebase 파기 픽스처 프로필이 올바르지 않습니다.");
  }
}

function assertValidDate(value) {
  if (!(value instanceof Date) || !Number.isFinite(value.getTime())) {
    throw new Error("픽스처 기준 시각이 올바르지 않습니다.");
  }
}

module.exports = {
  DEVELOPMENT_PROFILE,
  DEVELOPMENT_PROJECT_ID,
  FIXTURE_MARKER,
  MANAGER_IDS,
  OBJECT_PATHS,
  PRODUCTION_PROFILE,
  SESSION_IDS,
  assertExpectedSummary,
  buildFixtureDefinition,
  cleanupFixture,
  createScopedLegacyStore,
  createScopedManagerStore,
  createScopedStorageGateway,
  inspectFixture,
  resolveFixturePhase,
  runFixtureRetention,
  setupFixture,
};
