const assert = require("node:assert/strict");
const test = require("node:test");
const {deleteApp} = require("firebase-admin/app");

const {
  assertExecutionBoundary,
  createFirebaseDependencies,
  initializeFixtureApp,
  parseOptions,
} = require("../scripts/run-retention-firebase-fixture");
const {
  FIXTURE_MARKER,
  OBJECT_PATHS,
  assertExpectedSummary,
  buildFixtureDefinition,
  createScopedLegacyStore,
  createScopedManagerStore,
  createScopedStorageGateway,
} = require("../scripts/lib/retention-firebase-fixture");
const {
  FirebaseLegacyCompanionStore,
  FirebaseManagerDocumentStore,
  emptyRetentionSummary,
} = require("../src/retention");

test("개발 Firebase 픽스처 경로와 합성 데이터 표식을 고정한다", () => {
  const now = new Date("2026-08-23T00:00:00.000Z");
  const fixture = buildFixtureDefinition(now);

  assert.equal(fixture.documents.sessionExpired.data.bodeulFixture.name, FIXTURE_MARKER);
  assert.equal(fixture.documents.sessionExpired.data.bodeulFixture.owner, "bodeul110/Bodeul");
  assert.equal(fixture.documents.sessionExpired.data.bodeulFixture.issue, "222");
  assert.equal(
      fixture.documents.sessionExpired.data.chatMessages[0].attachments[0].fullPath,
      OBJECT_PATHS.sessionExpired,
  );
  assert.equal(
      fixture.documents.managerExpired.data.managerDocumentFiles.idCard.fullPath,
      OBJECT_PATHS.managerExpired,
  );
  assert.equal(
      fixture.documents.managerHeld.data.managerDocumentFiles.nursingLicense.fullPath,
      OBJECT_PATHS.managerHeld,
  );
  assert.equal(fixture.objects.length, 4);
});

test("쓰기 action은 bodeul-dev 확인값 없이는 실행하지 않는다", () => {
  const options = parseOptions([
    "apply",
    "--project",
    "bodeul-dev",
  ]);

  assert.throws(
      () => assertExecutionBoundary(options, {}),
      /--confirm-project bodeul-dev/,
  );
  options.confirmProject = "bodeul-dev";
  assert.doesNotThrow(() => assertExecutionBoundary(options, {}));
});

test("production 프로젝트와 Emulator 환경을 거부한다", () => {
  assert.throws(
      () => assertExecutionBoundary({
        action: "status",
        projectId: "bodeul-prod-110",
        confirmProject: "",
      }, {}),
      /bodeul-dev만 허용/,
  );
  assert.throws(
      () => assertExecutionBoundary({
        action: "status",
        projectId: "bodeul-dev",
        confirmProject: "",
      }, {FIRESTORE_EMULATOR_HOST: "127.0.0.1:8085"}),
      /Emulator 환경을 허용하지 않습니다/,
  );
  assert.throws(
      () => assertExecutionBoundary({
        action: "status",
        projectId: "bodeul-dev",
        confirmProject: "",
      }, {STORAGE_EMULATOR_HOST: "http://127.0.0.1:9199"}),
      /Emulator 환경을 허용하지 않습니다/,
  );
  assert.throws(
      () => assertExecutionBoundary({
        action: "status",
        projectId: "bodeul-dev",
        confirmProject: "",
      }, {GOOGLE_CLOUD_PROJECT: "bodeul-prod-110"}),
      /GOOGLE_CLOUD_PROJECT이 개발 프로젝트와 일치하지 않습니다/,
  );
});

test("Application Default Credentials 경계로 Firestore와 Storage를 초기화한다", async () => {
  const app = initializeFixtureApp(`retention-fixture-unit-${process.pid}`);
  try {
    const dependencies = createFirebaseDependencies(app);
    assert.ok(dependencies.firestore);
    assert.equal(dependencies.bucket.name, "bodeul-dev.firebasestorage.app");
  } finally {
    await deleteApp(app);
  }
});

test("scoped adapter는 고정 문서와 객체 밖의 파기를 차단한다", async () => {
  const firestore = {};
  const bucket = {};
  const legacyStore = createScopedLegacyStore(firestore);
  const managerStore = createScopedManagerStore(firestore);
  const storage = createScopedStorageGateway(bucket);

  await assert.rejects(
      () => legacyStore.applySession({sessionId: "real-session"}, new Date(), storage),
      /픽스처 범위를 벗어난 문서/,
  );
  await assert.rejects(
      () => managerStore.isStillEligible({managerId: "real-manager"}, new Date()),
      /픽스처 범위를 벗어난 문서/,
  );
  await assert.rejects(
      () => storage.deleteChatAttachment("companion-chat-attachments/real/file.pdf"),
      /픽스처 범위를 벗어난 Storage 객체/,
  );

  const foreignStorage = createScopedStorageGateway({
    file() {
      return {
        async getMetadata() {
          return [{metadata: {bodeulFixture: "foreign"}, generation: "1"}];
        },
      };
    },
  });
  await assert.rejects(
      () => foreignStorage.deleteChatAttachment(OBJECT_PATHS.sessionExpired),
      /픽스처 표식이 일치하지 않습니다/,
  );
});

