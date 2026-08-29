const assert = require("node:assert/strict");
const test = require("node:test");

const {deleteApp, initializeApp} = require("firebase-admin/app");
const {getFirestore, Timestamp} = require("firebase-admin/firestore");
const {getStorage} = require("firebase-admin/storage");

const {
  FirebaseLegacyCompanionStore,
  FirebaseManagerDocumentStore,
  FirebaseStorageGateway,
  runRetentionJob,
} = require("../src/retention");
const {
  PRODUCTION_PROFILE,
  cleanupFixture,
  inspectFixture,
  runFixtureRetention,
  setupFixture,
} = require("../scripts/lib/retention-firebase-fixture");

const projectId = "bodeul-retention-emulator";
const emulatorRequired = process.env.RETENTION_EMULATOR_TEST_REQUIRED === "true";
const emulatorConfigured = Boolean(
    process.env.FIRESTORE_EMULATOR_HOST ||
    process.env.FIREBASE_STORAGE_EMULATOR_HOST,
);

test("Firestore와 Storage 파기 실패를 다음 실행에서 복구한다", {
  skip: !emulatorRequired && !emulatorConfigured,
}, async () => {
  assert.equal(
      isLoopbackEmulatorHost(process.env.FIRESTORE_EMULATOR_HOST),
      true,
      "Firestore Emulator는 로컬 루프백 주소에서만 실행해야 합니다.",
  );
  assert.equal(
      isLoopbackEmulatorHost(process.env.FIREBASE_STORAGE_EMULATOR_HOST),
      true,
      "Storage Emulator는 로컬 루프백 주소에서만 실행해야 합니다.",
  );

  const app = initializeApp({
    projectId,
    storageBucket: `${projectId}.firebasestorage.app`,
  }, `retention-emulator-${process.pid}`);
  const firestore = getFirestore(app);
  const bucket = getStorage(app).bucket();
  const legacyStore = new FirebaseLegacyCompanionStore(firestore);
  const managerStore = new FirebaseManagerDocumentStore(firestore);
  const storageGateway = new FirebaseStorageGateway(bucket);
  const asOf = new Date("2026-07-18T00:00:00.000Z");
  const expiredAt = Timestamp.fromDate(new Date("2025-12-01T00:00:00.000Z"));
  const heldUntil = Timestamp.fromDate(new Date("2026-08-01T00:00:00.000Z"));

  const paths = {
    chatSuccess: "companion-chat-attachments/session-expired/success.pdf",
    chatRetry: "companion-chat-attachments/session-expired/retry.pdf",
    chatHeld: "companion-chat-attachments/session-held/held.pdf",
    managerSuccess: "manager-documents/manager-expired/idCard/success.pdf",
    managerRetry: "manager-documents/manager-expired/license/retry.pdf",
    managerHeld: "manager-documents/manager-held/license/held.pdf",
  };

  try {
    await clearFirestore();
    await seedFirestore(firestore, {paths, expiredAt, heldUntil});
    await Promise.all(Object.values(paths).map((storagePath) =>
      bucket.file(storagePath).save(Buffer.from(`fixture:${storagePath}`)),
    ));

    const finishes = [];
    const adminAuditPurges = [];
    const database = createDatabase(finishes, adminAuditPurges);
    const failedOnce = new Set();
    const storage = {
      async deleteChatAttachment(storagePath) {
        if (storagePath === paths.chatRetry && !failedOnce.has(storagePath)) {
          failedOnce.add(storagePath);
          throw new Error("chat storage unavailable");
        }
        await storageGateway.deleteChatAttachment(storagePath);
      },
      async deleteManagerDocument(storagePath) {
        if (storagePath === paths.managerRetry && !failedOnce.has(storagePath)) {
          failedOnce.add(storagePath);
          throw new Error("manager storage unavailable");
        }
        await storageGateway.deleteManagerDocument(storagePath);
      },
    };

    const firstSummary = await runRetentionJob({
      database,
      legacyStore,
      managerStore,
      storage,
      apply: true,
      now: asOf,
    });

    assert.equal(firstSummary.firestoreMessagesRedacted, 1);
    assert.equal(firstSummary.firestoreAttachmentsDeleted, 1);
    assert.equal(firstSummary.firestoreAttachmentDeleteFailures, 1);
    assert.equal(firstSummary.firestoreLocationsCleared, 1);
    assert.equal(firstSummary.managerDocumentsDeleted, 1);
    assert.equal(firstSummary.managerDocumentDeleteFailures, 1);
    assert.equal(firstSummary.firestoreLegalHoldSkips, 3);
    assert.equal(firstSummary.managerDocumentLegalHoldSkips, 1);
    assert.equal(firstSummary.adminAuditCandidates, 1);
    assert.equal(firstSummary.adminAuditsDeleted, 1);

    const expiredSessionAfterFirst = await documentData(
        firestore,
        "companionSessions/session-expired",
    );
    assert.equal(expiredSessionAfterFirst.chatMessages[0].body, "");
    assert.deepEqual(expiredSessionAfterFirst.chatMessages[0].attachments, [
      {fullPath: paths.chatRetry},
    ]);
    assert.equal(Object.hasOwn(expiredSessionAfterFirst, "sharedLatitude"), false);
    assert.equal(Object.hasOwn(expiredSessionAfterFirst, "sharedLongitude"), false);
    assert.equal(
        Object.hasOwn(expiredSessionAfterFirst, "sharedLocationHistory"),
        false,
    );
    assert.equal(expiredSessionAfterFirst.liveLocationSharingActive, false);

    const expiredManagerAfterFirst = await documentData(
        firestore,
        "users/manager-expired",
    );
    assert.equal(expiredManagerAfterFirst.managerDocumentFiles.idCard, undefined);
    assert.equal(expiredManagerAfterFirst.managerDocumentFilePaths.idCard, undefined);
    assert.equal(expiredManagerAfterFirst.managerIdCardStoragePath, undefined);
    assert.equal(
        expiredManagerAfterFirst.managerDocumentFiles.license.fullPath,
        paths.managerRetry,
    );
    assert.equal(
        expiredManagerAfterFirst.managerDocumentFilePaths.license,
        paths.managerRetry,
    );
    assert.equal(
        expiredManagerAfterFirst.managerLicenseStoragePath,
        paths.managerRetry,
    );
    assert.equal(await fileExists(bucket, paths.chatSuccess), false);
    assert.equal(await fileExists(bucket, paths.chatRetry), true);
    assert.equal(await fileExists(bucket, paths.managerSuccess), false);
    assert.equal(await fileExists(bucket, paths.managerRetry), true);

    await assertHeldData(firestore, bucket, paths);

    const secondSummary = await runRetentionJob({
      database,
      legacyStore,
      managerStore,
      storage,
      apply: true,
      now: new Date("2026-07-19T00:00:00.000Z"),
    });

    assert.equal(secondSummary.firestoreMessagesRedacted, 0);
    assert.equal(secondSummary.firestoreAttachmentsDeleted, 1);
    assert.equal(secondSummary.firestoreAttachmentDeleteFailures, 0);
    assert.equal(secondSummary.firestoreLocationsCleared, 0);
    assert.equal(secondSummary.managerDocumentsDeleted, 1);
    assert.equal(secondSummary.managerDocumentDeleteFailures, 0);
    assert.equal(secondSummary.firestoreLegalHoldSkips, 3);
    assert.equal(secondSummary.managerDocumentLegalHoldSkips, 1);
    assert.equal(secondSummary.adminAuditCandidates, 1);
    assert.equal(secondSummary.adminAuditsDeleted, 1);

    const expiredSessionAfterSecond = await documentData(
        firestore,
        "companionSessions/session-expired",
    );
    const expiredManagerAfterSecond = await documentData(
        firestore,
        "users/manager-expired",
    );
    assert.deepEqual(expiredSessionAfterSecond.chatMessages[0].attachments, []);
    assert.equal(Object.hasOwn(expiredSessionAfterSecond, "sharedLatitude"), false);
    assert.equal(Object.hasOwn(expiredSessionAfterSecond, "sharedLongitude"), false);
    assert.equal(
        Object.hasOwn(expiredSessionAfterSecond, "sharedLocationHistory"),
        false,
    );
    assert.equal(expiredSessionAfterSecond.liveLocationSharingActive, false);
    assert.equal(expiredManagerAfterSecond.managerDocumentFiles.idCard, undefined);
    assert.equal(expiredManagerAfterSecond.managerDocumentFilePaths.idCard, undefined);
    assert.equal(expiredManagerAfterSecond.managerIdCardStoragePath, undefined);
    assert.equal(expiredManagerAfterSecond.managerDocumentFiles.license, undefined);
    assert.equal(expiredManagerAfterSecond.managerDocumentFilePaths.license, undefined);
    assert.equal(expiredManagerAfterSecond.managerLicenseStoragePath, undefined);
    assert.equal(await fileExists(bucket, paths.chatRetry), false);
    assert.equal(await fileExists(bucket, paths.managerRetry), false);
    await assertHeldData(firestore, bucket, paths);
    assert.deepEqual(finishes.map((finish) => finish.status), ["COMPLETED", "COMPLETED"]);
    assert.deepEqual(adminAuditPurges, [
      {asOf: "2026-07-18T00:00:00.000Z", limit: 500},
      {asOf: "2026-07-19T00:00:00.000Z", limit: 500},
    ]);
  } finally {
    await clearFirestore();
    await deleteApp(app);
  }
});

