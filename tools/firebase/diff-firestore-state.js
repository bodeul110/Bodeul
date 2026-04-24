#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const {MANAGED_COLLECTIONS} = require("./lib/baseline-config");
const {
  createCliContext,
  extractRelativeDocumentPath,
  listCollectionDocuments,
} = require("./lib/firebase-toolkit");

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }
  if (!options.filePath) {
    throw new Error("비교할 백업 파일 경로가 필요합니다. --file 옵션을 지정해 주세요.");
  }

  const snapshotPath = path.resolve(process.cwd(), options.filePath);
  const baselineSnapshot = JSON.parse(fs.readFileSync(snapshotPath, "utf8"));
  const context = await createCliContext();
  const currentSnapshot = await createCurrentSnapshot(context);
  const diffSummary = diffSnapshots(baselineSnapshot, currentSnapshot);

  console.log("보들 Firestore 상태 diff");
  console.log(`- 기준 백업: ${snapshotPath}`);
  console.log(`- 비교 대상: 현재 Firestore (${context.projectId})`);
  console.log(`- 추가 문서: ${diffSummary.totalAdded}`);
  console.log(`- 삭제 문서: ${diffSummary.totalRemoved}`);
  console.log(`- 변경 문서: ${diffSummary.totalChanged}`);

  for (const collectionName of MANAGED_COLLECTIONS) {
    const collectionDiff = diffSummary.collections[collectionName];
    if (!collectionDiff) {
      continue;
    }
    if (collectionDiff.added.length === 0
        && collectionDiff.removed.length === 0
        && collectionDiff.changed.length === 0) {
      continue;
    }

    console.log("");
    console.log(`[${collectionName}]`);
    if (collectionDiff.added.length > 0) {
      console.log(`- 추가: ${collectionDiff.added.length}건`);
      for (const item of collectionDiff.added.slice(0, 10)) {
        console.log(`  + ${item}`);
      }
    }
    if (collectionDiff.removed.length > 0) {
      console.log(`- 삭제: ${collectionDiff.removed.length}건`);
      for (const item of collectionDiff.removed.slice(0, 10)) {
        console.log(`  - ${item}`);
      }
    }
    if (collectionDiff.changed.length > 0) {
      console.log(`- 변경: ${collectionDiff.changed.length}건`);
      for (const item of collectionDiff.changed.slice(0, 10)) {
        console.log(`  * ${item}`);
      }
    }
    if (collectionDiff.added.length > 10
        || collectionDiff.removed.length > 10
        || collectionDiff.changed.length > 10) {
      console.log("  ... 생략된 항목이 있습니다.");
    }
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
  console.log("보들 Firestore 상태 diff 스크립트");
  console.log("");
  console.log("사용법:");
  console.log("  node diff-firestore-state.js --file backups/firestore-backup.json");
  console.log("");
  console.log("동작:");
  console.log("- 백업 파일과 현재 Firestore 상태를 비교합니다.");
  console.log("- 컬렉션별 추가/삭제/변경 문서를 요약합니다.");
}

async function createCurrentSnapshot(context) {
  const snapshot = {
    schemaVersion: 1,
    projectId: context.projectId,
    generatedAt: new Date().toISOString(),
    collections: {},
  };

  for (const collectionName of MANAGED_COLLECTIONS) {
    const documents = await listCollectionDocuments(context, collectionName);
    snapshot.collections[collectionName] = documents.map((document) => ({
      id: document.name.split("/").pop(),
      path: extractRelativeDocumentPath(document.name, context.projectId),
      fields: document.fields || {},
    }));
  }
  return snapshot;
}

function diffSnapshots(baseSnapshot, targetSnapshot) {
  const collections = {};
  let totalAdded = 0;
  let totalRemoved = 0;
  let totalChanged = 0;

  for (const collectionName of MANAGED_COLLECTIONS) {
    const baseMap = toDocumentMap(baseSnapshot?.collections?.[collectionName]);
    const targetMap = toDocumentMap(targetSnapshot?.collections?.[collectionName]);

    const added = [];
    const removed = [];
    const changed = [];

    for (const [documentPath, targetDocument] of targetMap.entries()) {
      if (!baseMap.has(documentPath)) {
        added.push(documentPath);
        continue;
      }

      const baseDocument = baseMap.get(documentPath);
      if (baseDocument.normalizedFields !== targetDocument.normalizedFields) {
        changed.push(documentPath);
      }
    }

    for (const documentPath of baseMap.keys()) {
      if (!targetMap.has(documentPath)) {
        removed.push(documentPath);
      }
    }

    added.sort();
    removed.sort();
    changed.sort();

    collections[collectionName] = {added, removed, changed};
    totalAdded += added.length;
    totalRemoved += removed.length;
    totalChanged += changed.length;
  }

  return {
    collections,
    totalAdded,
    totalRemoved,
    totalChanged,
  };
}

function toDocumentMap(documents) {
  const documentMap = new Map();
  for (const document of Array.isArray(documents) ? documents : []) {
    const documentPath = typeof document?.path === "string" ? document.path.trim() : "";
    if (!documentPath) {
      continue;
    }
    documentMap.set(documentPath, {
      normalizedFields: stableStringify(document.fields || {}),
    });
  }
  return documentMap;
}

function stableStringify(value) {
  return JSON.stringify(sortValue(value));
}

function sortValue(value) {
  if (Array.isArray(value)) {
    return value.map((item) => sortValue(item));
  }
  if (!value || typeof value !== "object") {
    return value;
  }

  const sorted = {};
  for (const key of Object.keys(value).sort()) {
    sorted[key] = sortValue(value[key]);
  }
  return sorted;
}

main().catch((error) => {
  console.error("상태 diff 스크립트 실행 중 오류가 발생했습니다.");
  console.error(error);
  process.exitCode = 1;
});
