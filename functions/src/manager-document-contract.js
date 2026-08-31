const ACTIVE_MANAGER_DOCUMENT_KEYS = Object.freeze([
  "license",
  "nursingLicense",
]);

const MIGRATION_LEGACY_MANAGER_DOCUMENT_KEYS = Object.freeze([
  "healthCertificate",
]);

const RETENTION_ONLY_MANAGER_DOCUMENT_KEYS = Object.freeze([
  "idCard",
  "criminalRecord",
]);

const REPLACEMENT_CLEANUP_MANAGER_DOCUMENT_KEYS = Object.freeze([
  ...ACTIVE_MANAGER_DOCUMENT_KEYS,
  ...MIGRATION_LEGACY_MANAGER_DOCUMENT_KEYS,
]);

const RETENTION_MANAGER_DOCUMENT_KEYS = Object.freeze([
  ...REPLACEMENT_CLEANUP_MANAGER_DOCUMENT_KEYS,
  ...RETENTION_ONLY_MANAGER_DOCUMENT_KEYS,
]);

// 일부 기존 문서는 중첩 경로와 함께 최상위 호환 경로도 보존한다.
const MANAGER_DOCUMENT_PATH_ALIAS_FIELDS = Object.freeze({
  license: "managerLicenseStoragePath",
  nursingLicense: null,
  healthCertificate: "managerHealthCertificateStoragePath",
  idCard: "managerIdCardStoragePath",
  criminalRecord: "managerCriminalRecordStoragePath",
});

const OPTIONAL_MANAGER_DOCUMENT_PATH_ALIAS_KEYS = new Set([
  "healthCertificate",
]);

const MANAGER_DOCUMENT_LEGAL_HOLD_FIELDS = Object.freeze([
  "managerDocumentLegalHoldUntil",
  "managerDocumentLegalHoldReason",
  "managerDocumentLegalHoldByAdminUserId",
]);

function managerDocumentLegalHoldBlocksDeletion(data, asOf = new Date()) {
  if (!isPlainObject(data)) {
    return false;
  }
  const hasLegalHoldField = MANAGER_DOCUMENT_LEGAL_HOLD_FIELDS.some(
      (field) => Object.hasOwn(data, field),
  );
  if (!hasLegalHoldField) {
    return false;
  }
  if (!MANAGER_DOCUMENT_LEGAL_HOLD_FIELDS.every(
      (field) => Object.hasOwn(data, field),
  ) ||
      !isExactNonEmptyString(data.managerDocumentLegalHoldReason) ||
      !isExactNonEmptyString(data.managerDocumentLegalHoldByAdminUserId)) {
    return true;
  }

  const legalHoldUntilMillis = managerDocumentTimestampMillis(
      data.managerDocumentLegalHoldUntil,
  );
  const asOfMillis = managerDocumentTimestampMillis(asOf);
  if (legalHoldUntilMillis === null || asOfMillis === null) {
    return true;
  }
  return legalHoldUntilMillis > asOfMillis;
}

function resolveManagerDocumentReference(data, managerId, documentKey) {
  if (!RETENTION_MANAGER_DOCUMENT_KEYS.includes(documentKey) ||
      !isPlainObject(data)) {
    return null;
  }
  const fileMap = isPlainObject(data.managerDocumentFiles)
    ? data.managerDocumentFiles
    : {};
  const pathMap = isPlainObject(data.managerDocumentFilePaths)
    ? data.managerDocumentFilePaths
    : {};
  const metadata = isPlainObject(fileMap[documentKey]) ? fileMap[documentKey] : {};
  const requiredPaths = [metadata.fullPath, pathMap[documentKey]];
  const aliasField = MANAGER_DOCUMENT_PATH_ALIAS_FIELDS[documentKey];
  if (aliasField) {
    if (Object.hasOwn(data, aliasField)) {
      requiredPaths.push(data[aliasField]);
    } else if (!OPTIONAL_MANAGER_DOCUMENT_PATH_ALIAS_KEYS.has(documentKey)) {
      return null;
    }
  }

  if (requiredPaths.some((storagePath) => !isExactNonEmptyString(storagePath)) ||
      new Set(requiredPaths).size !== 1 ||
      !isManagerDocumentStoragePath(requiredPaths[0], managerId, documentKey)) {
    return null;
  }
  return {
    documentKey,
    storagePath: requiredPaths[0],
    uploadedAt: metadata.uploadedAt,
  };
}

