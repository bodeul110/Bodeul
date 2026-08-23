#!/usr/bin/env node

const {createHash, timingSafeEqual} = require("node:crypto");
const {deleteApp} = require("firebase-admin/app");

const {
  createFirebaseDependencies,
  initializeFixtureApp,
  runAction,
} = require("./run-retention-firebase-fixture");
const {
  PRODUCTION_PROFILE,
} = require("./lib/retention-firebase-fixture");

const PRODUCTION_FIXTURE_ID = "issue-222-production-v1";
const PRODUCTION_APPLY_CONFIRMATION = "APPLY-ISSUE-222-PRODUCTION-V1";
const PRODUCTION_EXECUTION_TOKEN_SHA256 =
  "e4b62c843b40dda5421a5bdd00a3b74130dd46e5894879d561fbfaf44c51b7da";
const WRITE_ACTIONS = new Set(["setup", "apply", "cleanup"]);
const BACKUP_REQUIRED_ACTIONS = new Set(["setup", "apply"]);
const SUPPORTED_ACTIONS = new Set(["setup", "status", "dry-run", "apply", "cleanup"]);
const FIRESTORE_BACKUP_PATTERN =
  /^gs:\/\/bodeul-prod-110-db-backups\/firestore\/verified\/[A-Za-z0-9._~/-]+\.export_metadata$/;
const STORAGE_INVENTORY_PATTERN =
  /^gs:\/\/bodeul-prod-110-db-backups\/storage-inventory\/verified\/[A-Za-z0-9._~/-]+\.json$/;
const POLICY_REVIEW_PATTERN = new RegExp(
    "^https://(?:(?:app\\.notion\\.com|www\\.notion\\.so)/" +
    "[A-Za-z0-9._~:/?#=&%-]+|github\\.com/bodeul110/Bodeul/" +
    "issues/222#issuecomment-[0-9]+)$",
);

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }
  assertProductionExecutionBoundary(options, process.env);

  const projectId = PRODUCTION_PROFILE.projectId;
  process.env.FIREBASE_PROJECT_ID = projectId;
  process.env.GCLOUD_PROJECT = projectId;
  process.env.GOOGLE_CLOUD_PROJECT = projectId;
  const app = initializeFixtureApp(
      `retention-firebase-production-fixture-${process.pid}`,
      PRODUCTION_PROFILE,
  );

  try {
    const dependencies = createFirebaseDependencies(app, PRODUCTION_PROFILE);
    const result = await runAction(
        options.action,
        dependencies,
        PRODUCTION_PROFILE,
    );
    console.log(JSON.stringify(result, null, 2));
  } finally {
    await deleteApp(app);
  }
}

function parseOptions(args) {
  const options = {
    action: "",
    projectId: "",
    confirmProject: "",
    confirmCommit: "",
    fixtureId: "",
    confirmFixtureId: "",
    confirmApply: "",
    firestoreBackupReference: "",
    storageInventoryReference: "",
    policyReviewReference: "",
    help: false,
  };
  const valueOptions = new Map([
    ["--project", "projectId"],
    ["--confirm-project", "confirmProject"],
    ["--confirm-commit", "confirmCommit"],
    ["--fixture-id", "fixtureId"],
    ["--confirm-fixture-id", "confirmFixtureId"],
    ["--confirm-apply", "confirmApply"],
    ["--firestore-backup-reference", "firestoreBackupReference"],
    ["--storage-inventory-reference", "storageInventoryReference"],
    ["--policy-review-reference", "policyReviewReference"],
  ]);
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (valueOptions.has(argument)) {
      options[valueOptions.get(argument)] = String(args[index + 1] || "").trim();
      index += 1;
    } else if (argument === "--help" || argument === "-h") {
      options.help = true;
    } else if (!options.action) {
      options.action = argument;
    } else {
      throw new Error(`지원하지 않는 옵션입니다: ${argument}`);
    }
  }
  return options;
}