test("개발 Firebase 픽스처 생명주기를 Emulator에서 격리 검증한다", {
  skip: !emulatorRequired && !emulatorConfigured,
}, async () => {
  assert.equal(isLoopbackEmulatorHost(process.env.FIRESTORE_EMULATOR_HOST), true);
  assert.equal(isLoopbackEmulatorHost(process.env.FIREBASE_STORAGE_EMULATOR_HOST), true);

  const app = initializeApp({
    projectId,
    storageBucket: `${projectId}.firebasestorage.app`,
  }, `retention-fixture-emulator-${process.pid}`);
  const dependencies = {
    firestore: getFirestore(app),
    bucket: getStorage(app).bucket(),
  };
  const now = new Date("2026-08-23T00:00:00.000Z");

  try {
    await clearFirestore();
    assert.equal((await inspectFixture({...dependencies, now})).phase, "ABSENT");
    assert.equal((await setupFixture({...dependencies, now})).phase, "READY");

    const dryRun = await runFixtureRetention({...dependencies, apply: false, now});
    assert.equal(dryRun.summary.mode, "DRY_RUN");
    assert.equal(dryRun.status.phase, "READY");

    const apply = await runFixtureRetention({...dependencies, apply: true, now});
    assert.equal(apply.summary.mode, "APPLY");
    assert.equal(apply.status.phase, "APPLIED");

    const cleanup = await cleanupFixture({...dependencies, now});
    assert.equal(cleanup.before.phase, "APPLIED");
    assert.equal(cleanup.after.phase, "ABSENT");
  } finally {
    try {
      await cleanupFixture({...dependencies, now});
    } catch (_error) {
      // 본문 assertion을 보존하고 Emulator 문서는 아래에서 일괄 정리한다.
    }
    await clearFirestore();
    await deleteApp(app);
  }
});

