#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const {MANAGED_COLLECTIONS} = require("./lib/baseline-config");
const {validateBackupSnapshot} = require("./lib/backup-validator");

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }
  if (!options.filePath) {
    throw new Error("검증할 백업 파일 경로가 필요합니다. --file 옵션을 지정해 주세요.");
  }

  const snapshotPath = path.resolve(process.cwd(), options.filePath);
  const snapshot = JSON.parse(fs.readFileSync(snapshotPath, "utf8"));
  const result = validateBackupSnapshot(snapshot, MANAGED_COLLECTIONS);

  console.log("보들 Firestore 백업 검증 결과");
  console.log(`- 파일: ${snapshotPath}`);
  console.log(`- schemaVersion: ${snapshot?.schemaVersion ?? "없음"}`);
  console.log(`- 생성 시각: ${snapshot?.generatedAt || "알 수 없음"}`);
  console.log(`- 오류: ${result.errors.length}건`);
  console.log(`- 경고: ${result.warnings.length}건`);

  if (result.errors.length > 0) {
    console.log("");
    console.log("오류:");
    for (const message of result.errors) {
      console.log(`- ${message}`);
    }
  }

  if (result.warnings.length > 0) {
    console.log("");
    console.log("경고:");
    for (const message of result.warnings) {
      console.log(`- ${message}`);
    }
  }

  if (result.errors.length === 0 && result.warnings.length === 0) {
    console.log("");
    console.log("백업 구조가 현재 도구 기준에 맞습니다.");
  }

  if (result.errors.length > 0) {
    process.exitCode = 1;
  }
}

function parseOptions(args) {
  const fileIndex = args.indexOf("--file");
  return {
    filePath: fileIndex >= 0 ? args[fileIndex + 1] : "",
    help: args.includes("--help") || args.includes("-h"),
  };
}

function printHelp() {
  console.log("보들 Firestore 백업 검증 스크립트");
  console.log("");
  console.log("사용법:");
  console.log("  node validate-firestore-backup.js --file backups/firestore-backup.json");
}

main().catch((error) => {
  console.error("백업 검증 스크립트 실행 중 오류가 발생했습니다.");
  console.error(error);
  process.exitCode = 1;
});
