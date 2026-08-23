const assert = require("node:assert/strict");
const {createHash} = require("node:crypto");
const {readFileSync} = require("node:fs");
const {resolve} = require("node:path");
const test = require("node:test");
const {deleteApp} = require("firebase-admin/app");

const {
  createFirebaseDependencies,
  initializeFixtureApp,
} = require("../scripts/run-retention-firebase-fixture");
const {
  PRODUCTION_APPLY_CONFIRMATION,
  PRODUCTION_FIXTURE_ID,
  assertProductionExecutionBoundary,
  parseOptions,
} = require("../scripts/run-retention-firebase-production-fixture");
const {
  DEVELOPMENT_PROFILE,
  PRODUCTION_PROFILE,
  buildFixtureDefinition,
  createScopedStorageGateway,
  inspectFixture,
} = require("../scripts/lib/retention-firebase-fixture");

const PROJECT_ID = "bodeul-prod-110";
const GITHUB_SHA = "0123456789abcdef0123456789abcdef01234567";
const TEST_EXECUTION_TOKEN = "production-fixture-unit-test-token";
const TEST_EXECUTION_TOKEN_SHA256 = createHash("sha256")
    .update(TEST_EXECUTION_TOKEN, "utf8")
    .digest("hex");

test("production 픽스처는 개발 픽스처와 표식과 경로를 분리한다", () => {
  const now = new Date("2026-08-23T00:00:00.000Z");
  const fixture = buildFixtureDefinition(now, PRODUCTION_PROFILE);

  assert.equal(PRODUCTION_PROFILE.projectId, PROJECT_ID);
  assert.notEqual(PRODUCTION_PROFILE.marker, DEVELOPMENT_PROFILE.marker);
  assert.match(
      fixture.documents.sessionExpired.path,
      /^companionSessions\/retention-fixture-production-/,
  );
  assert.match(
      fixture.objects[0],
      /retention-fixture-production-firestore-expired-v1/,
  );
  assert.equal(
      fixture.documents.sessionExpired.data.bodeulFixture.name,
      "bodeul-retention-firebase-production-v1",
  );
});

test("production 읽기 action도 프로젝트와 fixture ID를 확인한다", () => {
  const options = parseOptions([
    "status",
    "--project", PROJECT_ID,
    "--fixture-id", PRODUCTION_FIXTURE_ID,
    "--confirm-commit", GITHUB_SHA,
  ]);

  assert.doesNotThrow(() => assertBoundary(options));
  assert.throws(
      () => assertBoundary({...options, projectId: "bodeul-dev"}),
      /bodeul-prod-110만 허용/,
  );
  assert.throws(
      () => assertBoundary({...options, fixtureId: "other"}),
      /--fixture-id issue-222-production-v1/,
  );
  assert.throws(
      () => assertBoundary(options, {
        GOOGLE_CLOUD_PROJECT: "bodeul-dev",
      }),
      /GOOGLE_CLOUD_PROJECT이 production 프로젝트와 일치하지 않습니다/,
  );
  assert.throws(
      () => assertBoundary(options, {
        FIRESTORE_EMULATOR_HOST: "127.0.0.1:8085",
      }),
      /Emulator를 허용하지 않습니다/,
  );
});

test("production action은 로컬 ADC와 잘못된 Environment 토큰을 거부한다", () => {
  const options = productionReadOptions("status");

  assert.throws(
      () => assertProductionExecutionBoundary(
          options,
          {},
          TEST_EXECUTION_TOKEN_SHA256,
      ),
      /보호된 GitHub Actions/,
  );
  assert.throws(
      () => assertProductionExecutionBoundary(
          options,
          githubEnvironment({FIREBASE_RETENTION_EXECUTION_TOKEN: "wrong"}),
          TEST_EXECUTION_TOKEN_SHA256,
      ),
      /Environment 실행 토큰/,
  );
  assert.throws(
      () => assertBoundary({...options, confirmCommit: "f".repeat(40)}),
      /--confirm-commit/,
  );
});

test("production WIF 리소스 ID는 GCP 길이 제한과 workflow 기준을 지킨다", () => {
  const providerId = "bodeul-retention-prod";
  const serviceAccountId = "bodeul-retention-operator";
  const workflow = readFileSync(resolve(
      __dirname,
      "../../.github/workflows/firebase-retention-production.yml",
  ), "utf8");

  assert.ok(providerId.length <= 32);
  assert.ok(serviceAccountId.length <= 30);
  assert.match(workflow, new RegExp(`/providers/${providerId}\\$`));
  assert.match(
      workflow,
      new RegExp(`${serviceAccountId}@\\$\\{FIREBASE_PROJECT_ID\\}`),
  );
});

test("production setup은 이중 확인과 복구 증적 없이는 실행하지 않는다", () => {
  const options = productionWriteOptions("setup");

  assert.throws(
      () => assertBoundary({...options, confirmProject: ""}),
      /--confirm-project bodeul-prod-110/,
  );
  assert.throws(
      () => assertBoundary({...options, confirmFixtureId: ""}),
      /--confirm-fixture-id issue-222-production-v1/,
  );
  assert.throws(
      () => assertBoundary({...options, firestoreBackupReference: ""}),
      /--firestore-backup-reference/,
  );
  assert.throws(
      () => assertBoundary({
        ...options,
        storageInventoryReference:
          "gs://other/storage-inventory/verified/inventory.json",
      }),
      /--storage-inventory-reference/,
  );
  assert.doesNotThrow(() => assertBoundary(options));
});

