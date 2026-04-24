#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const {MANAGED_COLLECTIONS} = require("./lib/baseline-config");
const {
  buildBackupFileName,
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

  const context = await createCliContext();
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

  const outputPath = resolveOutputPath(options.outputPath);
  fs.mkdirSync(path.dirname(outputPath), {recursive: true});
  fs.writeFileSync(outputPath, `${JSON.stringify(snapshot, null, 2)}\n`, "utf8");

  console.log("Firestore 백업을 저장했습니다.");
  console.log(`- 파일: ${outputPath}`);
  for (const collectionName of MANAGED_COLLECTIONS) {
    console.log(`- ${collectionName}: ${snapshot.collections[collectionName].length}건`);
  }
}

function parseOptions(args) {
  const outputIndex = args.indexOf("--output");
  return {
    help: args.includes("--help") || args.includes("-h"),
    outputPath: outputIndex >= 0 ? args[outputIndex + 1] : "",
  };
}

function printHelp() {
  console.log("보들 Firestore 백업 스크립트");
  console.log("");
  console.log("사용법:");
  console.log("  node backup-firestore-state.js");
  console.log("  node backup-firestore-state.js --output backups/my-backup.json");
}

function resolveOutputPath(outputPath) {
  if (outputPath) {
    return path.resolve(process.cwd(), outputPath);
  }
  return path.resolve(process.cwd(), "backups", buildBackupFileName());
}

main().catch((error) => {
  console.error("백업 스크립트 실행 중 오류가 발생했습니다.");
  console.error(error);
  process.exitCode = 1;
});
