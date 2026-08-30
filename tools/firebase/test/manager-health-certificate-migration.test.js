const test = require("node:test");
const assert = require("node:assert/strict");

const {
  applyMigrationPlan,
  buildFirestoreMutation,
  buildMigrationPlan,
  parseOptions,
  shouldBlockApply,
  validateManagerDocumentLegalHold,
} = require("../migrate-manager-health-certificate");

const managerId = "manager-1";
const sourcePath =
  `manager-documents/${managerId}/healthCertificate/legacy.png`;
const destinationPath =
  `manager-documents/${managerId}/nursingLicense/legacy.png`;

function legacyData({alias = true, path = sourcePath, metadataPath = sourcePath} = {}) {
  const data = {
    role: "MANAGER",
    managerDocumentFiles: {
      healthCertificate: {
        fullPath: metadataPath,
        contentType: "image/png",
        fileName: "legacy.png",
      },
    },
    managerDocumentFilePaths: {healthCertificate: path},
  };
  if (alias) data.managerHealthCertificateStoragePath = sourcePath;
  return data;
}

function canonicalData() {
  return {
    role: "MANAGER",
    managerDocumentFiles: {
      nursingLicense: {
        fullPath: destinationPath,
        contentType: "image/png",
        fileName: "legacy.png",
      },
    },
    managerDocumentFilePaths: {nursingLicense: destinationPath},
  };
}

function withLegalHold(data, overrides = {}) {
  return {
    ...data,
    managerDocumentLegalHoldUntil: "2100-01-01T00:00:00.000Z",
    managerDocumentLegalHoldReason: "분쟁 대응",
    managerDocumentLegalHoldByAdminUserId: "admin-1",
    ...overrides,
  };
}

function storageObject(name, overrides = {}) {
  return {
    name,
    generation: "7",
    contentType: "image/png",
    size: "128",
    md5Hash: "same-md5",
    crc32c: "same-crc",
    ...overrides,
  };
}

function plan(input = {}) {
  return buildMigrationPlan({
    managerId,
    data: legacyData(),
    sourceObject: storageObject(sourcePath),
    destinationObject: null,
    ...input,
  });
}

test("기본 실행은 dry-run이고 --apply만 쓰기를 허용한다", () => {
  assert.equal(parseOptions([]).apply, false);
  assert.equal(parseOptions(["--apply"]).apply, true);
  assert.equal(parseOptions(["--uid", managerId]).uid, managerId);
  assert.throws(() => parseOptions(["--write"]), /지원하지 않는 인자/);
  assert.equal(shouldBlockApply([{action: "BLOCKED"}], false), false);
  assert.equal(shouldBlockApply([{action: "BLOCKED"}], true), true);
});

test("완전한 healthCertificate 레거시는 복사 후 교체 계획을 만든다", () => {
  const result = plan();
  assert.equal(result.action, "COPY_AND_UPDATE");
  assert.equal(result.sourcePath, sourcePath);
  assert.equal(result.destinationPath, destinationPath);

  const withoutAlias = plan({data: legacyData({alias: false})});
  assert.equal(withoutAlias.action, "COPY_AND_UPDATE");
});

test("동일한 대상 복사본은 재사용하고 충돌 복사본은 차단한다", () => {
  const resumable = plan({
    destinationObject: storageObject(destinationPath, {generation: "8"}),
  });
  assert.equal(resumable.action, "UPDATE_METADATA");

  const conflict = plan({
    destinationObject: storageObject(destinationPath, {md5Hash: "different"}),
  });
  assert.equal(conflict.action, "BLOCKED");
  assert.match(conflict.reason, /충돌/);
});

test("부분 상태, 경로 불일치, canonical 충돌은 차단한다", () => {
  const partial = legacyData();
  delete partial.managerDocumentFilePaths.healthCertificate;
  assert.equal(plan({data: partial}).action, "BLOCKED");

  assert.equal(plan({
    data: legacyData({path: `${sourcePath}.other`}),
  }).action, "BLOCKED");

  const conflict = legacyData();
  conflict.managerDocumentFiles.license = {
    fullPath: `manager-documents/${managerId}/license/license.png`,
  };
  assert.equal(plan({data: conflict}).action, "BLOCKED");

  const partialLicense = {
    role: "MANAGER",
    managerDocumentFiles: {
      license: {
        fullPath: `manager-documents/${managerId}/license/license.png`,
      },
    },
    managerDocumentFilePaths: {},
  };
  assert.equal(plan({
    data: partialLicense,
    sourceObject: null,
  }).action, "BLOCKED");
});

