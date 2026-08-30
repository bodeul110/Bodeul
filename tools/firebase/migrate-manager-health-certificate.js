#!/usr/bin/env node

const {
  copyStorageObject,
  createCliContext,
  deleteStorageObject,
  getDocument,
  getStorageObject,
  listCollectionDocuments,
  runDocumentTransaction,
} = require("./lib/firebase-toolkit");

const SOURCE_KEY = "healthCertificate";
const DESTINATION_KEY = "nursingLicense";
const LEGACY_ALIAS = "managerHealthCertificateStoragePath";
const MAX_DOCUMENT_SIZE = 10 * 1024 * 1024;
const ALLOWED_CONTENT_TYPES = new Set([
  "image/jpeg",
  "image/png",
  "image/webp",
]);

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }

  const context = await createCliContext();
  const managers = await loadManagers(context, options.uid);
  if (options.uid && managers.length === 0) {
    throw new Error(`지정한 MANAGER 문서를 찾지 못했습니다: ${options.uid}`);
  }
  const entries = [];
  for (const manager of managers) {
    const objectPaths = resolveMigrationObjectPaths(manager.id, manager.data);
    const [sourceObject, destinationObject] = await Promise.all([
      objectPaths.sourcePath
        ? getStorageObject(context, objectPaths.sourcePath)
        : Promise.resolve(null),
      objectPaths.destinationPath
        ? getStorageObject(context, objectPaths.destinationPath)
        : Promise.resolve(null),
    ]);
    const plan = buildMigrationPlan({
      managerId: manager.id,
      data: manager.data,
      sourceObject,
      destinationObject,
    });
    entries.push({manager, plan});
  }

  const applyBlocked = shouldBlockApply(
      entries.map((entry) => entry.plan),
      options.apply,
  );
  const results = [];
  for (const {manager, plan} of entries) {

    if (!options.apply || applyBlocked ||
        plan.action === "NOOP" || plan.action === "BLOCKED") {
      results.push(summarizePlan(manager, plan, false));
      continue;
    }

    await applyMigrationPlan(plan, {
      copySource: () => copyStorageObject(
          context,
          plan.sourcePath,
          plan.destinationPath,
          plan.sourceObject.generation,
      ),
      getDestination: () => getStorageObject(context, plan.destinationPath),
      updateMetadata: async (mutationBuilder) => {
        let currentDecision = null;
        await runDocumentTransaction(
            context,
            `users/${manager.id}`,
            (document) => {
              if (!document) {
                throw new Error("Firestore 매니저 문서가 transaction 중 사라졌습니다.");
              }
              currentDecision = mutationBuilder(fromFirestoreDocument(document));
              return currentDecision?.mutation || null;
            },
        );
        return currentDecision;
      },
      getLatestData: async () => {
        const document = await getDocument(context, `users/${manager.id}`);
        return document ? fromFirestoreDocument(document) : null;
      },
      deleteSource: () => deleteStorageObject(
          context,
          plan.sourcePath,
          plan.sourceObject.generation,
      ),
    });
    results.push(summarizePlan(manager, plan, true));
  }

  const summary = {
    projectId: context.projectId,
    storageBucket: context.storageBucket,
    mode: options.apply ? "apply" : "dry-run",
    applyBlocked,
    managerCount: managers.length,
    blockedCount: results.filter((result) => result.action === "BLOCKED").length,
    results,
  };
  if (options.json) {
    console.log(JSON.stringify(summary, null, 2));
  } else {
    printSummary(summary);
  }
  if (summary.blockedCount > 0) {
    process.exitCode = 1;
  }
}

function parseOptions(args) {
  const options = {apply: false, help: false, json: false, uid: ""};
  for (let index = 0; index < args.length; index++) {
    const argument = args[index];
    if (argument === "--apply") {
      options.apply = true;
    } else if (argument === "--help" || argument === "-h") {
      options.help = true;
    } else if (argument === "--json") {
      options.json = true;
    } else if (argument === "--uid" && args[index + 1]) {
      options.uid = sanitizeText(args[index + 1]);
      index += 1;
    } else {
      throw new Error(`지원하지 않는 인자입니다: ${argument}`);
    }
  }
  return options;
}

