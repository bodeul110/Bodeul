#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const {
  createCliContext,
  deleteStorageObject,
  getStorageObject,
  listCollectionDocuments,
  listStorageObjects,
} = require("./lib/firebase-toolkit");

const DOCUMENT_KEYS = ["idCard", "license", "criminalRecord"];
const STORAGE_PREFIX = "manager-documents/";

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }

  const context = await createCliContext();
  const managerDocuments = await loadManagerDocuments(context);
  const references = collectReferences(managerDocuments);
  const objectChecks = await resolveStorageChecks(context, references);
  const storageObjects = await listStorageObjects(context, STORAGE_PREFIX);
  const orphanObjects = resolveOrphanObjects(storageObjects, references);

  const deletionDecision = resolveDeletionDecision(
      {
        managerDocuments,
        objectChecks,
        orphanObjects,
      },
      options,
  );

  let deletedOrphanCount = 0;
  if (deletionDecision.shouldDelete) {
    deletedOrphanCount = await deleteOrphanObjects(context, orphanObjects);
  }

  const summary = buildSummary(
      context,
      managerDocuments,
      references,
      objectChecks,
      orphanObjects,
      deletedOrphanCount,
      deletionDecision,
  );
  const reportPath = writeReport(summary, options.outputPath);
  summary.reportPath = reportPath;

  if (options.json) {
    console.log(JSON.stringify(summary, null, 2));
  } else {
    printSummary(summary, options.deleteOrphans);
  }

  if (options.strict && hasBlockingIssue(summary)) {
    process.exitCode = 1;
  }
}

function parseOptions(args) {
  const options = {
    help: false,
    json: false,
    strict: false,
    deleteOrphans: false,
    apply: false,
    force: false,
    maxDelete: 20,
    outputPath: "",
  };

  for (let index = 0; index < args.length; index++) {
    const argument = args[index];
    if (argument === "--help" || argument === "-h") {
      options.help = true;
    } else if (argument === "--json") {
      options.json = true;
    } else if (argument === "--strict") {
      options.strict = true;
    } else if (argument === "--delete-orphans") {
      options.deleteOrphans = true;
    } else if (argument === "--apply") {
      options.apply = true;
    } else if (argument === "--force") {
      options.force = true;
    } else if (argument === "--max-delete" && args[index + 1]) {
      options.maxDelete = Math.max(Number(args[index + 1]) || 0, 0);
      index += 1;
    } else if (argument === "--output" && args[index + 1]) {
      options.outputPath = args[index + 1];
      index += 1;
    }
  }

  return options;
}

function printHelp() {
  console.log("매니저 서류 Storage / Firestore 메타데이터 점검");
  console.log("");
  console.log("사용법");
  console.log("  node check-manager-document-storage.js");
  console.log("  node check-manager-document-storage.js --json");
  console.log("  node check-manager-document-storage.js --strict");
  console.log("  node check-manager-document-storage.js --delete-orphans");
  console.log("  node check-manager-document-storage.js --delete-orphans --apply");
  console.log("");
  console.log("- users 컬렉션의 MANAGER 문서에서 managerDocumentFiles / managerDocumentFilePaths / 레거시 경로를 읽습니다.");
  console.log("- manager-documents/ 아래 실제 Storage 객체와 비교해 누락, 경로 불일치, 고아 파일 후보를 찾습니다.");
  console.log("- --delete-orphans 는 삭제 후보만 계산하고, 실제 삭제는 --apply 를 함께 줘야 합니다.");
  console.log("- 누락 객체나 경로 불일치가 있으면 기본적으로 삭제를 막고, 정말 필요할 때만 --force 로 우회합니다.");
  console.log("- 대량 삭제 방지를 위해 기본 최대 삭제 수는 20개이며, --max-delete 로 조정할 수 있습니다.");
}

