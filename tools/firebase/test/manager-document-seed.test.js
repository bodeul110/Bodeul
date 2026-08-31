const test = require("node:test");
const assert = require("node:assert/strict");

const {
  buildSamplePatch,
  buildSampleMutation,
  buildSampleUploads,
  parseOptions,
} = require("../seed-manager-document-storage-sample");

test("매니저 샘플 seed는 기본 dry-run이다", () => {
  assert.equal(parseOptions([]).dryRun, true);
  assert.equal(parseOptions(["--apply"]).dryRun, false);
});

test("샘플 업로드는 license canonical 자격 증빙 1종만 만든다", () => {
  const uploads = buildSampleUploads("manager-1", 1234);
  assert.equal(uploads.length, 1);
  assert.equal(uploads[0].documentKey, "license");
  assert.equal(
      uploads[0].fullPath,
      "manager-documents/manager-1/license/1234-sample-license.png",
  );
});

test("샘플 Firestore patch에는 신분증·범죄경력·건강진단서가 없다", () => {
  const uploads = buildSampleUploads("manager-1", 1234);
  const patch = buildSamplePatch({
    manager: {name: "매니저", documentHistory: []},
    summaryText: "자격 증빙 샘플 업로드",
    uploads,
    now: 1234,
  });
  assert.deepEqual(Object.keys(patch.managerDocumentFiles), ["license"]);
  assert.deepEqual(Object.keys(patch.managerDocumentFilePaths), ["license"]);
  assert.equal(patch.managerLicenseStoragePath, uploads[0].fullPath);
  for (const key of ["idCard", "criminalRecord", "healthCertificate"]) {
    assert.equal(key in patch.managerDocumentFiles, false);
    assert.equal(key in patch.managerDocumentFilePaths, false);
  }

  const mutation = buildSampleMutation({
    manager: {name: "매니저", documentHistory: []},
    summaryText: "자격 증빙 샘플 업로드",
    uploads,
    now: 1234,
  });
  assert.deepEqual(mutation.deleteFields, [
    "managerIdCardStoragePath",
    "managerCriminalRecordStoragePath",
    "managerHealthCertificateStoragePath",
    "managerDocumentEvidenceMigration",
  ]);
});