function printHelp() {
  console.log("매니저 건강진단서 레거시 자격 증빙 이관");
  console.log("");
  console.log("사용법");
  console.log("  node migrate-manager-health-certificate.js");
  console.log("  node migrate-manager-health-certificate.js --uid <uid>");
  console.log("  node migrate-manager-health-certificate.js --apply --uid <uid>");
  console.log("");
  console.log("- 기본값은 dry-run이며 --apply를 명시해야만 변경합니다.");
  console.log("- Storage 복사, Firestore transaction, 구 객체 generation 조건부 삭제 순서로 실행합니다.");
}

async function loadManagers(context, requestedUid) {
  const documents = await listCollectionDocuments(context, "users");
  return documents
      .map((document) => ({
        id: document.name.split("/").pop(),
        data: fromFirestoreDocument(document),
      }))
      .filter((manager) => manager.data.role === "MANAGER")
      .filter((manager) => !requestedUid || manager.id === requestedUid);
}

function resolveMigrationObjectPaths(managerId, data) {
  const files = isPlainObject(data?.managerDocumentFiles)
      ? data.managerDocumentFiles
      : {};
  const paths = isPlainObject(data?.managerDocumentFilePaths)
      ? data.managerDocumentFilePaths
      : {};
  const sourcePath = sanitizeText(
      files[SOURCE_KEY]?.fullPath || paths[SOURCE_KEY] || data?.[LEGACY_ALIAS],
  );
  const canonicalPath = sanitizeText(
      files[DESTINATION_KEY]?.fullPath || paths[DESTINATION_KEY],
  );
  const pathForFileName = sourcePath || canonicalPath;
  const fileName = pathForFileName.split("/").pop() || "";
  return {
    sourcePath: sourcePath || (
      canonicalPath && fileName
        ? `manager-documents/${managerId}/${SOURCE_KEY}/${fileName}`
        : ""
    ),
    destinationPath: canonicalPath || (
      sourcePath && fileName
        ? `manager-documents/${managerId}/${DESTINATION_KEY}/${fileName}`
        : ""
    ),
  };
}

