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

const DEVELOPMENT_PROJECT_ID = "bodeul-dev";
const FIXTURE_MARKER = "bodeul-retention-firebase-v1";
const FIXTURE_OWNER = "bodeul110/Bodeul";
const FIXTURE_ISSUE = "222";
const DAY_IN_MILLIS = 24 * 60 * 60 * 1000;
const SESSION_EXPIRED_ID = "retention-fixture-firestore-expired-v1";
const SESSION_HELD_ID = "retention-fixture-firestore-held-v1";
const MANAGER_EXPIRED_ID = "retention-fixture-manager-expired-v1";
const MANAGER_HELD_ID = "retention-fixture-manager-held-v1";
const SESSION_IDS = [SESSION_EXPIRED_ID, SESSION_HELD_ID];
const MANAGER_IDS = [MANAGER_EXPIRED_ID, MANAGER_HELD_ID];
const OBJECT_PATHS = {
  sessionExpired: `companion-chat-attachments/${SESSION_EXPIRED_ID}/fixture.pdf`,
  sessionHeld: `companion-chat-attachments/${SESSION_HELD_ID}/fixture.pdf`,
  managerExpired: `manager-documents/${MANAGER_EXPIRED_ID}/idCard/fixture.pdf`,
  managerHeld: `manager-documents/${MANAGER_HELD_ID}/license/fixture.pdf`,
};