test("production Firebase 픽스처 프로필도 고정 범위에서만 동작한다", {
  skip: !emulatorRequired && !emulatorConfigured,
}, async () => {
  assert.equal(isLoopbackEmulatorHost(process.env.FIRESTORE_EMULATOR_HOST), true);
  assert.equal(isLoopbackEmulatorHost(process.env.FIREBASE_STORAGE_EMULATOR_HOST), true);

  const app = initializeApp({
    projectId,
    storageBucket: `${projectId}.firebasestorage.app`,
  }, `retention-production-profile-emulator-${process.pid}`);
  const dependencies = {
    firestore: getFirestore(app),
    bucket: getStorage(app).bucket(),
    profile: PRODUCTION_PROFILE,
  };
  const now = new Date("2026-08-23T00:00:00.000Z");

  try {
    await clearFirestore();
    const absent = await inspectFixture({...dependencies, now});
    assert.equal(absent.projectId, "bodeul-prod-110");
    assert.equal(absent.phase, "ABSENT");
    assert.equal((await setupFixture({...dependencies, now})).phase, "READY");

    const dryRun = await runFixtureRetention({...dependencies, apply: false, now});
    assert.equal(dryRun.summary.mode, "DRY_RUN");
    assert.equal(dryRun.status.phase, "READY");

    const apply = await runFixtureRetention({...dependencies, apply: true, now});
    assert.equal(apply.summary.mode, "APPLY");
    assert.equal(apply.status.phase, "APPLIED");

    const cleanup = await cleanupFixture({...dependencies, now});
    assert.equal(cleanup.before.phase, "APPLIED");
    assert.equal(cleanup.after.phase, "ABSENT");
  } finally {
    try {
      await cleanupFixture({...dependencies, now});
    } catch (_error) {
      // 본문 assertion을 보존하고 Emulator 문서는 아래에서 일괄 정리한다.
    }
    await clearFirestore();
    await deleteApp(app);
  }
});