function buildMigrationPlan({managerId, data, sourceObject, destinationObject}) {
  const files = isPlainObject(data?.managerDocumentFiles)
      ? data.managerDocumentFiles
      : {};
  const paths = isPlainObject(data?.managerDocumentFilePaths)
      ? data.managerDocumentFilePaths
      : {};
  const hasHealthFile = hasOwn(files, SOURCE_KEY);
  const hasHealthPath = hasOwn(paths, SOURCE_KEY);
  const hasHealthAlias = hasOwn(data || {}, LEGACY_ALIAS);
  const hasLegacy = hasHealthFile || hasHealthPath || hasHealthAlias;
  const hasLicense = hasOwn(files, "license") ||
    hasOwn(paths, "license") ||
    hasOwn(data || {}, "managerLicenseStoragePath");
  const hasNursing = hasOwn(files, DESTINATION_KEY) ||
    hasOwn(paths, DESTINATION_KEY);
  const objectPaths = resolveMigrationObjectPaths(managerId, data);

  if (!isExactText(managerId)) {
    return blockedPlan(managerId, "매니저 UID가 비어 있거나 공백을 포함합니다.");
  }
  if (data?.role !== "MANAGER") {
    return blockedPlan(managerId, "현재 사용자 역할이 MANAGER가 아닙니다.");
  }
  const legalHoldIssue = validateManagerDocumentLegalHold(data);
  if (legalHoldIssue) {
    return blockedPlan(managerId, `legal hold 검증 실패: ${legalHoldIssue}`);
  }
  if (hasLicense && hasNursing) {
    return blockedPlan(managerId, "canonical 자격 증빙이 둘 이상입니다.");
  }

  if (hasLegacy) {
    if (hasLicense || hasNursing) {
      return blockedPlan(managerId, "레거시와 canonical 메타데이터가 동시에 존재합니다.");
    }
    if (!hasHealthFile || !hasHealthPath || !isPlainObject(files[SOURCE_KEY])) {
      return blockedPlan(managerId, "healthCertificate 메타데이터 또는 path map이 부분 상태입니다.");
    }
    const metadataPath = files[SOURCE_KEY].fullPath;
    const pathMap = paths[SOURCE_KEY];
    const alias = data[LEGACY_ALIAS];
    if (!isExpectedPath(metadataPath, managerId, SOURCE_KEY) ||
        metadataPath !== pathMap ||
        (hasHealthAlias && metadataPath !== alias)) {
      return blockedPlan(managerId, "healthCertificate 경로 귀속 또는 alias가 일치하지 않습니다.");
    }
    if (!objectPaths.destinationPath ||
        !isExpectedPath(objectPaths.destinationPath, managerId, DESTINATION_KEY)) {
      return blockedPlan(managerId, "nursingLicense 대상 경로를 안전하게 계산할 수 없습니다.");
    }
    const sourceIssue = validateStorageObject(sourceObject, metadataPath);
    if (sourceIssue) {
      return blockedPlan(managerId, `원본 Storage 검증 실패: ${sourceIssue}`);
    }
    const base = {
      action: destinationObject ? "UPDATE_METADATA" : "COPY_AND_UPDATE",
      managerId,
      sourcePath: metadataPath,
      destinationPath: objectPaths.destinationPath,
      sourceObject,
      destinationObject,
      data,
      reason: destinationObject
        ? "검증된 이전 복사본을 이어서 Firestore 메타데이터를 교체합니다."
        : "원본을 canonical 경로로 복사한 뒤 Firestore 메타데이터를 교체합니다.",
    };
    if (destinationObject) {
      const matchIssue = compareStorageObjects(
          sourceObject,
          destinationObject,
          objectPaths.destinationPath,
      );
      if (matchIssue) {
        return blockedPlan(managerId, `기존 대상 객체 충돌: ${matchIssue}`);
      }
    }
    return base;
  }

  if (hasLicense) {
    const licenseMetadata = files.license;
    const licensePath = paths.license;
    const licenseAlias = data.managerLicenseStoragePath;
    if (!isPlainObject(licenseMetadata) ||
        !isExpectedPath(licenseMetadata.fullPath, managerId, "license") ||
        licenseMetadata.fullPath !== licensePath ||
        licenseMetadata.fullPath !== licenseAlias) {
      return blockedPlan(managerId, "license canonical 메타데이터가 부분 상태입니다.");
    }
    return {
      action: "NOOP",
      managerId,
      reason: "license canonical 증빙은 healthCertificate 이관 대상이 아닙니다.",
    };
  }
  if (!hasNursing) {
    return {
      action: "NOOP",
      managerId,
      reason: "healthCertificate 레거시 참조가 없습니다.",
    };
  }

  const nursingMetadata = files[DESTINATION_KEY];
  const nursingPath = paths[DESTINATION_KEY];
  if (!isPlainObject(nursingMetadata) ||
      !isExpectedPath(nursingMetadata.fullPath, managerId, DESTINATION_KEY) ||
      nursingMetadata.fullPath !== nursingPath) {
    return blockedPlan(managerId, "nursingLicense canonical 메타데이터가 부분 상태입니다.");
  }
  const destinationIssue = validateStorageObject(
      destinationObject,
      nursingMetadata.fullPath,
  );
  if (destinationIssue) {
    return blockedPlan(managerId, `canonical Storage 검증 실패: ${destinationIssue}`);
  }
  if (!sourceObject) {
    return {
      action: "NOOP",
      managerId,
      destinationPath: nursingMetadata.fullPath,
      destinationObject,
      reason: "canonical 이관과 구 객체 삭제가 이미 완료됐습니다.",
    };
  }
  const sourceIssue = validateStorageObject(sourceObject, objectPaths.sourcePath);
  const matchIssue = sourceIssue || compareStorageObjects(
      sourceObject,
      destinationObject,
      nursingMetadata.fullPath,
  );
  if (matchIssue) {
    return blockedPlan(managerId, `삭제 대기 원본 검증 실패: ${matchIssue}`);
  }
  return {
    action: "CLEANUP_SOURCE",
    managerId,
    sourcePath: objectPaths.sourcePath,
    destinationPath: nursingMetadata.fullPath,
    sourceObject,
    destinationObject,
    data,
    reason: "Firestore 교체는 완료됐고 generation이 일치하는 구 객체만 삭제합니다.",
  };
}