async function loadManagerDocuments(context) {
  const userDocuments = await listCollectionDocuments(context, "users");
  const managers = [];
  for (const document of userDocuments) {
    const data = fromFirestoreDocument(document);
    if (data.role !== "MANAGER") {
      continue;
    }
    managers.push({
      id: document.name.split("/").pop(),
      name: sanitizeText(data.name) || "이름 없음",
      email: sanitizeText(data.email),
      documentSummary: sanitizeText(data.managerDocumentSummary),
      documentFiles: isPlainObject(data.managerDocumentFiles) ? data.managerDocumentFiles : {},
      documentFilePaths: isPlainObject(data.managerDocumentFilePaths) ? data.managerDocumentFilePaths : {},
      legacyPaths: {
        idCard: sanitizeText(data.managerIdCardStoragePath),
        license: sanitizeText(data.managerLicenseStoragePath),
        criminalRecord: sanitizeText(data.managerCriminalRecordStoragePath),
      },
    });
  }
  managers.sort((left, right) => left.email.localeCompare(right.email, "ko-KR"));
  return managers;
}

function collectReferences(managerDocuments) {
  const references = [];
  for (const manager of managerDocuments) {
    for (const documentKey of DOCUMENT_KEYS) {
      const metadata = isPlainObject(manager.documentFiles[documentKey])
          ? manager.documentFiles[documentKey]
          : null;
      const metadataPath = sanitizeText(metadata?.fullPath);
      const metadataFileName = sanitizeText(metadata?.fileName);
      const pathMapPath = sanitizeText(manager.documentFilePaths[documentKey]);
      const legacyPath = sanitizeText(manager.legacyPaths[documentKey]);
      const distinctPaths = Array.from(new Set(
          [metadataPath, pathMapPath, legacyPath].filter(Boolean),
      ));
      if (!distinctPaths.length) {
        continue;
      }

      references.push({
        managerId: manager.id,
        managerName: manager.name,
        managerEmail: manager.email,
        documentKey,
        metadataPath,
        pathMapPath,
        legacyPath,
        fullPath: metadataPath || pathMapPath || legacyPath,
        fileName: metadataFileName,
        uploadedAt: metadata?.uploadedAt || "",
        contentType: sanitizeText(metadata?.contentType),
        pathMismatch: distinctPaths.length > 1,
      });
    }
  }
  return references;
}

async function resolveStorageChecks(context, references) {
  return Promise.all(references.map(async (reference) => {
    const storageObject = await getStorageObject(context, reference.fullPath);
    return {
      ...reference,
      objectExists: Boolean(storageObject),
      storageObject: storageObject ? {
        name: sanitizeText(storageObject.name),
        contentType: sanitizeText(storageObject.contentType),
        size: Number(storageObject.size || 0),
        updated: sanitizeText(storageObject.updated),
      } : null,
    };
  }));
}

function resolveOrphanObjects(storageObjects, references) {
  const referencedPaths = new Set(references.map((reference) => reference.fullPath));
  return storageObjects
      .filter((storageObject) => !referencedPaths.has(sanitizeText(storageObject.name)))
      .map((storageObject) => ({
        name: sanitizeText(storageObject.name),
        contentType: sanitizeText(storageObject.contentType),
        size: Number(storageObject.size || 0),
        updated: sanitizeText(storageObject.updated),
      }))
      .sort((left, right) => left.name.localeCompare(right.name, "ko-KR"));
}

async function deleteOrphanObjects(context, orphanObjects) {
  let deletedCount = 0;
  for (const orphanObject of orphanObjects) {
    const deleted = await deleteStorageObject(context, orphanObject.name);
    if (deleted) {
      deletedCount += 1;
    }
  }
  return deletedCount;
}

