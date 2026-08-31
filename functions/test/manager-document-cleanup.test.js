const assert = require("node:assert/strict");
const test = require("node:test");

const {
  cleanupReplacedManagerDocumentObjects,
  ManagerDocumentReplacementStorageGateway,
  collectReplacedManagerDocumentCandidates,
  deleteUnreferencedManagerDocumentCandidates,
  isStorageObjectNotFound,
  managerDocumentDataReferencesStoragePath,
} = require("../src/manager-document-cleanup");
const {
  createTransactionalDocument,
} = require("./manager-document-deletion-test-helper");

const MANAGER_ID = "manager-1";

test("교체 정리는 활성 키와 과거 healthCertificate 원본만 후보로 만든다", () => {
  const paths = {
    license: documentPath("license", "old-license.jpg"),
    nursingLicense: documentPath("nursingLicense", "old-nursing.jpg"),
    healthCertificate: documentPath("healthCertificate", "old-health.jpg"),
    idCard: documentPath("idCard", "old-id.jpg"),
    criminalRecord: documentPath("criminalRecord", "old-record.jpg"),
  };
  const beforeData = managerDocumentData(paths, {includeHealthAlias: false});
  const afterData = managerDocumentData({
    license: documentPath("license", "new-license.jpg"),
    idCard: paths.idCard,
    criminalRecord: paths.criminalRecord,
  });

  assert.deepEqual(
      collectReplacedManagerDocumentCandidates(MANAGER_ID, beforeData, afterData),
      [
        {managerId: MANAGER_ID, documentKey: "license", storagePath: paths.license},
        {
          managerId: MANAGER_ID,
          documentKey: "nursingLicense",
          storagePath: paths.nursingLicense,
        },
        {
          managerId: MANAGER_ID,
          documentKey: "healthCertificate",
          storagePath: paths.healthCertificate,
        },
      ],
  );
});

test("과거 healthCertificate 별칭은 없어도 되지만 있으면 중첩 경로와 같아야 한다", () => {
  const healthPath = documentPath("healthCertificate", "legacy.jpg");
  const withoutAlias = managerDocumentData(
      {healthCertificate: healthPath},
      {includeHealthAlias: false},
  );
  assert.equal(
      collectReplacedManagerDocumentCandidates(
          MANAGER_ID,
          withoutAlias,
          canonicalAfterData(),
      ).length,
      1,
  );

  const withAlias = managerDocumentData({healthCertificate: healthPath});
  assert.equal(
      collectReplacedManagerDocumentCandidates(
          MANAGER_ID,
          withAlias,
          canonicalAfterData(),
      ).length,
      1,
  );

  const mismatchedAlias = {
    ...withAlias,
    managerHealthCertificateStoragePath:
      documentPath("healthCertificate", "other.jpg"),
  };
  assert.equal(
      collectReplacedManagerDocumentCandidates(
          MANAGER_ID,
          mismatchedAlias,
          canonicalAfterData(),
      ).length,
      0,
  );

  const missingPathMap = {
    ...withoutAlias,
    managerDocumentFilePaths: {},
  };
  assert.equal(
      collectReplacedManagerDocumentCandidates(
          MANAGER_ID,
          missingPathMap,
          canonicalAfterData(),
      ).length,
      0,
  );
});

test("다른 사용자나 문서 키에 속한 이전 경로는 교체 정리하지 않는다", () => {
  const validPath = documentPath("license", "valid.jpg");
  const cases = [
    {
      metadataPath: "manager-documents/manager-2/license/foreign.jpg",
      pathMapPath: "manager-documents/manager-2/license/foreign.jpg",
      aliasPath: "manager-documents/manager-2/license/foreign.jpg",
    },
    {
      metadataPath: documentPath("nursingLicense", "wrong-key.jpg"),
      pathMapPath: documentPath("nursingLicense", "wrong-key.jpg"),
      aliasPath: documentPath("nursingLicense", "wrong-key.jpg"),
    },
    {
      metadataPath: validPath,
      pathMapPath: documentPath("license", "mismatch.jpg"),
      aliasPath: validPath,
    },
  ];

  for (const item of cases) {
    const beforeData = {
      role: "MANAGER",
      managerDocumentFiles: {license: {fullPath: item.metadataPath}},
      managerDocumentFilePaths: {license: item.pathMapPath},
      managerLicenseStoragePath: item.aliasPath,
    };
    assert.equal(
        collectReplacedManagerDocumentCandidates(
            MANAGER_ID,
            beforeData,
            canonicalAfterData(),
        ).length,
        0,
    );
  }
});

