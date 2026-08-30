const test = require("node:test");
const assert = require("node:assert/strict");

const {
  analyzeManagerDocumentPolicy,
  collectReferences,
  hasStoragePolicyMismatch,
  isExpectedManagerDocumentPath,
} = require("../check-manager-document-storage");

function managerDocument(fullPath, documentKey = "license") {
  const legacyPaths = {};
  if (documentKey === "license") {
    legacyPaths.license = fullPath;
  }
  return [{
    id: "manager-1",
    name: "매니저",
    email: "manager@bodeul.test",
    documentStatus: "PENDING_REVIEW",
    documentFiles: {
      [documentKey]: {fullPath, contentType: "image/jpeg"},
    },
    documentFilePaths: {[documentKey]: fullPath},
    legacyPaths,
  }];
}

test("매니저 증빙 경로는 사용자와 문서 키에 정확히 귀속된다", () => {
  assert.equal(isExpectedManagerDocumentPath(
      "manager-documents/manager-1/nursingLicense/license.jpg",
      "manager-1",
      "nursingLicense",
  ), true);
  assert.equal(isExpectedManagerDocumentPath(
      "manager-documents/manager-2/nursingLicense/license.jpg",
      "manager-1",
      "nursingLicense",
  ), false);
  assert.equal(isExpectedManagerDocumentPath(
      "manager-documents/manager-1/license/id.jpg",
      "manager-1",
      "nursingLicense",
  ), false);
  assert.equal(isExpectedManagerDocumentPath(
      "manager-documents/manager-1/license/id.jpg",
      " manager-1 ",
      "license",
  ), false);
  assert.equal(isExpectedManagerDocumentPath(
      "manager-documents/manager-1/license/id.jpg",
      "manager-1",
      " license ",
  ), false);
});

test("Storage 객체는 이미지 MIME과 10 MiB 경계를 지켜야 한다", () => {
  const reference = {contentType: "image/png"};
  assert.equal(hasStoragePolicyMismatch(reference, {
    contentType: "image/png",
    size: String(10 * 1024 * 1024),
  }), false);
  assert.equal(hasStoragePolicyMismatch(reference, {
    contentType: "image/png",
    size: String(10 * 1024 * 1024 + 1),
  }), true);
  assert.equal(hasStoragePolicyMismatch(reference, {
    contentType: "application/pdf",
    size: "128",
  }), true);
  assert.equal(hasStoragePolicyMismatch(reference, {
    contentType: "image/jpeg",
    size: "128",
  }), true);
});

test("canonical alias와 경로 맵 불일치를 보고한다", () => {
  const valid = collectReferences(managerDocument(
      "manager-documents/manager-1/license/license.jpg",
  ));
  assert.equal(valid[0].pathMismatch, false);
  assert.equal(valid[0].policyKind, "canonical");

  const crossOwner = collectReferences(managerDocument(
      "manager-documents/manager-2/license/license.jpg",
  ));
  assert.equal(crossOwner[0].pathMismatch, true);

  const missingAlias = managerDocument(
      "manager-documents/manager-1/license/license.jpg",
  );
  missingAlias[0].legacyPaths.license = "";
  assert.equal(collectReferences(missingAlias)[0].pathMismatch, true);

  const missingPathMap = managerDocument(
      "manager-documents/manager-1/license/license.jpg",
  );
  missingPathMap[0].documentFilePaths = {};
  assert.equal(collectReferences(missingPathMap)[0].pathMismatch, true);
});

test("nursingLicense는 top-level alias 없이도 정상 canonical 참조다", () => {
  const references = collectReferences(managerDocument(
      "manager-documents/manager-1/nursingLicense/license.webp",
      "nursingLicense",
  ));
  assert.equal(references.length, 1);
  assert.equal(references[0].pathMismatch, false);
  assert.equal(references[0].policyKind, "canonical");
});

test("healthCertificate alias는 선택 사항이지만 존재하면 경로가 같아야 한다", () => {
  const fullPath = "manager-documents/manager-1/healthCertificate/legacy.png";
  const withoutAlias = managerDocument(fullPath, "healthCertificate");
  assert.equal(collectReferences(withoutAlias)[0].pathMismatch, false);

  const mismatchedAlias = managerDocument(fullPath, "healthCertificate");
  mismatchedAlias[0].legacyPaths.healthCertificate =
    "manager-documents/manager-1/healthCertificate/other.png";
  assert.equal(collectReferences(mismatchedAlias)[0].pathMismatch, true);
});

test("심사 상태는 canonical 정확히 1종을 요구하고 legacy는 이관 후보로 분리한다", () => {
  const license = managerDocument(
      "manager-documents/manager-1/license/license.jpg",
  )[0];
  assert.deepEqual(analyzeManagerDocumentPolicy([license])[0].issues, []);

  const both = structuredClone(license);
  const nursingPath =
    "manager-documents/manager-1/nursingLicense/nursing.png";
  both.documentFiles.nursingLicense = {fullPath: nursingPath};
  both.documentFilePaths.nursingLicense = nursingPath;
  assert.equal(analyzeManagerDocumentPolicy([both])[0].issues.length, 1);

  const legacyOnly = managerDocument(
      "manager-documents/manager-1/healthCertificate/legacy.png",
      "healthCertificate",
  )[0];
  const result = analyzeManagerDocumentPolicy([legacyOnly])[0];
  assert.equal(result.issues.length, 1);
  assert.deepEqual(result.legacyKeys, ["healthCertificate"]);
  assert.equal(result.migrationRequired, true);

  const originalsDeleted = {
    ...legacyOnly,
    documentFiles: {},
    documentFilePaths: {},
    legacyPaths: {},
    documentStatus: "APPROVED",
    originalsDeletedAt: "2026-08-30T00:00:00.000Z",
  };
  assert.deepEqual(
      analyzeManagerDocumentPolicy([originalsDeleted])[0].issues,
      [],
  );
});