async function applyMigrationPlan(plan, dependencies) {
  if (plan.action === "BLOCKED") {
    throw new Error(plan.reason);
  }
  if (plan.action === "NOOP") {
    return {action: "NOOP"};
  }

  let destinationObject = plan.destinationObject;
  if (plan.action === "COPY_AND_UPDATE") {
    await dependencies.copySource();
    destinationObject = await dependencies.getDestination();
    const matchIssue = compareStorageObjects(
        plan.sourceObject,
        destinationObject,
        plan.destinationPath,
    );
    if (matchIssue) {
      throw new Error(`복사 결과 검증 실패: ${matchIssue}`);
    }
  }

  if (plan.action === "COPY_AND_UPDATE" || plan.action === "UPDATE_METADATA") {
    await dependencies.updateMetadata((currentData) => {
      const currentPlan = buildMigrationPlan({
        managerId: plan.managerId,
        data: currentData,
        sourceObject: plan.sourceObject,
        destinationObject,
      });
      if (currentPlan.action === "NOOP" || currentPlan.action === "CLEANUP_SOURCE") {
        return {state: currentPlan.action, mutation: null};
      }
      if (currentPlan.action !== "UPDATE_METADATA") {
        throw new Error(`transaction 재검증 차단: ${currentPlan.reason}`);
      }
      return {
        state: "UPDATE_METADATA",
        mutation: buildFirestoreMutation(currentData, currentPlan.destinationPath),
      };
    });
  }

  const latestDestination = await dependencies.getDestination();
  const latestDestinationIssue = compareStorageObjects(
      plan.sourceObject,
      latestDestination,
      plan.destinationPath,
  );
  if (latestDestinationIssue) {
    throw new Error(`삭제 직전 대상 객체 검증 실패: ${latestDestinationIssue}`);
  }

  const latestData = await dependencies.getLatestData();
  const deletionIssue = validateSourceDeletionState({
    managerId: plan.managerId,
    data: latestData,
    sourceObject: plan.sourceObject,
    destinationObject: latestDestination,
    sourcePath: plan.sourcePath,
    destinationPath: plan.destinationPath,
  });
  if (deletionIssue) {
    throw new Error(`원본 삭제 직전 재검증 차단: ${deletionIssue}`);
  }

  await dependencies.deleteSource();
  return {action: plan.action};
}

function validateSourceDeletionState({
  managerId,
  data,
  sourceObject,
  destinationObject,
  sourcePath,
  destinationPath,
}) {
  if (!isPlainObject(data)) {
    return "Firestore 매니저 문서가 없습니다.";
  }
  const currentPlan = buildMigrationPlan({
    managerId,
    data,
    sourceObject,
    destinationObject,
  });
  if (currentPlan.action !== "CLEANUP_SOURCE") {
    return currentPlan.reason ||
      `현재 상태가 원본 삭제 가능 상태가 아닙니다: ${currentPlan.action}`;
  }
  if (currentPlan.sourcePath !== sourcePath ||
      currentPlan.destinationPath !== destinationPath) {
    return "현재 이관 경로가 처음 검증한 경로와 다릅니다.";
  }
  return "";
}

