#!/usr/bin/env node

const {applicationDefault, deleteApp, initializeApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
const {getStorage} = require("firebase-admin/storage");

const {
  DEVELOPMENT_PROFILE,
  DEVELOPMENT_PROJECT_ID,
  cleanupFixture,
  inspectFixture,
  runFixtureRetention,
  setupFixture,
} = require("./lib/retention-firebase-fixture");

const WRITE_ACTIONS = new Set(["setup", "apply", "cleanup"]);
const SUPPORTED_ACTIONS = new Set(["setup", "status", "dry-run", "apply", "cleanup"]);

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }
  assertExecutionBoundary(options, process.env);

  process.env.FIREBASE_PROJECT_ID = options.projectId;
  process.env.GCLOUD_PROJECT = options.projectId;
  process.env.GOOGLE_CLOUD_PROJECT = options.projectId;
  const app = initializeFixtureApp(`retention-firebase-fixture-${process.pid}`);

  try {
    const dependencies = createFirebaseDependencies(app);
    const result = await runAction(options.action, dependencies);
    console.log(JSON.stringify(result, null, 2));
  } finally {
    await deleteApp(app);
  }
}

function initializeFixtureApp(appName, profile = DEVELOPMENT_PROFILE) {
  const storageBucket = `${profile.projectId}.firebasestorage.app`;
  return initializeApp({
    projectId: profile.projectId,
    storageBucket,
    credential: applicationDefault(),
  }, appName);
}

function createFirebaseDependencies(app, profile = DEVELOPMENT_PROFILE) {
  const storageBucket = `${profile.projectId}.firebasestorage.app`;
  return {
    firestore: getFirestore(app),
    bucket: getStorage(app).bucket(storageBucket),
  };
}

async function runAction(
    action,
    dependencies,
    profile = DEVELOPMENT_PROFILE,
) {
  if (action === "setup") {
    return setupFixture({...dependencies, profile});
  }
  if (action === "status") {
    return inspectFixture({...dependencies, profile});
  }
  if (action === "dry-run") {
    return runFixtureRetention({...dependencies, profile, apply: false});
  }
  if (action === "apply") {
    return runFixtureRetention({...dependencies, profile, apply: true});
  }
  if (action === "cleanup") {
    return cleanupFixture({...dependencies, profile});
  }
  throw new Error(`지원하지 않는 action입니다: ${action}`);
}

function parseOptions(args) {
  const options = {
    action: "",
    projectId: "",
    confirmProject: "",
    help: false,
  };
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument === "--project") {
      options.projectId = String(args[index + 1] || "").trim();
      index += 1;
    } else if (argument === "--confirm-project") {
      options.confirmProject = String(args[index + 1] || "").trim();
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

function assertExecutionBoundary(options, env) {
  if (!SUPPORTED_ACTIONS.has(options.action)) {
    throw new Error("action은 setup, status, dry-run, apply, cleanup 중 하나여야 합니다.");
  }
  if (options.projectId !== DEVELOPMENT_PROJECT_ID) {
    throw new Error(`개발 프로젝트 ${DEVELOPMENT_PROJECT_ID}만 허용합니다.`);
  }
  if (WRITE_ACTIONS.has(options.action) &&
      options.confirmProject !== DEVELOPMENT_PROJECT_ID) {
    throw new Error(
        `쓰기 action에는 --confirm-project ${DEVELOPMENT_PROJECT_ID}가 필요합니다.`,
    );
  }
  for (const variableName of [
    "FIREBASE_PROJECT_ID",
    "GCLOUD_PROJECT",
    "GOOGLE_CLOUD_PROJECT",
  ]) {
    const configuredProject = String(env[variableName] || "").trim();
    if (configuredProject && configuredProject !== DEVELOPMENT_PROJECT_ID) {
      throw new Error(`${variableName}이 개발 프로젝트와 일치하지 않습니다.`);
    }
  }
  if (String(env.FIRESTORE_EMULATOR_HOST || "").trim() ||
      String(env.FIREBASE_STORAGE_EMULATOR_HOST || "").trim() ||
      String(env.STORAGE_EMULATOR_HOST || "").trim()) {
    throw new Error("이 도구는 실제 개발 Firebase 전용이며 Emulator 환경을 허용하지 않습니다.");
  }
}

function printHelp() {
  console.log("BoDeul 개발 Firebase 자동 파기 픽스처");
  console.log("");
  console.log("읽기:");
  console.log("  npm run retention:firebase-fixture -- status --project bodeul-dev");
  console.log("  npm run retention:firebase-fixture -- dry-run --project bodeul-dev");
  console.log("");
  console.log("쓰기:");
  console.log("  npm run retention:firebase-fixture -- setup --project bodeul-dev --confirm-project bodeul-dev");
  console.log("  npm run retention:firebase-fixture -- apply --project bodeul-dev --confirm-project bodeul-dev");
  console.log("  npm run retention:firebase-fixture -- cleanup --project bodeul-dev --confirm-project bodeul-dev");
  console.log("");
  console.log("고정된 합성 문서와 Storage 객체만 다루며 production 프로젝트는 거부합니다.");
}

if (require.main === module) {
  main().catch((error) => {
    console.error(`개발 Firebase 파기 픽스처 작업이 실패했습니다: ${error.message}`);
    process.exitCode = 1;
  });
}

module.exports = {
  assertExecutionBoundary,
  createFirebaseDependencies,
  initializeFixtureApp,
  parseOptions,
  runAction,
};