test("production APPLY는 별도 확인값과 정책 검토 증적을 요구한다", () => {
  const options = {
    ...productionWriteOptions("apply"),
    confirmApply: PRODUCTION_APPLY_CONFIRMATION,
    policyReviewReference:
      "https://github.com/bodeul110/Bodeul/issues/222#issuecomment-5385359314",
  };

  assert.throws(
      () => assertBoundary({...options, confirmApply: ""}),
      /--confirm-apply APPLY-ISSUE-222-PRODUCTION-V1/,
  );
  assert.throws(
      () => assertBoundary({...options, policyReviewReference: ""}),
      /--policy-review-reference/,
  );
  assert.throws(
      () => assertBoundary({
        ...options,
        policyReviewReference: "unsafe reference with spaces",
      }),
      /--policy-review-reference이 허용된 증적 경로 기준과 다릅니다/,
  );
  assert.throws(
      () => assertBoundary({
        ...options,
        policyReviewReference:
          "https://github.com/bodeul110/Bodeul/issues/223#issuecomment-1",
      }),
      /--policy-review-reference이 허용된 증적 경로 기준과 다릅니다/,
  );
  assert.doesNotThrow(() => assertBoundary({
    ...options,
    policyReviewReference:
      "https://www.notion.so/bodeul/privacy-policy-review-0123456789abcdef",
  }));
  assert.doesNotThrow(() => assertBoundary(options));
});

test("production 프로필로 Admin SDK 프로젝트와 버킷을 고정한다", async () => {
  const app = initializeFixtureApp(
      `retention-production-fixture-unit-${process.pid}`,
      PRODUCTION_PROFILE,
  );
  try {
    const dependencies = createFirebaseDependencies(app, PRODUCTION_PROFILE);
    assert.ok(dependencies.firestore);
    assert.equal(dependencies.bucket.name, "bodeul-prod-110.firebasestorage.app");
  } finally {
    await deleteApp(app);
  }
});

test("production scoped Storage adapter는 개발 픽스처 경로를 거부한다", async () => {
  const storage = createScopedStorageGateway({}, PRODUCTION_PROFILE);

  await assert.rejects(
      () => storage.deleteChatAttachment(
          DEVELOPMENT_PROFILE.objectPaths.sessionExpired,
      ),
      /픽스처 범위를 벗어난 Storage 객체/,
  );
});

test("고정 경로에 다른 문서가 있어도 내용을 평가하거나 출력하지 않는다", async () => {
  const foreignBody = "출력되면 안 되는 실제 데이터";
  const foreignPath =
    `companionSessions/${PRODUCTION_PROFILE.documentIds.sessionExpired}`;
  const firestore = {
    doc(path) {
      return {
        async get() {
          if (path === foreignPath) {
            return {
              exists: true,
              data: () => ({
                bodeulFixture: {name: "foreign"},
                currentStatus: "COMPLETED",
                chatMessages: [{body: foreignBody}],
              }),
            };
          }
          return {exists: false};
        },
      };
    },
  };
  const bucket = {
    file() {
      return {
        async exists() {
          return [false];
        },
      };
    },
  };

  const status = await inspectFixture({
    firestore,
    bucket,
    profile: PRODUCTION_PROFILE,
    now: new Date("2026-08-23T00:00:00.000Z"),
  });

  assert.equal(status.phase, "PARTIAL");
  assert.equal(status.documents.sessionExpired.owned, false);
  assert.equal(status.evaluations.sessionExpired, null);
  assert.doesNotMatch(JSON.stringify(status), new RegExp(foreignBody));
});

function productionWriteOptions(action) {
  return {
    action,
    projectId: PROJECT_ID,
    confirmProject: PROJECT_ID,
    confirmCommit: GITHUB_SHA,
    fixtureId: PRODUCTION_FIXTURE_ID,
    confirmFixtureId: PRODUCTION_FIXTURE_ID,
    confirmApply: "",
    firestoreBackupReference:
      "gs://bodeul-prod-110-db-backups/firestore/verified/fixture/all.export_metadata",
    storageInventoryReference:
      "gs://bodeul-prod-110-db-backups/storage-inventory/verified/fixture.json",
    policyReviewReference: "",
    help: false,
  };
}

function productionReadOptions(action) {
  return {
    ...productionWriteOptions(action),
    confirmProject: "",
    confirmFixtureId: "",
    firestoreBackupReference: "",
    storageInventoryReference: "",
  };
}

function githubEnvironment(overrides = {}) {
  return {
    GITHUB_ACTIONS: "true",
    GITHUB_REPOSITORY: "bodeul110/Bodeul",
    GITHUB_REF: "refs/heads/master",
    GITHUB_SHA,
    FIREBASE_RETENTION_ENVIRONMENT: "firebase-retention-production",
    FIREBASE_RETENTION_EXECUTION_TOKEN: TEST_EXECUTION_TOKEN,
    ...overrides,
  };
}

function assertBoundary(options, envOverrides = {}) {
  return assertProductionExecutionBoundary(
      options,
      githubEnvironment(envOverrides),
      TEST_EXECUTION_TOKEN_SHA256,
  );
}