function buildFirestoreMutation(data, destinationPath) {
  const files = {...data.managerDocumentFiles};
  const paths = {...data.managerDocumentFilePaths};
  const legacyMetadata = {...files[SOURCE_KEY]};
  delete files[SOURCE_KEY];
  delete paths[SOURCE_KEY];
  files[DESTINATION_KEY] = {
    ...legacyMetadata,
    fullPath: destinationPath,
  };
  paths[DESTINATION_KEY] = destinationPath;
  return {
    data: {
      managerDocumentFiles: files,
      managerDocumentFilePaths: paths,
      managerDocumentEvidenceMigration: {
        migrationId: "health-certificate-to-nursing-license-v1",
        sourceKey: SOURCE_KEY,
        destinationKey: DESTINATION_KEY,
        sourcePath: legacyMetadata.fullPath,
        destinationPath,
      },
    },
    deleteFields: [LEGACY_ALIAS],
  };
}

function validateStorageObject(storageObject, expectedPath) {
  if (!storageObject) {
    return "객체가 없습니다.";
  }
  if (sanitizeText(storageObject.name) !== expectedPath) {
    return "객체 이름이 예상 경로와 다릅니다.";
  }
  if (!sanitizeText(storageObject.generation)) {
    return "generation이 없습니다.";
  }
  if (!ALLOWED_CONTENT_TYPES.has(sanitizeText(storageObject.contentType))) {
    return "허용되지 않은 content type입니다.";
  }
  const size = Number(storageObject.size);
  if (!Number.isSafeInteger(size) || size <= 0 || size > MAX_DOCUMENT_SIZE) {
    return "크기가 1 byte 이상 10 MiB 이하여야 합니다.";
  }
  if (!sanitizeText(storageObject.md5Hash) && !sanitizeText(storageObject.crc32c)) {
    return "무결성 checksum이 없습니다.";
  }
  return "";
}

function compareStorageObjects(sourceObject, destinationObject, destinationPath) {
  const destinationIssue = validateStorageObject(destinationObject, destinationPath);
  if (destinationIssue) {
    return destinationIssue;
  }
  if (sanitizeText(sourceObject.contentType) !==
      sanitizeText(destinationObject.contentType)) {
    return "content type이 원본과 다릅니다.";
  }
  if (Number(sourceObject.size) !== Number(destinationObject.size)) {
    return "크기가 원본과 다릅니다.";
  }
  const sourceMd5 = sanitizeText(sourceObject.md5Hash);
  const destinationMd5 = sanitizeText(destinationObject.md5Hash);
  const sourceCrc = sanitizeText(sourceObject.crc32c);
  const destinationCrc = sanitizeText(destinationObject.crc32c);
  if (sourceMd5 && destinationMd5 && sourceMd5 !== destinationMd5) {
    return "MD5 checksum이 원본과 다릅니다.";
  }
  if (sourceCrc && destinationCrc && sourceCrc !== destinationCrc) {
    return "CRC32C checksum이 원본과 다릅니다.";
  }
  if (!(sourceMd5 && destinationMd5) && !(sourceCrc && destinationCrc)) {
    return "원본과 대상을 비교할 공통 checksum이 없습니다.";
  }
  return "";
}

function validateManagerDocumentLegalHold(data, asOf = new Date()) {
  const fields = [
    "managerDocumentLegalHoldUntil",
    "managerDocumentLegalHoldReason",
    "managerDocumentLegalHoldByAdminUserId",
  ];
  const present = fields.filter((field) => hasOwn(data, field));
  if (present.length === 0) {
    return "";
  }
  if (present.length !== fields.length) {
    return "보존 필드가 불완전합니다.";
  }
  if (!isExactText(data.managerDocumentLegalHoldReason) ||
      !isExactText(data.managerDocumentLegalHoldByAdminUserId)) {
    return "보존 사유 또는 설정 관리자 ID가 비어 있거나 앞뒤 공백을 포함합니다.";
  }
  if (!isFirestoreTimestampLike(data.managerDocumentLegalHoldUntil)) {
    return "보존 만료 시각 형식이 Firestore timestamp가 아닙니다.";
  }
  const holdUntilMillis = timestampMillis(data.managerDocumentLegalHoldUntil);
  const asOfMillis = timestampMillis(asOf);
  if (holdUntilMillis === null || asOfMillis === null) {
    return "보존 만료 시각을 해석할 수 없습니다.";
  }
  if (holdUntilMillis > asOfMillis) {
    return "활성 legal hold가 있습니다.";
  }
  return "";
}