async function seedFirestore(firestore, {paths, expiredAt, heldUntil}) {
  const batch = firestore.batch();
  batch.set(firestore.doc("companionSessions/session-expired"), {
    currentStatus: "COMPLETED",
    completedAt: expiredAt,
    updatedAt: expiredAt,
    sharedLatitude: 37.5,
    sharedLongitude: 127.0,
    sharedLocationHistory: [{latitude: 37.5, longitude: 127.0}],
    liveLocationSharingActive: true,
    chatMessages: [{
      body: "민감한 대화",
      attachments: [
        {fullPath: paths.chatSuccess},
        {fullPath: paths.chatRetry},
      ],
    }],
  });
  batch.set(firestore.doc("companionSessions/session-held"), {
    currentStatus: "COMPLETED",
    completedAt: expiredAt,
    updatedAt: expiredAt,
    legalHoldUntil: heldUntil,
    sharedLatitude: 37.5,
    sharedLongitude: 127.0,
    sharedLocationHistory: [{latitude: 37.5, longitude: 127.0}],
    liveLocationSharingActive: true,
    chatMessages: [{
      body: "법적 보존 대화",
      attachments: [{fullPath: paths.chatHeld}],
    }],
  });
  batch.set(firestore.doc("users/manager-expired"), {
    role: "MANAGER",
    managerDocumentStatus: "APPROVED",
    managerDocumentReviewedAt: expiredAt,
    managerDocumentUpdatedAt: expiredAt,
    managerDocumentFiles: {
      idCard: {fullPath: paths.managerSuccess, uploadedAt: expiredAt},
      license: {fullPath: paths.managerRetry, uploadedAt: expiredAt},
    },
    managerDocumentFilePaths: {
      idCard: paths.managerSuccess,
      license: paths.managerRetry,
    },
    managerIdCardStoragePath: paths.managerSuccess,
    managerLicenseStoragePath: paths.managerRetry,
  });
  batch.set(firestore.doc("users/manager-held"), {
    role: "MANAGER",
    managerDocumentStatus: "APPROVED",
    managerDocumentReviewedAt: expiredAt,
    managerDocumentUpdatedAt: expiredAt,
    managerDocumentLegalHoldUntil: heldUntil,
    managerDocumentFiles: {
      license: {fullPath: paths.managerHeld, uploadedAt: expiredAt},
    },
    managerDocumentFilePaths: {license: paths.managerHeld},
    managerLicenseStoragePath: paths.managerHeld,
  });
  await batch.commit();
}

function createDatabase(finishes, adminAuditPurges) {
  let jobCount = 0;
  return {
    async beginJob() {
      jobCount += 1;
      return `retention-emulator-job-${jobCount}`;
    },
    async preview() {
      return {
        messageCandidates: 0,
        attachmentCandidates: 0,
        locationCandidates: 0,
        legalHoldSkips: 0,
        adminAuditCandidates: 1,
      };
    },
    async claimAttachments() {
      return [];
    },
    async purgeCompanionRecords() {
      return {messagesRedacted: 0, locationsDeleted: 0};
    },
    async purgeAdminAudits(asOf, limit) {
      adminAuditPurges.push({asOf: asOf.toISOString(), limit});
      return 1;
    },
    async finishJob(_jobId, status, _finishedAt, summary, failureStage) {
      finishes.push({status, failureStage: failureStage || null, summary: {...summary}});
      return true;
    },
  };
}

async function documentData(firestore, path) {
  const snapshot = await firestore.doc(path).get();
  assert.equal(snapshot.exists, true);
  return snapshot.data();
}

async function fileExists(bucket, storagePath) {
  const [exists] = await bucket.file(storagePath).exists();
  return exists;
}

async function assertHeldData(firestore, bucket, paths) {
  const heldSession = await documentData(firestore, "companionSessions/session-held");
  const heldManager = await documentData(firestore, "users/manager-held");
  assert.equal(heldSession.chatMessages[0].body, "법적 보존 대화");
  assert.deepEqual(heldSession.chatMessages[0].attachments, [
    {fullPath: paths.chatHeld},
  ]);
  assert.equal(heldSession.sharedLatitude, 37.5);
  assert.equal(heldSession.sharedLongitude, 127.0);
  assert.deepEqual(heldSession.sharedLocationHistory, [
    {latitude: 37.5, longitude: 127.0},
  ]);
  assert.equal(heldSession.liveLocationSharingActive, true);
  assert.equal(
      heldManager.managerDocumentFiles.license.fullPath,
      paths.managerHeld,
  );
  assert.equal(
      heldManager.managerDocumentFilePaths.license,
      paths.managerHeld,
  );
  assert.equal(heldManager.managerLicenseStoragePath, paths.managerHeld);
  assert.equal(await fileExists(bucket, paths.chatHeld), true);
  assert.equal(await fileExists(bucket, paths.managerHeld), true);
}

async function clearFirestore() {
  const host = process.env.FIRESTORE_EMULATOR_HOST;
  const response = await fetch(
      `http://${host}/emulator/v1/projects/${projectId}/databases/(default)/documents`,
      {method: "DELETE"},
  );
  assert.equal(response.ok, true, await response.text());
}

function isLoopbackEmulatorHost(value) {
  try {
    const url = new URL(`http://${value}`);
    const hostname = url.hostname.replace(/^\[(.*)]$/, "$1").toLowerCase();
    return Boolean(url.port) &&
      url.username === "" &&
      url.password === "" &&
      url.pathname === "/" &&
      url.search === "" &&
      url.hash === "" &&
      ["localhost", "127.0.0.1", "::1"].includes(hostname);
  } catch (_error) {
    return false;
  }
}