test("이벤트 after의 어느 알려진 참조에라도 이전 경로가 남으면 후보에서 제외한다", () => {
  const oldPath = documentPath("license", "old.jpg");
  const beforeData = managerDocumentData({license: oldPath});
  const afterData = {
    role: "MANAGER",
    managerDocumentFiles: {
      healthCertificate: {fullPath: oldPath},
      nursingLicense: {
        fullPath: documentPath("nursingLicense", "current.jpg"),
      },
    },
    managerDocumentFilePaths: {
      nursingLicense: documentPath("nursingLicense", "current.jpg"),
    },
  };

  assert.equal(managerDocumentDataReferencesStoragePath(afterData, oldPath), true);
  assert.deepEqual(
      collectReplacedManagerDocumentCandidates(MANAGER_ID, beforeData, afterData),
      [],
  );
});

test("후보 생성은 before의 활성·불완전 legal hold를 차단하고 만료만 허용한다", () => {
  const asOf = new Date("2026-08-30T00:00:00.000Z");
  const oldPath = documentPath("license", "old.jpg");
  const baseBeforeData = managerDocumentData({license: oldPath});
  const afterData = canonicalAfterData();
  const blockedHolds = [
    {
      managerDocumentLegalHoldUntil: "2026-09-01T00:00:00.000Z",
      managerDocumentLegalHoldReason: "분쟁 대응",
      managerDocumentLegalHoldByAdminUserId: "admin-1",
    },
    {managerDocumentLegalHoldUntil: "invalid"},
    {managerDocumentLegalHoldReason: "분쟁 대응"},
    {managerDocumentLegalHoldByAdminUserId: "admin-1"},
  ];

  for (const hold of blockedHolds) {
    assert.deepEqual(collectReplacedManagerDocumentCandidates(
        MANAGER_ID,
        {...baseBeforeData, ...hold},
        afterData,
        asOf,
    ), []);
  }

  assert.deepEqual(collectReplacedManagerDocumentCandidates(
      MANAGER_ID,
      {
        ...baseBeforeData,
        managerDocumentLegalHoldUntil: "2026-08-29T23:59:59.999Z",
        managerDocumentLegalHoldReason: "만료된 보존",
        managerDocumentLegalHoldByAdminUserId: "admin-1",
      },
      afterData,
      asOf,
  ), [{managerId: MANAGER_ID, documentKey: "license", storagePath: oldPath}]);
});

test("변경 후 canonical 증빙이 0개·2개이거나 경로가 불일치하면 정리하지 않는다", () => {
  const oldPath = documentPath("license", "old.jpg");
  const newLicensePath = documentPath("license", "new.jpg");
  const nursingPath = documentPath("nursingLicense", "new.jpg");
  const beforeData = managerDocumentData({license: oldPath});
  const invalidAfterStates = [
    managerDocumentData({}),
    managerDocumentData({license: newLicensePath, nursingLicense: nursingPath}),
    {
      ...managerDocumentData({license: newLicensePath}),
      managerDocumentFilePaths: {
        license: documentPath("license", "mismatch.jpg"),
      },
    },
    {
      ...managerDocumentData({nursingLicense: nursingPath}),
      managerLicenseStoragePath: newLicensePath,
    },
  ];

  for (const afterData of invalidAfterStates) {
    assert.deepEqual(
        collectReplacedManagerDocumentCandidates(
            MANAGER_ID,
            beforeData,
            afterData,
        ),
        [],
    );
  }
});

test("사용자 삭제와 MANAGER 역할 이탈은 후보와 Storage 삭제를 만들지 않는다", async () => {
  const oldPath = documentPath("license", "old.jpg");
  const beforeData = managerDocumentData({license: oldPath});
  const afterStates = [
    null,
    {...managerDocumentData({}), role: "PATIENT"},
  ];
  let documentReads = 0;
  let storageDeletes = 0;

  for (const afterData of afterStates) {
    const candidates = collectReplacedManagerDocumentCandidates(
        MANAGER_ID,
        beforeData,
        afterData,
    );
    assert.deepEqual(candidates, []);
    const result = await deleteUnreferencedManagerDocumentCandidates({
      documentReference: {
        async get() {
          documentReads += 1;
          return {exists: true, data: () => managerDocumentData({})};
        },
      },
      candidates,
      storage: {
        async deleteManagerDocument() {
          storageDeletes += 1;
        },
      },
    });
    assert.equal(result.deleted, 0);
  }
  assert.equal(documentReads, 0);
  assert.equal(storageDeletes, 0);
});