function isFirestoreTimestampLike(value) {
  return value instanceof Date ||
    (typeof value === "string" && /^\d{4}-\d{2}-\d{2}T/.test(value)) ||
    Boolean(value && typeof value.toMillis === "function");
}

function timestampMillis(value) {
  if (value instanceof Date) {
    const millis = value.getTime();
    return Number.isFinite(millis) ? millis : null;
  }
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : null;
  }
  if (typeof value === "string") {
    const millis = Date.parse(value);
    return Number.isFinite(millis) ? millis : null;
  }
  if (value && typeof value.toMillis === "function") {
    try {
      const millis = value.toMillis();
      return typeof millis === "number" && Number.isFinite(millis) ? millis : null;
    } catch (_error) {
      return null;
    }
  }
  return null;
}

function isExpectedPath(value, managerId, documentKey) {
  if (!isExactText(value) || !isExactText(managerId)) {
    return false;
  }
  const segments = value.split("/");
  return segments.length === 4 &&
    segments[0] === "manager-documents" &&
    segments[1] === managerId &&
    segments[2] === documentKey &&
    Boolean(segments[3]);
}

function blockedPlan(managerId, reason) {
  return {action: "BLOCKED", managerId, reason};
}

function shouldBlockApply(plans, applyRequested) {
  return Boolean(applyRequested) &&
    plans.some((plan) => plan.action === "BLOCKED");
}

function summarizePlan(manager, plan, applied) {
  return {
    managerId: manager.id,
    action: plan.action,
    reason: plan.reason,
    sourcePath: plan.sourcePath || "",
    destinationPath: plan.destinationPath || "",
    applied,
  };
}

function printSummary(summary) {
  console.log("매니저 healthCertificate 이관 점검");
  console.log(`- 프로젝트: ${summary.projectId}`);
  console.log(`- 버킷: ${summary.storageBucket}`);
  console.log(`- 모드: ${summary.mode}`);
  if (summary.applyBlocked) {
    console.log("- 차단된 매니저가 있어 이번 apply에서는 어떤 변경도 수행하지 않았습니다.");
  }
  console.log(`- 매니저 수: ${summary.managerCount}`);
  console.log(`- 차단 수: ${summary.blockedCount}`);
  for (const result of summary.results) {
    console.log(`- ${result.managerId}: ${result.action} | ${result.reason}`);
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
  if (!value || typeof value !== "object") return null;
  if ("stringValue" in value) return value.stringValue;
  if ("integerValue" in value) return Number(value.integerValue);
  if ("doubleValue" in value) return Number(value.doubleValue);
  if ("booleanValue" in value) return Boolean(value.booleanValue);
  if ("timestampValue" in value) return value.timestampValue;
  if ("nullValue" in value) return null;
  if ("mapValue" in value) return fromFirestoreMap(value.mapValue?.fields || {});
  if ("arrayValue" in value) {
    return (value.arrayValue?.values || []).map(fromFirestoreValue);
  }
  return null;
}

function isPlainObject(value) {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function hasOwn(value, key) {
  return Object.prototype.hasOwnProperty.call(value || {}, key);
}

function isExactText(value) {
  return typeof value === "string" && value.length > 0 && value.trim() === value;
}

function sanitizeText(value) {
  return value === null || value === undefined ? "" : String(value).trim();
}

if (require.main === module) {
  main().catch((error) => {
    console.error("매니저 healthCertificate 이관 중 오류가 발생했습니다.");
    console.error(error);
    process.exitCode = 1;
  });
}

module.exports = {
  applyMigrationPlan,
  buildFirestoreMutation,
  buildMigrationPlan,
  compareStorageObjects,
  parseOptions,
  resolveMigrationObjectPaths,
  shouldBlockApply,
  validateManagerDocumentLegalHold,
  validateSourceDeletionState,
  validateStorageObject,
};
