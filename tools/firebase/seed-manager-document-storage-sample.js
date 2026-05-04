#!/usr/bin/env node

const {
  createCliContext,
  listCollectionDocuments,
  patchDocumentData,
  uploadStorageObject,
} = require("./lib/firebase-toolkit");

const DOCUMENTS = [
  {key: "idCard", fileName: "sample-id-card.png", label: "신분증"},
  {key: "license", fileName: "sample-license.png", label: "자격증"},
  {key: "criminalRecord", fileName: "sample-criminal-record.png", label: "범죄경력 조회서"},
];

const SAMPLE_PNG_BASE64 =
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9WlH0wAAAABJRU5ErkJggg==";

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }

  const context = await createCliContext();
  const manager = await findManagerByEmail(context, options.email);
  if (!manager) {
    throw new Error(`매니저 계정을 찾지 못했습니다: ${options.email}`);
  }

  const summaryText = manager.documentSummary || "신분증, 자격증, 범죄경력 조회서 샘플 업로드";
  const now = Date.now();
  const uploads = [];
  for (const document of DOCUMENTS) {
    const objectPath = `manager-documents/${manager.id}/${document.key}/${now}-${document.fileName}`;
    uploads.push({
      documentKey: document.key,
      label: document.label,
      fileName: document.fileName,
      fullPath: objectPath,
      contentType: "image/png",
      uploadedAt: now,
    });
  }

  if (options.dryRun) {
    printDryRun(context, manager, summaryText, uploads);
    return;
  }

  const buffer = Buffer.from(SAMPLE_PNG_BASE64, "base64");
  for (const upload of uploads) {
    await uploadStorageObject(context, upload.fullPath, upload.contentType, buffer);
  }

  const updatedHistory = [
    {
      eventType: "SUBMITTED",
      happenedAt: now,
      actorName: manager.name,
      summary: summaryText,
      reviewNote: "",
    },
    ...manager.documentHistory,
  ];

  const patch = {
    managerDocumentSummary: summaryText,
    managerDocumentStatus: "PENDING_REVIEW",
    managerDocumentReviewNote: "",
    managerDocumentReviewedAt: null,
    managerDocumentReviewedByName: "",
    managerDocumentUpdatedAt: now,
    managerDocumentHistory: updatedHistory,
    managerDocumentFiles: {},
    managerDocumentFilePaths: {},
    managerIdCardStoragePath: uploads[0].fullPath,
    managerLicenseStoragePath: uploads[1].fullPath,
    managerCriminalRecordStoragePath: uploads[2].fullPath,
  };

  for (const upload of uploads) {
    patch.managerDocumentFiles[upload.documentKey] = {
      fullPath: upload.fullPath,
      fileName: upload.fileName,
      contentType: upload.contentType,
      uploadedAt: upload.uploadedAt,
    };
    patch.managerDocumentFilePaths[upload.documentKey] = upload.fullPath;
  }

  await patchDocumentData(context, `users/${manager.id}`, patch);

  console.log("매니저 서류 샘플 업로드를 반영했습니다.");
  console.log(`- 프로젝트: ${context.projectId}`);
  console.log(`- 버킷: ${context.storageBucket}`);
  console.log(`- 매니저: ${manager.email} (${manager.id})`);
  for (const upload of uploads) {
    console.log(`- ${upload.label}: ${upload.fullPath}`);
  }
}

function parseOptions(args) {
  const options = {
    help: false,
    dryRun: true,
    email: "manager@bodeul.app",
  };

  for (let index = 0; index < args.length; index++) {
    const argument = args[index];
    if (argument === "--help" || argument === "-h") {
      options.help = true;
    } else if (argument === "--apply") {
      options.dryRun = false;
    } else if (argument === "--email" && args[index + 1]) {
      options.email = args[index + 1];
      index += 1;
    }
  }

  return options;
}

function printHelp() {
  console.log("매니저 서류 Storage 샘플 업로드");
  console.log("");
  console.log("사용법");
  console.log("  node seed-manager-document-storage-sample.js");
  console.log("  node seed-manager-document-storage-sample.js --apply");
  console.log("  node seed-manager-document-storage-sample.js --apply --email manager@bodeul.app");
  console.log("");
  console.log("- 기본값은 dry-run 입니다.");
  console.log("- --apply 를 주면 manager-documents/{uid}/{documentKey}/ 아래 샘플 PNG 3개를 업로드하고 users 문서 메타데이터를 함께 갱신합니다.");
}

async function findManagerByEmail(context, email) {
  const userDocuments = await listCollectionDocuments(context, "users");
  for (const document of userDocuments) {
    const data = fromFirestoreDocument(document);
    if (data.role !== "MANAGER") {
      continue;
    }
    if (sanitizeText(data.email) !== sanitizeText(email)) {
      continue;
    }
    return {
      id: document.name.split("/").pop(),
      name: sanitizeText(data.name) || "매니저",
      email: sanitizeText(data.email),
      documentSummary: sanitizeText(data.managerDocumentSummary),
      documentHistory: Array.isArray(data.managerDocumentHistory)
          ? data.managerDocumentHistory
          : [],
    };
  }
  return null;
}

function printDryRun(context, manager, summaryText, uploads) {
  console.log("매니저 서류 샘플 업로드 dry-run");
  console.log(`- 프로젝트: ${context.projectId}`);
  console.log(`- 버킷: ${context.storageBucket}`);
  console.log(`- 매니저: ${manager.email} (${manager.id})`);
  console.log(`- 서류 요약: ${summaryText}`);
  for (const upload of uploads) {
    console.log(`- ${upload.label}: ${upload.fullPath}`);
  }
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

function sanitizeText(value) {
  if (value === null || value === undefined) {
    return "";
  }
  return String(value).trim();
}

main().catch((error) => {
  console.error("매니저 서류 샘플 업로드 중 오류가 발생했습니다.");
  console.error(error);
  process.exitCode = 1;
});