test("transaction claim은 재참조와 잘못된 후보를 건너뛰고 미참조 원본만 삭제한다", async () => {
  const licensePath = documentPath("license", "old.jpg");
  const nursingPath = documentPath("nursingLicense", "old.jpg");
  const candidates = [
    {managerId: MANAGER_ID, documentKey: "license", storagePath: licensePath},
    {managerId: MANAGER_ID, documentKey: "nursingLicense", storagePath: nursingPath},
    {
      managerId: MANAGER_ID,
      documentKey: "idCard",
      storagePath: documentPath("idCard", "legacy.jpg"),
    },
  ];
  const fixture = createTransactionalDocument({
    ...canonicalAfterData(),
    managerHealthCertificateStoragePath: licensePath,
  });
  const deleted = [];
  const storage = {
    async inspectManagerDocument() {
      return {objectGeneration: "11"};
    },
    async deleteManagerDocument(storagePath, managerId, documentKey, generation) {
      deleted.push({storagePath, managerId, documentKey, generation});
    },
  };

  const result = await deleteUnreferencedManagerDocumentCandidates({
    firestore: fixture.firestore,
    documentReference: fixture.reference,
    candidates,
    storage,
  });

  assert.deepEqual(deleted, [{
    storagePath: nursingPath,
    managerId: MANAGER_ID,
    documentKey: "nursingLicense",
    generation: "11",
  }]);
  assert.deepEqual(result, {
    deleted: 1,
    skippedReferenced: 1,
    skippedInvalid: 1,
    skippedDocumentState: 0,
    skippedLegalHold: 0,
    skippedClaimConflict: 0,
  });
});

test("claim transaction은 최신 legal hold를 차단하고 만료 상태만 허용한다", async () => {
  const asOf = new Date("2026-08-30T00:00:00.000Z");
  const blockedHolds = [
    {
      managerDocumentLegalHoldUntil: "2026-09-01T00:00:00.000Z",
      managerDocumentLegalHoldReason: "분쟁 대응",
      managerDocumentLegalHoldByAdminUserId: "admin-1",
    },
    {managerDocumentLegalHoldUntil: "invalid"},
    {managerDocumentLegalHoldReason: "분쟁 대응"},
    {managerDocumentLegalHoldByAdminUserId: "admin-1"},
  ];
  const deleted = [];
  for (let index = 0; index < blockedHolds.length; index += 1) {
    const fixture = createTransactionalDocument({
      ...canonicalAfterData(),
      ...blockedHolds[index],
    });
    const result = await deleteUnreferencedManagerDocumentCandidates({
      firestore: fixture.firestore,
      documentReference: fixture.reference,
      candidates: [{
        managerId: MANAGER_ID,
        documentKey: "license",
        storagePath: documentPath("license", `blocked-${index}.jpg`),
      }],
      storage: {
        async inspectManagerDocument() {
          assert.fail("legal hold 상태에서 Storage를 검사하면 안 됩니다.");
        },
        async deleteManagerDocument() {
          assert.fail("legal hold 상태에서 Storage를 삭제하면 안 됩니다.");
        },
      },
      asOf,
    });
    assert.equal(result.skippedLegalHold, 1);
  }

  const expiredFixture = createTransactionalDocument({
    ...canonicalAfterData(),
    managerDocumentLegalHoldUntil: "2026-08-29T23:59:59.999Z",
    managerDocumentLegalHoldReason: "만료된 보존",
    managerDocumentLegalHoldByAdminUserId: "admin-1",
  });
  const expiredPath = documentPath("license", "expired.jpg");
  const expiredResult = await deleteUnreferencedManagerDocumentCandidates({
    firestore: expiredFixture.firestore,
    documentReference: expiredFixture.reference,
    candidates: [{
      managerId: MANAGER_ID,
      documentKey: "license",
      storagePath: expiredPath,
    }],
    storage: {
      async inspectManagerDocument() {
        return {objectGeneration: "13"};
      },
      async deleteManagerDocument(storagePath) {
        deleted.push(storagePath);
      },
    },
    asOf,
  });

  assert.deepEqual(deleted, [expiredPath]);
  assert.deepEqual(expiredResult, {
    deleted: 1,
    skippedReferenced: 0,
    skippedInvalid: 0,
    skippedDocumentState: 0,
    skippedLegalHold: 0,
    skippedClaimConflict: 0,
  });
});

test("claim transaction 시 사용자 문서가 사라지거나 역할이 바뀌면 삭제하지 않는다", async () => {
  const healthPath = documentPath("healthCertificate", "legacy.jpg");
  const licensePath = documentPath("license", "legacy.jpg");
  const deleted = [];
  const states = [null, {...canonicalAfterData(), role: "PATIENT"}];
  for (const [index, state] of states.entries()) {
    const fixture = createTransactionalDocument(state);
    const result = await deleteUnreferencedManagerDocumentCandidates({
      firestore: fixture.firestore,
      documentReference: fixture.reference,
      candidates: [{
        managerId: MANAGER_ID,
        documentKey: index === 0 ? "healthCertificate" : "license",
        storagePath: index === 0 ? healthPath : licensePath,
      }],
      storage: {
        async inspectManagerDocument() {
          assert.fail("삭제 불가 문서에서 Storage를 검사하면 안 됩니다.");
        },
        async deleteManagerDocument(storagePath) {
          deleted.push(storagePath);
        },
      },
    });
    assert.equal(result.skippedDocumentState, 1);
  }

  assert.deepEqual(deleted, []);
});