function resolveDeletionDecision(input, options) {
  const missingObjectCount = input.objectChecks.filter((item) => !item.objectExists).length;
  const pathMismatchCount = input.objectChecks.filter((item) => item.pathMismatch).length;
  const candidateCount = input.orphanObjects.length;
  const blockedReasons = [];

  if (!options.deleteOrphans) {
    return {
      requested: false,
      applyRequested: false,
      shouldDelete: false,
      blocked: false,
      blockedReasons,
      candidateCount,
      maxDelete: options.maxDelete,
    };
  }

  if (!candidateCount) {
    blockedReasons.push("삭제할 고아 파일이 없습니다.");
  }
  if (!options.apply) {
    blockedReasons.push("--apply 없이 실행되어 삭제를 수행하지 않았습니다.");
  }
  if (!options.force && missingObjectCount > 0) {
    blockedReasons.push("누락 객체가 있어 메타데이터와 Storage 상태를 먼저 정리해야 합니다.");
  }
  if (!options.force && pathMismatchCount > 0) {
    blockedReasons.push("경로 불일치가 있어 잘못된 파일 삭제를 막기 위해 중단했습니다.");
  }
  if (options.maxDelete > 0 && candidateCount > options.maxDelete) {
    blockedReasons.push(`삭제 후보가 ${candidateCount}건이라 최대 삭제 수 ${options.maxDelete}건을 넘습니다.`);
  }

  return {
    requested: true,
    applyRequested: options.apply,
    shouldDelete: blockedReasons.length === 0,
    blocked: blockedReasons.length > 0,
    blockedReasons,
    candidateCount,
    maxDelete: options.maxDelete,
    force: options.force,
  };
}

function buildSummary(
    context,
    managerDocuments,
    references,
    objectChecks,
    orphanObjects,
    deletedOrphanCount,
    deletionDecision,
) {
  const missingObjects = objectChecks.filter((item) => !item.objectExists);
  const pathMismatches = objectChecks.filter((item) => item.pathMismatch);
  const managersWithReferences = new Set(objectChecks.map((item) => item.managerId));
  const legacyOnlyReferences = objectChecks.filter((item) =>
    !item.metadataPath && !item.pathMapPath && Boolean(item.legacyPath),
  );

  return {
    projectId: context.projectId,
    storageBucket: context.storageBucket,
    checkedAt: new Date().toISOString(),
    managerCount: managerDocuments.length,
    managerWithDocumentReferenceCount: managersWithReferences.size,
    referencedFileCount: objectChecks.length,
    matchedFileCount: objectChecks.length - missingObjects.length,
    missingObjectCount: missingObjects.length,
    pathMismatchCount: pathMismatches.length,
    legacyOnlyReferenceCount: legacyOnlyReferences.length,
    orphanObjectCount: orphanObjects.length,
    deletedOrphanCount,
    orphanDeletion: {
      requested: Boolean(deletionDecision?.requested),
      applyRequested: Boolean(deletionDecision?.applyRequested),
      applied: Boolean(deletionDecision?.shouldDelete),
      blocked: Boolean(deletionDecision?.blocked),
      blockedReasons: deletionDecision?.blockedReasons || [],
      candidateCount: deletionDecision?.candidateCount || 0,
      maxDelete: deletionDecision?.maxDelete || 0,
      force: Boolean(deletionDecision?.force),
    },
    missingObjects,
    pathMismatches,
    legacyOnlyReferences,
    orphanObjects,
  };
}

function writeReport(summary, outputPath) {
  const reportPath = outputPath
      ? path.resolve(process.cwd(), outputPath)
      : path.resolve(
          process.cwd(),
          "reports",
          `manager-document-storage-check-${buildTimestampToken()}.json`,
      );
  fs.mkdirSync(path.dirname(reportPath), {recursive: true});
  fs.writeFileSync(reportPath, `${JSON.stringify(summary, null, 2)}\n`, "utf8");
  return reportPath;
}