function resolveCanonicalManagerDocumentReference(data, managerId) {
  if (!isPlainObject(data)) {
    return null;
  }
  const fileMap = isPlainObject(data.managerDocumentFiles)
    ? data.managerDocumentFiles
    : {};
  const pathMap = isPlainObject(data.managerDocumentFilePaths)
    ? data.managerDocumentFilePaths
    : {};
  const presentKeys = ACTIVE_MANAGER_DOCUMENT_KEYS.filter((documentKey) => {
    const aliasField = MANAGER_DOCUMENT_PATH_ALIAS_FIELDS[documentKey];
    return Object.hasOwn(fileMap, documentKey) ||
      Object.hasOwn(pathMap, documentKey) ||
      Boolean(aliasField && Object.hasOwn(data, aliasField));
  });
  if (presentKeys.length !== 1) {
    return null;
  }
  return resolveManagerDocumentReference(data, managerId, presentKeys[0]);
}

function collectManagerDocumentReferencePaths(
    data,
    documentKeys = RETENTION_MANAGER_DOCUMENT_KEYS,
) {
  const paths = new Set();
  if (!isPlainObject(data)) {
    return paths;
  }
  const fileMap = isPlainObject(data.managerDocumentFiles)
    ? data.managerDocumentFiles
    : {};
  const pathMap = isPlainObject(data.managerDocumentFilePaths)
    ? data.managerDocumentFilePaths
    : {};
  for (const documentKey of documentKeys) {
    const metadata = isPlainObject(fileMap[documentKey]) ? fileMap[documentKey] : {};
    const aliasField = MANAGER_DOCUMENT_PATH_ALIAS_FIELDS[documentKey];
    const values = [metadata.fullPath, pathMap[documentKey]];
    if (aliasField && Object.hasOwn(data, aliasField)) {
      values.push(data[aliasField]);
    }
    values.filter(isExactNonEmptyString).forEach((storagePath) => paths.add(storagePath));
  }
  return paths;
}

function isManagerDocumentStoragePath(
    value,
    managerId,
    documentKey,
    allowedDocumentKeys = RETENTION_MANAGER_DOCUMENT_KEYS,
) {
  if (!isExactNonEmptyString(value) ||
      !isExactNonEmptyString(managerId) ||
      !isExactNonEmptyString(documentKey) ||
      !allowedDocumentKeys.includes(documentKey)) {
    return false;
  }
  const segments = value.split("/");
  return segments.length === 4 &&
    segments[0] === "manager-documents" &&
    segments[1] === managerId &&
    segments[2] === documentKey &&
    isExactNonEmptyString(segments[3]);
}

function managerDocumentTimestampMillis(value) {
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

function isExactNonEmptyString(value) {
  return typeof value === "string" && value.length > 0 && value.trim() === value;
}

function isPlainObject(value) {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

module.exports = {
  ACTIVE_MANAGER_DOCUMENT_KEYS,
  MANAGER_DOCUMENT_LEGAL_HOLD_FIELDS,
  REPLACEMENT_CLEANUP_MANAGER_DOCUMENT_KEYS,
  MANAGER_DOCUMENT_PATH_ALIAS_FIELDS,
  MIGRATION_LEGACY_MANAGER_DOCUMENT_KEYS,
  RETENTION_MANAGER_DOCUMENT_KEYS,
  RETENTION_ONLY_MANAGER_DOCUMENT_KEYS,
  collectManagerDocumentReferencePaths,
  isManagerDocumentStoragePath,
  managerDocumentLegalHoldBlocksDeletion,
  resolveCanonicalManagerDocumentReference,
  resolveManagerDocumentReference,
};