function buildFixtureDefinition(now = new Date()) {
  assertValidDate(now);
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
    name: FIXTURE_MARKER,
    owner: FIXTURE_OWNER,
    issue: FIXTURE_ISSUE,
    createdAt,
  };

  return {
    documents: {
      sessionExpired: {
        path: `companionSessions/${SESSION_EXPIRED_ID}`,
        data: {
          bodeulFixture: marker,
          currentStatus: "COMPLETED",
          completedAt: sessionExpiredAt,
          updatedAt: sessionExpiredAt,
          chatMessages: [{
            body: "개발 파기 픽스처 메시지",
            attachments: [{fullPath: OBJECT_PATHS.sessionExpired}],
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
        path: `companionSessions/${SESSION_HELD_ID}`,
        data: {
          bodeulFixture: marker,
          currentStatus: "COMPLETED",
          completedAt: sessionExpiredAt,
          updatedAt: sessionExpiredAt,
          legalHoldUntil: heldUntil,
          chatMessages: [{
            body: "개발 legal hold 픽스처 메시지",
            attachments: [{fullPath: OBJECT_PATHS.sessionHeld}],
          }],
          sharedLatitude: 37.5665,
          sharedLongitude: 126.978,
          sharedLocationHistory: [{latitude: 37.5665, longitude: 126.978}],
          liveLocationSharingActive: true,
        },
      },
      managerExpired: {
        path: `users/${MANAGER_EXPIRED_ID}`,
        data: {
          bodeulFixture: marker,
          role: "MANAGER",
          email: "retention-fixture-expired@bodeul.invalid",
          managerDocumentStatus: "APPROVED",
          managerDocumentReviewedAt: managerExpiredAt,
          managerDocumentUpdatedAt: managerExpiredAt,
          managerDocumentFiles: {
            idCard: {
              fullPath: OBJECT_PATHS.managerExpired,
              uploadedAt: managerExpiredAt,
            },
          },
          managerDocumentFilePaths: {idCard: OBJECT_PATHS.managerExpired},
          managerIdCardStoragePath: OBJECT_PATHS.managerExpired,
        },
      },
      managerHeld: {
        path: `users/${MANAGER_HELD_ID}`,
        data: {
          bodeulFixture: marker,
          role: "MANAGER",
          email: "retention-fixture-held@bodeul.invalid",
          managerDocumentStatus: "APPROVED",
          managerDocumentReviewedAt: managerExpiredAt,
          managerDocumentUpdatedAt: managerExpiredAt,
          managerDocumentLegalHoldUntil: heldUntil,
          managerDocumentFiles: {
            license: {
              fullPath: OBJECT_PATHS.managerHeld,
              uploadedAt: managerExpiredAt,
            },
          },
          managerDocumentFilePaths: {license: OBJECT_PATHS.managerHeld},
          managerLicenseStoragePath: OBJECT_PATHS.managerHeld,
        },
      },
    },
    objects: Object.values(OBJECT_PATHS),
  };
}

async function setupFixture({firestore, bucket, now = new Date()}) {
  const before = await inspectFixture({firestore, bucket, now});
  if (before.phase !== "ABSENT") {
    throw new Error(
        `픽스처가 비어 있지 않습니다(${before.phase}). cleanup 후 다시 실행해 주세요.`,
    );
  }

  const fixture = buildFixtureDefinition(now);
  for (const objectPath of fixture.objects) {
    await bucket.file(objectPath).save(
        Buffer.from(`fixture:${FIXTURE_MARKER}:${objectPath}`),
        {
          resumable: false,
          metadata: {
            contentType: "application/pdf",
            metadata: {
              bodeulFixture: FIXTURE_MARKER,
              bodeulFixtureOwner: FIXTURE_OWNER,
              bodeulFixtureIssue: FIXTURE_ISSUE,
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

  const after = await inspectFixture({firestore, bucket, now});
  assertFixturePhase(after, "READY");
  return after;
}

async function runFixtureRetention({firestore, bucket, apply, now = new Date()}) {
  const before = await inspectFixture({firestore, bucket, now});
  assertFixturePhase(before, "READY");

  const summary = await runRetentionJob({
    database: createFixtureDatabase(),
    legacyStore: createScopedLegacyStore(firestore),
    managerStore: createScopedManagerStore(firestore),
    storage: createScopedStorageGateway(bucket),
    apply,
    now,
  });
  assertExpectedSummary(summary, apply);

  const after = await inspectFixture({firestore, bucket, now});
  assertFixturePhase(after, apply ? "APPLIED" : "READY");
  return {summary, status: after};
}

function createScopedLegacyStore(firestore) {
  const delegate = new FirebaseLegacyCompanionStore(firestore, {
    documentGuard: (documentId, data) =>
      SESSION_IDS.includes(documentId) && isFixtureMarker(data?.bodeulFixture),
  });
  return {
    async preview(asOf) {
      const documents = await getDocumentsById(
          firestore.collection("companionSessions"),
          SESSION_IDS,
      );
      const summary = {
        sessions: [],
        messageCandidates: 0,
        attachmentCandidates: 0,
        locationCandidates: 0,
        legalHoldSkips: 0,
      };
      for (const document of documents) {
        assertFixtureDocument(document, SESSION_IDS, "동행 세션");
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
      assertAllowedId(candidate?.sessionId, SESSION_IDS, "동행 세션");
      return delegate.applySession(candidate, asOf, storage);
    },
  };
}

function createScopedManagerStore(firestore) {
  const delegate = new FirebaseManagerDocumentStore(firestore, {
    documentGuard: (documentId, data) =>
      MANAGER_IDS.includes(documentId) && isFixtureMarker(data?.bodeulFixture),
  });
  return {
    async preview(asOf) {
      const documents = await getDocumentsById(
          firestore.collection("users"),
          MANAGER_IDS,
      );
      const result = {candidates: [], legalHoldSkips: 0};
      for (const document of documents) {
        assertFixtureDocument(document, MANAGER_IDS, "매니저");
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
      assertAllowedId(candidate?.managerId, MANAGER_IDS, "매니저");
      return delegate.isStillEligible(candidate, asOf);
    },
    async clearReference(candidate, deletedAt) {
      assertAllowedId(candidate?.managerId, MANAGER_IDS, "매니저");
      return delegate.clearReference(candidate, deletedAt);
    },
  };
}

function createScopedStorageGateway(bucket) {
  const allowedPaths = new Set(Object.values(OBJECT_PATHS));
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
          if (!isFixtureObjectMetadata(metadata?.metadata)) {
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

function assertFixtureDocument(document, allowedIds, label) {
  assertAllowedId(document?.id, allowedIds, label);
  if (!isFixtureMarker(document?.data()?.bodeulFixture)) {
    throw new Error(`${label} 문서의 픽스처 표식이 일치하지 않습니다.`);
  }
}

function assertAllowedStoragePath(storagePath, allowedPaths) {
  if (!allowedPaths.has(String(storagePath || ""))) {
    throw new Error("픽스처 범위를 벗어난 Storage 객체는 처리하지 않습니다.");
  }
}

async function cleanupFixture({firestore, bucket, now = new Date()}) {
  const before = await inspectFixture({firestore, bucket, now});
  const ownership = await assertOwnedArtifacts({firestore, bucket});

  for (const objectPath of Object.values(OBJECT_PATHS)) {
    const generation = ownership.objectGenerations[objectPath];
    if (generation) {
      await bucket.file(objectPath, {generation}).delete({ignoreNotFound: true});
    }
  }
  const batch = firestore.batch();
  for (const documentPath of fixtureDocumentPaths()) {
    const lastUpdateTime = ownership.documentUpdateTimes[documentPath];
    if (lastUpdateTime) {
      batch.delete(firestore.doc(documentPath), {lastUpdateTime});
    }
  }
  await batch.commit();

  const after = await inspectFixture({firestore, bucket, now});
  assertFixturePhase(after, "ABSENT");
  return {before, after};
}

async function inspectFixture({firestore, bucket, now = new Date()}) {
  assertValidDate(now);
  const documentEntries = Object.entries(buildFixtureDefinition(now).documents);
  const documentSnapshots = await Promise.all(
      documentEntries.map(([, document]) => firestore.doc(document.path).get()),
  );
  const objectEntries = Object.entries(OBJECT_PATHS);
  const objectStates = await Promise.all(objectEntries.map(async ([key, objectPath]) => {
    const file = bucket.file(objectPath);
    const [exists] = await file.exists();
    if (!exists) {
      return [key, {exists: false, owned: false}];
    }
    const [metadata] = await file.getMetadata();
    return [key, {
      exists: true,
      owned: isFixtureObjectMetadata(metadata?.metadata),
    }];
  }));

  const documents = Object.fromEntries(documentEntries.map(([key], index) => {
    const snapshot = documentSnapshots[index];
    return [key, {
      exists: snapshot.exists,
      owned: snapshot.exists && isFixtureMarker(snapshot.data()?.bodeulFixture),
      data: snapshot.exists ? snapshot.data() : null,
    }];
  }));
  const objects = Object.fromEntries(objectStates);
  const evaluations = evaluateFixtureDocuments(documents, now);

  return {
    projectId: DEVELOPMENT_PROJECT_ID,
    marker: FIXTURE_MARKER,
    phase: resolveFixturePhase(documents, objects, evaluations),
    documents: summarizeArtifacts(documents),
    objects: summarizeArtifacts(objects),
    evaluations,
  };
}

function evaluateFixtureDocuments(documents, now) {
  return {
    sessionExpired: documents.sessionExpired.exists
      ? evaluateLegacyCompanionSession(
          SESSION_EXPIRED_ID,
          documents.sessionExpired.data,
          now,
      )
      : null,
    sessionHeld: documents.sessionHeld.exists
      ? evaluateLegacyCompanionSession(
          SESSION_HELD_ID,
          documents.sessionHeld.data,
          now,
      )
      : null,
    managerExpired: documents.managerExpired.exists
      ? evaluateManagerDocument(
          MANAGER_EXPIRED_ID,
          documents.managerExpired.data,
          now,
      )
      : null,
    managerHeld: documents.managerHeld.exists
      ? evaluateManagerDocument(
          MANAGER_HELD_ID,
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

async function assertOwnedArtifacts({firestore, bucket}) {
  const documentUpdateTimes = {};
  const objectGenerations = {};
  for (const documentPath of fixtureDocumentPaths()) {
    const snapshot = await firestore.doc(documentPath).get();
    if (snapshot.exists && !isFixtureMarker(snapshot.data()?.bodeulFixture)) {
      throw new Error(`픽스처 표식이 다른 문서는 삭제하지 않습니다: ${documentPath}`);
    }
    if (snapshot.exists) {
      documentUpdateTimes[documentPath] = snapshot.updateTime;
    }
  }
  for (const objectPath of Object.values(OBJECT_PATHS)) {
    const file = bucket.file(objectPath);
    const [exists] = await file.exists();
    if (!exists) {
      continue;
    }
    const [metadata] = await file.getMetadata();
    if (!isFixtureObjectMetadata(metadata?.metadata)) {
      throw new Error(`픽스처 표식이 다른 객체는 삭제하지 않습니다: ${objectPath}`);
    }
    objectGenerations[objectPath] = metadata.generation;
  }
  return {documentUpdateTimes, objectGenerations};
}

function createFixtureDatabase() {
  return {
    async beginJob() {
      return `${FIXTURE_MARKER}-local-job`;
    },
    async preview() {
      return {
        messageCandidates: 0,
        attachmentCandidates: 0,
        locationCandidates: 0,
        legalHoldSkips: 0,
      };
    },
    async claimAttachments() {
      return [];
    },
    async purgeCompanionRecords() {
      return {messagesRedacted: 0, locationsDeleted: 0};
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

function fixtureDocumentPaths() {
  return [
    `companionSessions/${SESSION_EXPIRED_ID}`,
    `companionSessions/${SESSION_HELD_ID}`,
    `users/${MANAGER_EXPIRED_ID}`,
    `users/${MANAGER_HELD_ID}`,
  ];
}

function summarizeArtifacts(artifacts) {
  return Object.fromEntries(Object.entries(artifacts).map(([key, value]) => [
    key,
    {exists: value.exists, owned: value.owned},
  ]));
}

function isFixtureMarker(value) {
  return value?.name === FIXTURE_MARKER &&
    value?.owner === FIXTURE_OWNER &&
    String(value?.issue || "") === FIXTURE_ISSUE;
}

function isFixtureObjectMetadata(value) {
  return value?.bodeulFixture === FIXTURE_MARKER &&
    value?.bodeulFixtureOwner === FIXTURE_OWNER &&
    String(value?.bodeulFixtureIssue || "") === FIXTURE_ISSUE;
}

function assertValidDate(value) {
  if (!(value instanceof Date) || !Number.isFinite(value.getTime())) {
    throw new Error("픽스처 기준 시각이 올바르지 않습니다.");
  }
}

module.exports = {
  DEVELOPMENT_PROJECT_ID,
  FIXTURE_MARKER,
  MANAGER_IDS,
  OBJECT_PATHS,
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