function printSummary(summary, deleteOrphans) {
  console.log("매니저 서류 Storage 점검");
  console.log(`- 프로젝트: ${summary.projectId}`);
  console.log(`- 버킷: ${summary.storageBucket}`);
  console.log(`- 매니저 계정 수: ${summary.managerCount}`);
  console.log(`- 서류 참조가 있는 매니저 수: ${summary.managerWithDocumentReferenceCount}`);
  console.log(`- 참조 파일 수: ${summary.referencedFileCount}`);
  console.log(`- 실제 Storage 객체 일치 수: ${summary.matchedFileCount}`);
  console.log(`- 누락 객체 수: ${summary.missingObjectCount}`);
  console.log(`- 경로 불일치 수: ${summary.pathMismatchCount}`);
  console.log(`- 레거시 경로만 있는 참조 수: ${summary.legacyOnlyReferenceCount}`);
  console.log(`- 고아 파일 수: ${summary.orphanObjectCount}`);
  if (deleteOrphans) {
    console.log(`- 삭제한 고아 파일 수: ${summary.deletedOrphanCount}`);
    if (summary.orphanDeletion.blockedReasons.length) {
      console.log("- 삭제 차단 사유:");
      for (const reason of summary.orphanDeletion.blockedReasons) {
        console.log(`  - ${reason}`);
      }
    }
  }
  console.log(`- 리포트: ${summary.reportPath}`);

  if (summary.missingObjects.length) {
    console.log("");
    console.log("누락 객체:");
    for (const item of summary.missingObjects) {
      console.log(`- ${item.managerEmail} | ${item.documentKey} | ${item.fullPath}`);
    }
  }

  if (summary.pathMismatches.length) {
    console.log("");
    console.log("경로 불일치:");
    for (const item of summary.pathMismatches) {
      console.log(
          `- ${item.managerEmail} | ${item.documentKey} | metadata=${item.metadataPath || "-"} | map=${item.pathMapPath || "-"} | legacy=${item.legacyPath || "-"}`,
      );
    }
  }

  if (summary.orphanObjectCount) {
    console.log("");
    console.log("고아 파일 후보:");
    for (const item of summary.orphanObjects) {
      console.log(`- ${item.name}`);
    }
  }
}

function hasBlockingIssue(summary) {
  return summary.missingObjectCount > 0 || summary.pathMismatchCount > 0;
}

function fromFirestoreDocument(document) {
  return fromFirestoreMap(document?.fields || {});
}

function fromFirestoreMap(fields) {
  const result = {};
  for (const [key, value] of Object.entries(fields || {})) {
    result[key] = fromFirestoreValue(value);
  }
  return result;
}

function fromFirestoreValue(value) {
  if (!value || typeof value !== "object") {
    return null;
  }
  if ("stringValue" in value) {
    return value.stringValue;
  }
  if ("integerValue" in value) {
    return Number(value.integerValue);
  }
  if ("doubleValue" in value) {
    return Number(value.doubleValue);
  }
  if ("booleanValue" in value) {
    return Boolean(value.booleanValue);
  }
  if ("timestampValue" in value) {
    return value.timestampValue;
  }
  if ("nullValue" in value) {
    return null;
  }
  if ("mapValue" in value) {
    return fromFirestoreMap(value.mapValue?.fields || {});
  }
  if ("arrayValue" in value) {
    return (value.arrayValue?.values || []).map((entry) => fromFirestoreValue(entry));
  }
  return null;
}

function isPlainObject(value) {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function sanitizeText(value) {
  if (value === null || value === undefined) {
    return "";
  }
  return String(value).trim();
}

function buildTimestampToken() {
  const now = new Date();
  return [
    String(now.getFullYear()),
    String(now.getMonth() + 1).padStart(2, "0"),
    String(now.getDate()).padStart(2, "0"),
    "-",
    String(now.getHours()).padStart(2, "0"),
    String(now.getMinutes()).padStart(2, "0"),
    String(now.getSeconds()).padStart(2, "0"),
  ].join("");
}

main().catch((error) => {
  console.error("매니저 서류 Storage 점검 중 오류가 발생했습니다.");
  console.error(error);
  process.exitCode = 1;
});
