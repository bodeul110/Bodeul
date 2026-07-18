#!/usr/bin/env node

const {getApps, initializeApp} = require("firebase-admin/app");
const {
  retentionErrorCode,
  runConfiguredRetentionJob,
} = require("../src/retention");

async function main() {
  const options = parseOptions(process.argv.slice(2));
  const databaseUrl = String(process.env.RETENTION_DATABASE_URL || "").trim();
  if (!databaseUrl) {
    throw new Error("RETENTION_DATABASE_URL 환경변수가 필요합니다.");
  }
  if (!options.projectId) {
    throw new Error("--project로 Firebase 프로젝트 ID를 지정해 주세요.");
  }
  if (options.apply && options.confirmProject !== options.projectId) {
    throw new Error("실제 파기에는 --confirm-project에 같은 프로젝트 ID가 필요합니다.");
  }

  process.env.GCLOUD_PROJECT = options.projectId;
  process.env.GOOGLE_CLOUD_PROJECT = options.projectId;
  if (!getApps().length) {
    initializeApp({projectId: options.projectId});
  }

  const summary = await runConfiguredRetentionJob({
    databaseUrl,
    apply: options.apply,
  });
  console.log(JSON.stringify(summary, null, 2));
}

function parseOptions(args) {
  const options = {
    apply: false,
    projectId: "",
    confirmProject: "",
  };
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument === "--dry-run") {
      options.apply = false;
    } else if (argument === "--apply") {
      options.apply = true;
    } else if (argument === "--project") {
      options.projectId = String(args[index + 1] || "").trim();
      index += 1;
    } else if (argument === "--confirm-project") {
      options.confirmProject = String(args[index + 1] || "").trim();
      index += 1;
    } else if (argument === "--help" || argument === "-h") {
      printHelp();
      process.exit(0);
    } else {
      throw new Error(`지원하지 않는 옵션입니다: ${argument}`);
    }
  }
  return options;
}

function printHelp() {
  console.log("BoDeul 개인정보 자동 파기 작업");
  console.log("");
  console.log("dry-run:");
  console.log("  npm run retention:dry-run -- --project bodeul-dev");
  console.log("");
  console.log("apply:");
  console.log("  npm run retention:apply -- --project bodeul-dev --confirm-project bodeul-dev");
}

main().catch((error) => {
  console.error(`자동 파기 작업이 실패했습니다: ${retentionErrorCode(error)}`);
  process.exitCode = 1;
});