test("Storage gateway는 object-not-found만 성공으로 처리하고 경로를 다시 검증한다", async () => {
  const calls = [];
  let deleteFailure = null;
  let metadataFailure = null;
  const gateway = new ManagerDocumentReplacementStorageGateway({
    file(storagePath, options) {
      return {
        async getMetadata() {
          if (metadataFailure) {
            throw metadataFailure;
          }
          return [{generation: "41"}];
        },
        async delete(deleteOptions) {
          calls.push({storagePath, options, deleteOptions});
          if (deleteFailure) {
            throw deleteFailure;
          }
        },
      };
    },
  });
  const licensePath = documentPath("license", "old.jpg");

  assert.deepEqual(
      await gateway.inspectManagerDocument(licensePath, MANAGER_ID, "license"),
      {objectGeneration: "41"},
  );
  await gateway.deleteManagerDocument(licensePath, MANAGER_ID, "license", "41");
  metadataFailure = {code: 404};
  assert.deepEqual(
      await gateway.inspectManagerDocument(
          documentPath("healthCertificate", "missing.jpg"),
          MANAGER_ID,
          "healthCertificate",
      ),
      {objectMissing: true},
  );
  metadataFailure = null;
  deleteFailure = {code: 404};
  await gateway.deleteManagerDocument(
      documentPath("healthCertificate", "missing.jpg"),
      MANAGER_ID,
      "healthCertificate",
      "42",
  );
  deleteFailure = {code: 500};
  await assert.rejects(
      gateway.deleteManagerDocument(
          documentPath("nursingLicense", "failure.jpg"),
          MANAGER_ID,
          "nursingLicense",
          "43",
      ),
      (error) => error.code === 500,
  );
  deleteFailure = null;
  await assert.rejects(
      gateway.deleteManagerDocument(
          "manager-documents/manager-2/license/foreign.jpg",
          MANAGER_ID,
          "license",
          "44",
      ),
      (error) => error.code === "MANAGER_REPLACEMENT_PATH_INVALID",
  );
  await assert.rejects(
      gateway.deleteManagerDocument(
          documentPath("idCard", "legacy.jpg"),
          MANAGER_ID,
          "idCard",
          "45",
      ),
      (error) => error.code === "MANAGER_REPLACEMENT_PATH_INVALID",
  );
  await assert.rejects(
      gateway.deleteManagerDocument(licensePath, MANAGER_ID, "license", "invalid"),
      (error) => error.code === "MANAGER_REPLACEMENT_GENERATION_INVALID",
  );

  assert.equal(calls.length, 3);
  assert.equal(calls[0].options, undefined);
  assert.deepEqual(calls[0].deleteOptions, {
    ignoreNotFound: true,
    ifGenerationMatch: "41",
  });
  assert.equal(isStorageObjectNotFound({code: "storage/object-not-found"}), true);
  assert.equal(isStorageObjectNotFound({errors: [{reason: "notFound"}]}), true);
  assert.equal(isStorageObjectNotFound({code: 500}), false);
});

test("교체 정리 trigger는 일시 오류를 재시도한다", () => {
  assert.equal(cleanupReplacedManagerDocumentObjects.__endpoint.eventTrigger.retry, true);
});

function managerDocumentData(paths, {includeHealthAlias = true} = {}) {
  const data = {
    role: "MANAGER",
    managerDocumentFiles: {},
    managerDocumentFilePaths: {},
  };
  for (const [documentKey, storagePath] of Object.entries(paths)) {
    data.managerDocumentFiles[documentKey] = {fullPath: storagePath};
    data.managerDocumentFilePaths[documentKey] = storagePath;
    if (documentKey === "license") {
      data.managerLicenseStoragePath = storagePath;
    } else if (documentKey === "healthCertificate" && includeHealthAlias) {
      data.managerHealthCertificateStoragePath = storagePath;
    } else if (documentKey === "idCard") {
      data.managerIdCardStoragePath = storagePath;
    } else if (documentKey === "criminalRecord") {
      data.managerCriminalRecordStoragePath = storagePath;
    }
  }
  return data;
}

function canonicalAfterData() {
  return managerDocumentData({
    nursingLicense: documentPath("nursingLicense", "current.jpg"),
  });
}

function documentPath(documentKey, fileName) {
  return `manager-documents/${MANAGER_ID}/${documentKey}/${fileName}`;
}