test("활성·불완전·해석 불가·빈 legal hold는 차단하고 완전한 만료 상태만 허용한다", () => {
  const asOf = new Date("2026-08-30T00:00:00.000Z");
  assert.match(
      validateManagerDocumentLegalHold(withLegalHold(legacyData()), asOf),
      /활성/,
  );
  assert.match(validateManagerDocumentLegalHold({
    ...legacyData(),
    managerDocumentLegalHoldReason: "분쟁 대응",
  }, asOf), /불완전/);
  assert.match(validateManagerDocumentLegalHold(withLegalHold(legacyData(), {
    managerDocumentLegalHoldUntil: "not-a-date",
  }), asOf), /형식|해석/);
  assert.match(validateManagerDocumentLegalHold(withLegalHold(legacyData(), {
    managerDocumentLegalHoldUntil: 0,
  }), asOf), /Firestore timestamp/);
  for (const overrides of [
    {managerDocumentLegalHoldReason: ""},
    {managerDocumentLegalHoldReason: "   "},
    {managerDocumentLegalHoldByAdminUserId: ""},
    {managerDocumentLegalHoldByAdminUserId: " admin-1 "},
  ]) {
    assert.match(
        validateManagerDocumentLegalHold(withLegalHold(legacyData(), overrides), asOf),
        /비어 있거나 앞뒤 공백/,
    );
  }
  assert.equal(validateManagerDocumentLegalHold(withLegalHold(legacyData(), {
    managerDocumentLegalHoldUntil: "2026-08-29T23:59:59.999Z",
  }), asOf), "");

  assert.equal(plan({data: withLegalHold(legacyData())}).action, "BLOCKED");
  assert.equal(plan({data: withLegalHold(legacyData(), {
    managerDocumentLegalHoldUntil: "2000-01-01T00:00:00.000Z",
  })}).action, "COPY_AND_UPDATE");
  assert.equal(plan({data: {...legacyData(), role: " MANAGER "}}).action, "BLOCKED");
});

test("canonical 완료 상태는 no-op이고 구 객체만 남으면 cleanup 계획이다", () => {
  const completed = plan({
    data: canonicalData(),
    sourceObject: null,
    destinationObject: storageObject(destinationPath),
  });
  assert.equal(completed.action, "NOOP");

  const cleanup = plan({
    data: canonicalData(),
    sourceObject: storageObject(sourcePath),
    destinationObject: storageObject(destinationPath),
  });
  assert.equal(cleanup.action, "CLEANUP_SOURCE");
});

test("Firestore mutation은 canonical map을 만들고 health alias를 삭제한다", () => {
  const mutation = buildFirestoreMutation(legacyData(), destinationPath);
  assert.equal(
      mutation.data.managerDocumentFiles.nursingLicense.fullPath,
      destinationPath,
  );
  assert.equal(
      mutation.data.managerDocumentFilePaths.nursingLicense,
      destinationPath,
  );
  assert.equal("healthCertificate" in mutation.data.managerDocumentFiles, false);
  assert.deepEqual(mutation.data.managerDocumentEvidenceMigration, {
    migrationId: "health-certificate-to-nursing-license-v1",
    sourceKey: "healthCertificate",
    destinationKey: "nursingLicense",
    sourcePath,
    destinationPath,
  });
  assert.deepEqual(mutation.deleteFields, ["managerHealthCertificateStoragePath"]);
});

test("apply는 copy, 검증, transaction, source delete 순서를 지킨다", async () => {
  const calls = [];
  const result = plan();
  await applyMigrationPlan(result, {
    copySource: async () => calls.push("copy"),
    getDestination: async () => {
      calls.push("get-destination");
      return storageObject(destinationPath);
    },
    updateMetadata: async (builder) => {
      calls.push("transaction");
      const decision = builder(legacyData());
      assert.equal(decision.state, "UPDATE_METADATA");
      assert.ok(decision.mutation);
    },
    getLatestData: async () => {
      calls.push("latest-data");
      return canonicalData();
    },
    deleteSource: async () => calls.push("delete-source"),
  });
  assert.deepEqual(calls, [
    "copy",
    "get-destination",
    "transaction",
    "get-destination",
    "latest-data",
    "delete-source",
  ]);
});