function assertProductionExecutionBoundary(
    options,
    env,
    expectedTokenSha256 = PRODUCTION_EXECUTION_TOKEN_SHA256,
) {
  const projectId = PRODUCTION_PROFILE.projectId;
  if (!SUPPORTED_ACTIONS.has(options.action)) {
    throw new Error("action은 setup, status, dry-run, apply, cleanup 중 하나여야 합니다.");
  }
  if (options.projectId !== projectId) {
    throw new Error(`production 프로젝트 ${projectId}만 허용합니다.`);
  }
  if (options.fixtureId !== PRODUCTION_FIXTURE_ID) {
    throw new Error(`--fixture-id ${PRODUCTION_FIXTURE_ID}가 필요합니다.`);
  }
  assertWorkflowExecutionContext(options, env, expectedTokenSha256);
  if (WRITE_ACTIONS.has(options.action)) {
    if (options.confirmProject !== projectId) {
      throw new Error(`쓰기 action에는 --confirm-project ${projectId}가 필요합니다.`);
    }
    if (options.confirmFixtureId !== PRODUCTION_FIXTURE_ID) {
      throw new Error(
          `쓰기 action에는 --confirm-fixture-id ${PRODUCTION_FIXTURE_ID}가 필요합니다.`,
      );
    }
  }
  if (BACKUP_REQUIRED_ACTIONS.has(options.action)) {
    assertScopedReference(
        options.firestoreBackupReference,
        "--firestore-backup-reference",
        FIRESTORE_BACKUP_PATTERN,
    );
    assertScopedReference(
        options.storageInventoryReference,
        "--storage-inventory-reference",
        STORAGE_INVENTORY_PATTERN,
    );
  }
  if (options.action === "apply") {
    if (options.confirmApply !== PRODUCTION_APPLY_CONFIRMATION) {
      throw new Error(
          `APPLY에는 --confirm-apply ${PRODUCTION_APPLY_CONFIRMATION}이 필요합니다.`,
      );
    }
    assertScopedReference(
        options.policyReviewReference,
        "--policy-review-reference",
        POLICY_REVIEW_PATTERN,
    );
  }
  for (const variableName of [
    "FIREBASE_PROJECT_ID",
    "GCLOUD_PROJECT",
    "GOOGLE_CLOUD_PROJECT",
  ]) {
    const configuredProject = String(env[variableName] || "").trim();
    if (configuredProject && configuredProject !== projectId) {
      throw new Error(`${variableName}이 production 프로젝트와 일치하지 않습니다.`);
    }
  }
  if (String(env.FIRESTORE_EMULATOR_HOST || "").trim() ||
      String(env.FIREBASE_STORAGE_EMULATOR_HOST || "").trim() ||
      String(env.STORAGE_EMULATOR_HOST || "").trim()) {
    throw new Error("이 도구는 실제 production Firebase 전용이며 Emulator를 허용하지 않습니다.");
  }
}

function assertWorkflowExecutionContext(options, env, expectedTokenSha256) {
  if (String(env.GITHUB_ACTIONS || "") !== "true" ||
      String(env.GITHUB_REPOSITORY || "") !== "bodeul110/Bodeul" ||
      String(env.GITHUB_REF || "") !== "refs/heads/master" ||
      String(env.FIREBASE_RETENTION_ENVIRONMENT || "") !==
        "firebase-retention-production") {
    throw new Error("production 픽스처는 보호된 GitHub Actions에서만 실행합니다.");
  }
  const githubSha = String(env.GITHUB_SHA || "").trim();
  if (!/^[0-9a-f]{40}$/.test(githubSha) || options.confirmCommit !== githubSha) {
    throw new Error("--confirm-commit이 workflow의 master commit SHA와 일치해야 합니다.");
  }
  if (!matchesExecutionToken(
      String(env.FIREBASE_RETENTION_EXECUTION_TOKEN || ""),
      expectedTokenSha256,
  )) {
    throw new Error("production Environment 실행 토큰이 일치하지 않습니다.");
  }
}

function matchesExecutionToken(value, expectedSha256) {
  if (!value || !/^[0-9a-f]{64}$/.test(expectedSha256)) {
    return false;
  }
  const actual = createHash("sha256").update(value, "utf8").digest();
  const expected = Buffer.from(expectedSha256, "hex");
  return actual.length === expected.length && timingSafeEqual(actual, expected);
}

function assertScopedReference(value, optionName, pattern) {
  if (!value || !pattern.test(value)) {
    throw new Error(`${optionName}이 허용된 증적 경로 기준과 다릅니다.`);
  }
}

function printHelp() {
  const projectId = PRODUCTION_PROFILE.projectId;
  console.log("BoDeul production Firebase 자동 파기 격리 픽스처");
  console.log("");
  console.log("모든 action:");
  console.log(`  --project ${projectId} --fixture-id ${PRODUCTION_FIXTURE_ID}`);
  console.log("  --confirm-commit <workflow의 master commit SHA 40자>");
  console.log("");
  console.log("쓰기 action:");
  console.log(`  --confirm-project ${projectId}`);
  console.log(`  --confirm-fixture-id ${PRODUCTION_FIXTURE_ID}`);
  console.log("");
  console.log("setup/apply:");
  console.log("  --firestore-backup-reference <검증된 export metadata 객체>");
  console.log("  --storage-inventory-reference <검증된 inventory JSON 객체>");
  console.log("");
  console.log("apply 추가 확인:");
  console.log(`  --confirm-apply ${PRODUCTION_APPLY_CONFIRMATION}`);
  console.log("  --policy-review-reference <승인된 Notion URL 또는 #222 댓글 URL>");
  console.log("");
  console.log("일반 production 데이터와 PostgreSQL은 처리하지 않습니다.");
}

if (require.main === module) {
  main().catch((error) => {
    console.error(`production Firebase 파기 픽스처 작업이 실패했습니다: ${error.message}`);
    process.exitCode = 1;
  });
}

module.exports = {
  PRODUCTION_APPLY_CONFIRMATION,
  PRODUCTION_EXECUTION_TOKEN_SHA256,
  PRODUCTION_FIXTURE_ID,
  assertProductionExecutionBoundary,
  matchesExecutionToken,
  parseOptions,
};
