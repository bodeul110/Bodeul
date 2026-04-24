#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const {MANAGED_COLLECTIONS} = require("./lib/baseline-config");
const {
  createCliContext,
  deleteCollectionDocuments,
  getCollectionCounts,
  patchDocumentFields,
} = require("./lib/firebase-toolkit");

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }
  if (!options.filePath) {
    throw new Error("복원할 백업 파일 경로가 필요합니다. --file 옵션을 지정해 주세요.");
  }

  const snapshotPath = path.resolve(process.cwd(), options.filePath);
  const snapshot = JSON.parse(fs.readFileSync(snapshotPath, "utf8"));
  const restoreCollections = resolveRestoreCollections(snapshot);
  const context = await createCliContext();
  const currentCounts = await getCollectionCounts(context, MANAGED_COLLECTIONS);

  printPlan(context.projectId, snapshotPath, snapshot, restoreCollections, currentCounts, options.apply);

  if (!options.apply) {
    console.log("");
    console.log("dry-run 완료: 실제 삭제와 복원은 수행하지 않았습니다.");
    return;
  }

  for (const collectionName of MANAGED_COLLECTIONS) {
    await deleteCollectionDocuments(context, collectionName);
  }

  for (const collectionName of restoreCollections) {
    const documents = snapshot.collections[collectionName] || [];
    for (const document of documents) {
      await patchDocumentFields(context, document.path, document.fields || {});
    }
  }

  console.log("");
  console.log("백업 복원이 끝났습니다.");
}

function parseOptions(args) {
  const fileIndex = args.indexOf("--file");
  return {
    apply: args.includes("--apply"),
    filePath: fileIndex >= 0 ? args[fileIndex + 1] : "",
    help: args.includes("--help") || args.includes("-h"),
  };
}

function printHelp() {
  console.log("보들 Firestore 백업 복원 스크립트");
  console.log("");
  console.log("사용법:");
  console.log("  node restore-firestore-state.js --file backups/firestore-backup.json");
  console.log("  node restore-firestore-state.js --apply --file backups/firestore-backup.json");
  console.log("");
  console.log("주의:");
  console.log("- 이 스크립트는 Firestore 문서만 복원합니다.");
  console.log("- Firebase Authentication 계정은 별도로 유지됩니다.");
}

function resolveRestoreCollections(snapshot) {
  if (!snapshot || typeof snapshot !== "object" || typeof snapshot.collections !== "object") {
    throw new Error("백업 파일 형식이 올바르지 않습니다.");
  }
  return MANAGED_COLLECTIONS.filter((collectionName) =>
    Object.prototype.hasOwnProperty.call(snapshot.collections, collectionName),
  );
}

function printPlan(projectId, snapshotPath, snapshot, restoreCollections, currentCounts, apply) {
  console.log("보들 Firestore 백업 복원 계획");
  console.log(`프로젝트: ${projectId}`);
  console.log(`모드: ${apply ? "apply" : "dry-run"}`);
  console.log(`백업 파일: ${snapshotPath}`);
  console.log(`백업 생성 시각: ${snapshot.generatedAt || "알 수 없음"}`);
  console.log("");
  console.log("현재 컬렉션 문서 수:");
  for (const collectionName of MANAGED_COLLECTIONS) {
    console.log(`- ${collectionName}: ${currentCounts[collectionName]}건`);
  }
  console.log("");
  console.log("복원될 컬렉션 문서 수:");
  for (const collectionName of restoreCollections) {
    const documents = snapshot.collections[collectionName] || [];
    console.log(`- ${collectionName}: ${documents.length}건`);
  }
}

main().catch((error) => {
  console.error("복원 스크립트 실행 중 오류가 발생했습니다.");
  console.error(error);
  process.exitCode = 1;
});