test("transaction 실패 시 원본을 삭제하지 않고 재실행은 기존 복사본을 재사용한다", async () => {
  const calls = [];
  await assert.rejects(
      applyMigrationPlan(plan(), {
        copySource: async () => calls.push("copy"),
        getDestination: async () => storageObject(destinationPath),
        updateMetadata: async () => {
          calls.push("transaction");
          throw new Error("commit 실패");
        },
        getLatestData: async () => canonicalData(),
        deleteSource: async () => calls.push("delete-source"),
      }),
      /commit 실패/,
  );
  assert.deepEqual(calls, ["copy", "transaction"]);

  const resumed = plan({
    destinationObject: storageObject(destinationPath),
  });
  const resumedCalls = [];
  await applyMigrationPlan(resumed, {
    copySource: async () => resumedCalls.push("copy"),
    getDestination: async () => storageObject(destinationPath),
    updateMetadata: async (builder) => {
      resumedCalls.push("transaction");
      builder(legacyData());
    },
    getLatestData: async () => {
      resumedCalls.push("latest-data");
      return canonicalData();
    },
    deleteSource: async () => resumedCalls.push("delete-source"),
  });
  assert.deepEqual(resumedCalls, [
    "transaction",
    "latest-data",
    "delete-source",
  ]);
});

test("metadata 교체 후 재실행은 transaction 없이 구 객체만 정리한다", async () => {
  const cleanup = plan({
    data: canonicalData(),
    sourceObject: storageObject(sourcePath),
    destinationObject: storageObject(destinationPath),
  });
  const calls = [];
  await applyMigrationPlan(cleanup, {
    copySource: async () => calls.push("copy"),
    getDestination: async () => storageObject(destinationPath),
    updateMetadata: async () => calls.push("transaction"),
    getLatestData: async () => {
      calls.push("latest-data");
      return canonicalData();
    },
    deleteSource: async () => calls.push("delete-source"),
  });
  assert.deepEqual(calls, ["latest-data", "delete-source"]);
});

test("generation 조건부 삭제가 404 no-op을 반환해도 완료 상태를 유지한다", async () => {
  const cleanup = plan({
    data: canonicalData(),
    sourceObject: storageObject(sourcePath),
    destinationObject: storageObject(destinationPath),
  });
  const result = await applyMigrationPlan(cleanup, {
    copySource: async () => assert.fail("복사를 다시 실행하면 안 됩니다."),
    getDestination: async () => storageObject(destinationPath),
    updateMetadata: async () => assert.fail("transaction을 다시 실행하면 안 됩니다."),
    getLatestData: async () => canonicalData(),
    deleteSource: async () => false,
  });
  assert.equal(result.action, "CLEANUP_SOURCE");
});

test("transaction 재검증과 삭제 직전 최신 legal hold가 원본 삭제를 차단한다", async () => {
  const transactionCalls = [];
  await assert.rejects(
      applyMigrationPlan(plan(), {
        copySource: async () => transactionCalls.push("copy"),
        getDestination: async () => storageObject(destinationPath),
        updateMetadata: async (builder) => {
          transactionCalls.push("transaction");
          builder(withLegalHold(legacyData()));
        },
        getLatestData: async () => canonicalData(),
        deleteSource: async () => transactionCalls.push("delete-source"),
      }),
      /transaction 재검증 차단.*legal hold/,
  );
  assert.deepEqual(transactionCalls, ["copy", "transaction"]);

  const deletionCalls = [];
  await assert.rejects(
      applyMigrationPlan(plan(), {
        copySource: async () => deletionCalls.push("copy"),
        getDestination: async () => storageObject(destinationPath),
        updateMetadata: async (builder) => {
          deletionCalls.push("transaction");
          builder(legacyData());
        },
        getLatestData: async () => {
          deletionCalls.push("latest-data");
          return withLegalHold(canonicalData());
        },
        deleteSource: async () => deletionCalls.push("delete-source"),
      }),
      /원본 삭제 직전 재검증 차단.*legal hold/,
  );
  assert.deepEqual(deletionCalls, ["copy", "transaction", "latest-data"]);
});

test("삭제 직전 역할 또는 canonical 경로가 바뀌면 원본을 보존한다", async () => {
  for (const latestData of [
    {...canonicalData(), role: "PATIENT"},
    {
      ...canonicalData(),
      managerDocumentFiles: {
        nursingLicense: {
          ...canonicalData().managerDocumentFiles.nursingLicense,
          fullPath: `manager-documents/${managerId}/nursingLicense/other.png`,
        },
      },
      managerDocumentFilePaths: {
        nursingLicense: `manager-documents/${managerId}/nursingLicense/other.png`,
      },
    },
  ]) {
    const calls = [];
    await assert.rejects(
        applyMigrationPlan(plan(), {
          copySource: async () => calls.push("copy"),
          getDestination: async () => storageObject(destinationPath),
          updateMetadata: async (builder) => {
            calls.push("transaction");
            builder(legacyData());
          },
          getLatestData: async () => latestData,
          deleteSource: async () => calls.push("delete-source"),
        }),
        /원본 삭제 직전 재검증 차단/,
    );
    assert.equal(calls.includes("delete-source"), false);
  }
});
