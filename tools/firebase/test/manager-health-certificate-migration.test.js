const test = require("node:test");
const assert = require("node:assert/strict");

const {
  applyMigrationPlan,
  buildClaimDecision,
  buildFinalizeDecision,
  buildFirestoreMutation,
  buildMigrationPlan,
  buildReadyDecision,
  createDeletionClaim,
  parseOptions,
  shouldBlockApply,
  validateDeletionClaim,
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
    managerDocumentEvidenceMigration: {
      migrationId: "health-certificate-to-nursing-license-v1",
      sourceKey: "healthCertificate",
      destinationKey: "nursingLicense",
      sourcePath,
      destinationPath,
    },
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

test("durable claim은 결정적 식별자와 엄격한 CLAIMED/READY schema를 사용한다", () => {
  const claimed = createDeletionClaim(
      managerId,
      sourcePath,
      "2026-08-30T00:00:00.000Z",
  );
  assert.deepEqual(Object.keys(claimed), [
    "version",
    "claimId",
    "operation",
    "documentKey",
    "storagePath",
    "state",
    "claimedAt",
  ]);
  assert.equal(claimed.operation, "MIGRATION");
  assert.equal(claimed.state, "CLAIMED");
  assert.equal(
      claimed.claimId,
      "de4e54727e34cfd1d54ee46ddcc754d55a3a79d1c745e36e82e40f42a3cc822f",
  );
  assert.equal(validateDeletionClaim(claimed, managerId, sourcePath).issue, "");
  assert.equal(
      createDeletionClaim(managerId, sourcePath, claimed.claimedAt).claimId,
      claimed.claimId,
  );

  const ready = {
    ...claimed,
    state: "READY",
    objectGeneration: "7",
  };
  assert.equal(validateDeletionClaim(ready, managerId, sourcePath).issue, "");
  assert.equal(validateDeletionClaim({
    ...claimed,
    state: "READY",
    objectMissing: true,
  }, managerId, sourcePath).issue, "");
  for (const malformed of [
    {...claimed, operation: "RETENTION"},
    {...claimed, claimId: "forged"},
    {...claimed, claimedAt: "not-a-date"},
    {...ready, objectMissing: true},
    {...ready, objectGeneration: "not-a-generation"},
    {...claimed, state: "READY", objectMissing: false},
    {...claimed, unexpected: true},
  ]) {
    assert.notEqual(validateDeletionClaim(malformed, managerId, sourcePath).issue, "");
  }
});

test("claim transaction은 canonical 전환과 claim 생성을 한 mutation으로 묶는다", () => {
  const decision = buildClaimDecision({
    managerId,
    data: legacyData(),
    sourceObject: storageObject(sourcePath),
    destinationObject: storageObject(destinationPath),
    sourcePath,
    destinationPath,
    claimedAt: "2026-08-30T00:00:00.000Z",
  });
  assert.equal(decision.state, "CLAIMED");
  assert.equal(decision.mutation.data.managerDocumentDeletionClaim.claimId,
      decision.claim.claimId);
  assert.equal(
      decision.mutation.data.managerDocumentFiles.nursingLicense.fullPath,
      destinationPath,
  );
  assert.equal(
      "healthCertificate" in decision.mutation.data.managerDocumentFiles,
      false,
  );
});

test("claim 전 legal hold 또는 경로 변경 경쟁은 원본 삭제 전에 차단한다", async () => {
  for (const racedData of [
    withLegalHold(legacyData()),
    legacyData({path: `${sourcePath}.changed`}),
  ]) {
    const harness = createApplyHarness({
      beforeClaim: (state) => {
        state.documentData = clone(racedData);
      },
    });
    await assert.rejects(
        applyMigrationPlan(plan(), harness.dependencies),
        /claim transaction 재검증 차단/,
    );
    assert.equal(harness.calls.includes("delete-source"), false);
    assert.ok(harness.state.sourceObject);
  }
});

test("claim 획득 뒤 READY 직전 hold 또는 재참조 경쟁도 삭제 전에 차단한다", async () => {
  for (const mutateAfterClaim of [
    (data) => withLegalHold(data),
    (data) => ({
      ...data,
      managerDocumentFiles: {
        ...data.managerDocumentFiles,
        healthCertificate: legacyData().managerDocumentFiles.healthCertificate,
      },
      managerDocumentFilePaths: {
        ...data.managerDocumentFilePaths,
        healthCertificate: sourcePath,
      },
      managerHealthCertificateStoragePath: sourcePath,
    }),
  ]) {
    const harness = createApplyHarness({
      beforeReady: (state) => {
        state.documentData = mutateAfterClaim(state.documentData);
      },
    });
    await assert.rejects(
        applyMigrationPlan(plan(), harness.dependencies),
        /READY transaction 재검증 차단/,
    );
    assert.equal(harness.calls.includes("delete-source"), false);
    assert.ok(harness.state.sourceObject);
    assert.equal(
        harness.state.documentData.managerDocumentDeletionClaim.state,
        "CLAIMED",
    );
  }
});

test("apply는 claim, READY generation 고정, 조건부 삭제, finalize 순서를 지킨다", async () => {
  const harness = createApplyHarness();
  await applyMigrationPlan(plan(), harness.dependencies);
  assert.deepEqual(harness.calls, [
    "copy",
    "get-destination",
    "get-destination",
    "get-source",
    "claim-transaction",
    "get-source",
    "ready-transaction",
    "get-destination",
    "delete-source",
    "get-source",
    "finalize-transaction",
  ]);
  assert.equal(harness.deletedGeneration, "7");
  assert.equal(harness.state.sourceObject, null);
  assert.equal("managerDocumentDeletionClaim" in harness.state.documentData, false);
  assert.equal(
      harness.state.documentData.managerDocumentFiles.nursingLicense.fullPath,
      destinationPath,
  );
});

test("READY 뒤 canonical 객체가 바뀌면 원본을 삭제하지 않는다", async () => {
  const harness = createApplyHarness({
    beforeDeleteDestination: (state) => {
      state.destinationObject = storageObject(destinationPath, {generation: "99"});
    },
  });
  await assert.rejects(
      applyMigrationPlan(plan(), harness.dependencies),
      /원본 삭제 직전 canonical 객체 변경 감지/,
  );
  assert.equal(harness.calls.includes("delete-source"), false);
  assert.ok(harness.state.sourceObject);
  assert.equal(
      harness.state.documentData.managerDocumentDeletionClaim.state,
      "READY",
  );
});

test("CLAIMED 중단 후 재실행은 같은 claim을 READY로 승격해 완료한다", async () => {
  const harness = createApplyHarness({failReadyOnce: true});
  await assert.rejects(
      applyMigrationPlan(plan(), harness.dependencies),
      /READY transaction 중단/,
  );
  const claimed = clone(harness.state.documentData.managerDocumentDeletionClaim);
  assert.equal(claimed.state, "CLAIMED");
  assert.ok(harness.state.sourceObject);

  const resumedPlan = buildMigrationPlan({
    managerId,
    data: harness.state.documentData,
    sourceObject: harness.state.sourceObject,
    destinationObject: harness.state.destinationObject,
  });
  assert.equal(resumedPlan.action, "RESUME_CLAIM");
  harness.options.failReadyOnce = false;
  harness.calls.length = 0;
  await applyMigrationPlan(resumedPlan, harness.dependencies);
  assert.equal(harness.claimIds.every((claimId) => claimId === claimed.claimId), true);
  assert.equal("managerDocumentDeletionClaim" in harness.state.documentData, false);
});

test("삭제 후 finalize 중단은 READY claim과 404 상태에서 삭제 없이 재개한다", async () => {
  const harness = createApplyHarness({failFinalizeOnce: true});
  await assert.rejects(
      applyMigrationPlan(plan(), harness.dependencies),
      /finalize transaction 중단/,
  );
  const ready = clone(harness.state.documentData.managerDocumentDeletionClaim);
  assert.equal(ready.state, "READY");
  assert.equal(ready.objectGeneration, "7");
  assert.equal("objectMissing" in ready, false);
  assert.equal(harness.state.sourceObject, null);

  const resumedPlan = buildMigrationPlan({
    managerId,
    data: harness.state.documentData,
    sourceObject: null,
    destinationObject: harness.state.destinationObject,
  });
  assert.equal(resumedPlan.action, "RESUME_CLAIM");
  harness.options.failFinalizeOnce = false;
  harness.calls.length = 0;
  await applyMigrationPlan(resumedPlan, harness.dependencies);
  assert.equal(harness.calls.includes("delete-source"), false);
  assert.equal("managerDocumentDeletionClaim" in harness.state.documentData, false);
});

test("generation 조건부 삭제의 404는 멱등 성공으로 finalize한다", async () => {
  const harness = createApplyHarness({deleteReturns404: true});
  await applyMigrationPlan(plan(), harness.dependencies);
  assert.equal(harness.deletedGeneration, "7");
  assert.equal(harness.state.sourceObject, null);
  assert.equal("managerDocumentDeletionClaim" in harness.state.documentData, false);
});

test("claim 뒤 이미 사라진 원본은 READY objectMissing으로 삭제 호출 없이 finalize한다", async () => {
  const harness = createApplyHarness({sourceMissingAfterClaim: true});
  await applyMigrationPlan(plan(), harness.dependencies);
  assert.equal(harness.calls.includes("delete-source"), false);
  assert.equal(
      harness.readyClaims.some((claim) => claim.objectMissing === true),
      true,
  );
  assert.equal("managerDocumentDeletionClaim" in harness.state.documentData, false);
});

test("상이하거나 손상된 claim과 finalize claim 교체는 fail-closed한다", () => {
  const claimed = createDeletionClaim(
      managerId,
      sourcePath,
      "2026-08-30T00:00:00.000Z",
  );
  const conflicting = {
    ...canonicalData(),
    managerDocumentDeletionClaim: {...claimed, claimId: "forged"},
  };
  assert.equal(plan({
    data: conflicting,
    sourceObject: storageObject(sourcePath),
    destinationObject: storageObject(destinationPath),
  }).action, "BLOCKED");

  const readyDecision = buildReadyDecision({
    managerId,
    data: {...canonicalData(), managerDocumentDeletionClaim: claimed},
    sourceObject: storageObject(sourcePath),
    destinationObject: storageObject(destinationPath),
    sourcePath,
    destinationPath,
    expectedClaim: claimed,
  });
  const changedClaim = {...readyDecision.claim, claimId: "forged"};
  assert.throws(() => buildFinalizeDecision({
    managerId,
    data: {...canonicalData(), managerDocumentDeletionClaim: changedClaim},
    destinationObject: storageObject(destinationPath),
    sourcePath,
    destinationPath,
    expectedClaim: readyDecision.claim,
  }), /재검증 차단|claim/);
});

function createApplyHarness(overrides = {}) {
  const options = {...overrides};
  const state = {
    documentData: clone(overrides.documentData || legacyData()),
    sourceObject: clone(overrides.sourceObject || storageObject(sourcePath)),
    destinationObject: clone(overrides.destinationObject || null),
  };
  const calls = [];
  const claimIds = [];
  const readyClaims = [];
  let deletedGeneration = "";
  let destinationReads = 0;

  function runTransaction(label, builder) {
    calls.push(label);
    const decision = builder(clone(state.documentData));
    if (decision?.claim?.claimId) claimIds.push(decision.claim.claimId);
    if (decision?.claim?.state === "READY") readyClaims.push(clone(decision.claim));
    applyMutation(state.documentData, decision?.mutation);
    return decision;
  }

  const dependencies = {
    claimedAt: "2026-08-30T00:00:00.000Z",
    copySource: async () => {
      calls.push("copy");
      state.destinationObject = storageObject(destinationPath, {generation: "8"});
    },
    getSource: async () => {
      calls.push("get-source");
      return clone(state.sourceObject);
    },
    getDestination: async () => {
      calls.push("get-destination");
      destinationReads += 1;
      if (destinationReads === 3 && options.beforeDeleteDestination) {
        options.beforeDeleteDestination(state);
      }
      return clone(state.destinationObject);
    },
    claimDeletion: async (builder) => {
      if (options.beforeClaim) options.beforeClaim(state);
      const decision = runTransaction("claim-transaction", builder);
      if (options.sourceMissingAfterClaim) state.sourceObject = null;
      return decision;
    },
    prepareDeletion: async (builder) => {
      if (options.failReadyOnce) throw new Error("READY transaction 중단");
      if (options.beforeReady) options.beforeReady(state);
      return runTransaction("ready-transaction", builder);
    },
    deleteSource: async (generation) => {
      calls.push("delete-source");
      deletedGeneration = generation;
      assert.equal(generation, state.sourceObject?.generation);
      state.sourceObject = null;
      return options.deleteReturns404 ? false : true;
    },
    finalizeDeletion: async (builder) => {
      if (options.failFinalizeOnce) throw new Error("finalize transaction 중단");
      return runTransaction("finalize-transaction", builder);
    },
  };
  return {
    calls,
    claimIds,
    readyClaims,
    dependencies,
    options,
    state,
    get deletedGeneration() {
      return deletedGeneration;
    },
  };
}

function applyMutation(data, mutation) {
  if (!mutation) return;
  for (const [key, value] of Object.entries(mutation.data || {})) {
    data[key] = clone(value);
  }
  for (const key of mutation.deleteFields || []) {
    delete data[key];
  }
}

function clone(value) {
  return value === undefined ? undefined : JSON.parse(JSON.stringify(value));
}