test("문서 guard는 transaction 전 다른 표식의 문서를 거부한다", async () => {
  const firestore = {
    collection() {
      return {
        where() {
          return {
            async get() {
              return {
                docs: [{
                  id: "retention-fixture-manager-expired-v1",
                  data: () => ({role: "MANAGER"}),
                }],
              };
            },
          };
        },
      };
    },
  };
  const store = new FirebaseManagerDocumentStore(firestore, {
    documentGuard: () => false,
  });

  await assert.rejects(
      () => store.preview(new Date()),
      /FIRESTORE_DOCUMENT_GUARD_REJECTED/,
  );
  assert.throws(
      () => new FirebaseManagerDocumentStore(firestore, {documentGuard: true}),
      /FIRESTORE_DOCUMENT_GUARD_INVALID/,
  );
});

test("매니저 transaction은 표식이 바뀐 문서의 참조를 지우지 않는다", async () => {
  let updated = false;
  const reference = {};
  const firestore = {
    collection() {
      return {doc: () => reference};
    },
    async runTransaction(handler) {
      return handler({
        async get() {
          return {
            exists: true,
            id: "retention-fixture-manager-expired-v1",
            data: () => ({role: "MANAGER"}),
          };
        },
        update() {
          updated = true;
        },
      });
    },
  };
  const store = new FirebaseManagerDocumentStore(firestore, {
    documentGuard: (_documentId, data) => Boolean(data?.bodeulFixture),
  });

  await assert.rejects(
      () => store.clearReference({
        managerId: "retention-fixture-manager-expired-v1",
        documentKey: "idCard",
        storagePath: OBJECT_PATHS.managerExpired,
      }, new Date()),
      /FIRESTORE_DOCUMENT_GUARD_REJECTED/,
  );
  assert.equal(updated, false);
});

test("동행 transaction은 표식이 바뀐 문서를 갱신하지 않는다", async () => {
  const now = new Date("2026-08-23T00:00:00.000Z");
  const fixtureData = buildFixtureDefinition(now).documents.sessionExpired.data;
  let updated = false;
  const reference = {
    async get() {
      return {
        exists: true,
        id: "retention-fixture-firestore-expired-v1",
        data: () => fixtureData,
      };
    },
  };
  const firestore = {
    collection() {
      return {doc: () => reference};
    },
    async runTransaction(handler) {
      return handler({
        async get() {
          return {
            exists: true,
            id: "retention-fixture-firestore-expired-v1",
            data: () => ({...fixtureData, bodeulFixture: null}),
          };
        },
        update() {
          updated = true;
        },
      });
    },
  };
  const store = new FirebaseLegacyCompanionStore(firestore, {
    documentGuard: (_documentId, data) => Boolean(data?.bodeulFixture),
  });

  await assert.rejects(
      () => store.applySession(
          {sessionId: "retention-fixture-firestore-expired-v1"},
          now,
          {async deleteChatAttachment() {}},
      ),
      /FIRESTORE_DOCUMENT_GUARD_REJECTED/,
  );
  assert.equal(updated, false);
});

test("dry-run과 apply의 예상 집계를 엄격하게 확인한다", () => {
  const dryRun = expectedSummary(false);
  const apply = expectedSummary(true);

  assert.doesNotThrow(() => assertExpectedSummary(dryRun, false));
  assert.doesNotThrow(() => assertExpectedSummary(apply, true));
  apply.managerDocumentsDeleted = 0;
  assert.throws(
      () => assertExpectedSummary(apply, true),
      /managerDocumentsDeleted/,
  );
});

function expectedSummary(apply) {
  return {
    ...emptyRetentionSummary(apply ? "APPLY" : "DRY_RUN", new Date()),
    firestoreMessageCandidates: 1,
    firestoreAttachmentCandidates: 1,
    firestoreLocationCandidates: 1,
    firestoreLegalHoldSkips: 3,
    managerDocumentCandidates: 1,
    managerDocumentLegalHoldSkips: 1,
    firestoreMessagesRedacted: apply ? 1 : 0,
    firestoreAttachmentsDeleted: apply ? 1 : 0,
    firestoreLocationsCleared: apply ? 1 : 0,
    managerDocumentsDeleted: apply ? 1 : 0,
  };
}
