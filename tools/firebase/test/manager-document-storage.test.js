const test = require("node:test");
const assert = require("node:assert/strict");

const {
  collectReferences,
  isExpectedManagerDocumentPath,
} = require("../check-manager-document-storage");

function managerDocument(fullPath) {
  return [{
    id: "manager-1",
    name: "매니저",
    email: "manager@bodeul.test",
    documentFiles: {
      idCard: {fullPath, contentType: "image/jpeg"},
    },
    documentFilePaths: {idCard: fullPath},
    legacyPaths: {idCard: fullPath},
  }];
}

test("매니저 증빙 경로는 사용자와 문서 키에 정확히 귀속된다", () => {
  assert.equal(isExpectedManagerDocumentPath(
      "manager-documents/manager-1/idCard/id.jpg",
      "manager-1",
      "idCard",
  ), true);
  assert.equal(isExpectedManagerDocumentPath(
      "manager-documents/manager-2/idCard/id.jpg",
      "manager-1",
      "idCard",
  ), false);
  assert.equal(isExpectedManagerDocumentPath(
      "manager-documents/manager-1/license/id.jpg",
      "manager-1",
      "idCard",
  ), false);
  assert.equal(isExpectedManagerDocumentPath(
      "manager-documents/manager-1/idCard/id.jpg",
      " manager-1 ",
      "idCard",
  ), false);
  assert.equal(isExpectedManagerDocumentPath(
      "manager-documents/manager-1/idCard/id.jpg",
      "manager-1",
      " idCard ",
  ), false);
  assert.equal(isExpectedManagerDocumentPath(
      "manager-documents/manager-1/idCard/id.jpg",
      1,
      "idCard",
  ), false);
});

test("세 alias가 같아도 다른 사용자 경로이면 불일치로 보고한다", () => {
  const valid = collectReferences(managerDocument(
      "manager-documents/manager-1/idCard/id.jpg",
  ));
  assert.equal(valid[0].pathMismatch, false);

  const crossOwner = collectReferences(managerDocument(
      "manager-documents/manager-2/idCard/id.jpg",
  ));
  assert.equal(crossOwner[0].pathMismatch, true);

  const crossDocumentKey = collectReferences(managerDocument(
      "manager-documents/manager-1/license/id.jpg",
  ));
  assert.equal(crossDocumentKey[0].pathMismatch, true);

  const missingLegacy = managerDocument(
      "manager-documents/manager-1/idCard/id.jpg",
  );
  missingLegacy[0].legacyPaths.idCard = "";
  assert.equal(collectReferences(missingLegacy)[0].pathMismatch, true);

  const missingPathMap = managerDocument(
      "manager-documents/manager-1/idCard/id.jpg",
  );
  missingPathMap[0].documentFilePaths = {};
  assert.equal(collectReferences(missingPathMap)[0].pathMismatch, true);

  const whitespaceOwner = managerDocument(
      "manager-documents/manager-1/idCard/id.jpg",
  );
  whitespaceOwner[0].id = " manager-1 ";
  assert.equal(collectReferences(whitespaceOwner)[0].pathMismatch, true);

  const whitespacePath = managerDocument(
      " manager-documents/manager-1/idCard/id.jpg ",
  );
  assert.equal(collectReferences(whitespacePath)[0].pathMismatch, true);
});
