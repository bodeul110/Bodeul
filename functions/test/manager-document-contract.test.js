const assert = require("node:assert/strict");
const test = require("node:test");

const {
  ACTIVE_MANAGER_DOCUMENT_KEYS,
  MANAGER_DOCUMENT_PATH_ALIAS_FIELDS,
  MIGRATION_LEGACY_MANAGER_DOCUMENT_KEYS,
  RETENTION_MANAGER_DOCUMENT_KEYS,
  RETENTION_ONLY_MANAGER_DOCUMENT_KEYS,
  managerDocumentLegalHoldBlocksDeletion,
  resolveCanonicalManagerDocumentReference,
} = require("../src/manager-document-contract");

test("매니저 증빙 활성 키와 기존 파기 키를 분리한다", () => {
  assert.deepEqual(ACTIVE_MANAGER_DOCUMENT_KEYS, ["license", "nursingLicense"]);
  assert.deepEqual(MIGRATION_LEGACY_MANAGER_DOCUMENT_KEYS, ["healthCertificate"]);
  assert.deepEqual(RETENTION_ONLY_MANAGER_DOCUMENT_KEYS, ["idCard", "criminalRecord"]);
  assert.deepEqual(RETENTION_MANAGER_DOCUMENT_KEYS, [
    "license",
    "nursingLicense",
    "healthCertificate",
    "idCard",
    "criminalRecord",
  ]);
  assert.equal(
      MANAGER_DOCUMENT_PATH_ALIAS_FIELDS.healthCertificate,
      "managerHealthCertificateStoragePath",
  );
});

test("canonical 매니저 자격 증빙은 UID와 경로가 맞는 정확히 한 키만 허용한다", () => {
  const managerId = "manager-1";
  const licensePath = "manager-documents/manager-1/license/license.jpg";
  const nursingPath =
    "manager-documents/manager-1/nursingLicense/nursing-license.jpg";
  const license = {
    managerDocumentFiles: {license: {fullPath: licensePath}},
    managerDocumentFilePaths: {license: licensePath},
    managerLicenseStoragePath: licensePath,
  };
  const nursingLicense = {
    managerDocumentFiles: {nursingLicense: {fullPath: nursingPath}},
    managerDocumentFilePaths: {nursingLicense: nursingPath},
  };

  assert.equal(
      resolveCanonicalManagerDocumentReference(license, managerId)?.documentKey,
      "license",
  );
  assert.equal(
      resolveCanonicalManagerDocumentReference(nursingLicense, managerId)?.documentKey,
      "nursingLicense",
  );
  assert.equal(resolveCanonicalManagerDocumentReference({}, managerId), null);
  assert.equal(resolveCanonicalManagerDocumentReference({
    managerDocumentFiles: {
      license: {fullPath: licensePath},
      nursingLicense: {fullPath: nursingPath},
    },
    managerDocumentFilePaths: {
      license: licensePath,
      nursingLicense: nursingPath,
    },
    managerLicenseStoragePath: licensePath,
  }, managerId), null);
  assert.equal(resolveCanonicalManagerDocumentReference({
    ...nursingLicense,
    managerLicenseStoragePath: licensePath,
  }, managerId), null);
  assert.equal(resolveCanonicalManagerDocumentReference({
    ...license,
    managerDocumentFilePaths: {license: `${licensePath}.mismatch`},
  }, managerId), null);
  assert.equal(resolveCanonicalManagerDocumentReference({
    ...license,
    managerLicenseStoragePath:
      "manager-documents/manager-2/license/foreign.jpg",
  }, managerId), null);
});

test("매니저 증빙 법적 보존은 활성·불완전 상태를 차단하고 만료만 허용한다", () => {
  const asOf = new Date("2026-08-30T00:00:00.000Z");

  assert.equal(managerDocumentLegalHoldBlocksDeletion({}, asOf), false);
  assert.equal(managerDocumentLegalHoldBlocksDeletion({
    managerDocumentLegalHoldUntil: "2026-09-01T00:00:00.000Z",
    managerDocumentLegalHoldReason: "분쟁 대응",
    managerDocumentLegalHoldByAdminUserId: "admin-1",
  }, asOf), true);
  assert.equal(managerDocumentLegalHoldBlocksDeletion({
    managerDocumentLegalHoldUntil: "해석할 수 없는 시각",
  }, asOf), true);
  assert.equal(managerDocumentLegalHoldBlocksDeletion({
    managerDocumentLegalHoldReason: "분쟁 대응",
  }, asOf), true);
  assert.equal(managerDocumentLegalHoldBlocksDeletion({
    managerDocumentLegalHoldByAdminUserId: "admin-1",
  }, asOf), true);
  assert.equal(managerDocumentLegalHoldBlocksDeletion({
    managerDocumentLegalHoldUntil: "2026-08-29T23:59:59.999Z",
  }, asOf), true);
  assert.equal(managerDocumentLegalHoldBlocksDeletion({
    managerDocumentLegalHoldUntil: "2026-08-29T23:59:59.999Z",
    managerDocumentLegalHoldReason: "만료된 보존",
    managerDocumentLegalHoldByAdminUserId: "admin-1",
  }, asOf), false);
  assert.equal(managerDocumentLegalHoldBlocksDeletion({
    managerDocumentLegalHoldUntil: asOf,
    managerDocumentLegalHoldReason: "경계 시각까지 보존",
    managerDocumentLegalHoldByAdminUserId: "admin-1",
  }, asOf), false);
  assert.equal(managerDocumentLegalHoldBlocksDeletion({
    managerDocumentLegalHoldUntil: {toMillis: () => {
      throw new Error("timestamp conversion failed");
    }},
    managerDocumentLegalHoldReason: "변환 오류",
    managerDocumentLegalHoldByAdminUserId: "admin-1",
  }, asOf), true);
});
