const assert = require("node:assert/strict");
const test = require("node:test");

const {
  MANAGER_DOCUMENT_DELETION_CLAIM_FIELD,
  acquireManagerDocumentDeletionClaim,
  buildManagerDocumentDeletionClaim,
  executeManagerDocumentDeletion,
  parseManagerDocumentDeletionClaim,
  sameManagerDocumentDeletionClaim,
} = require("../src/manager-document-deletion");
const {
  createTransactionalDocument,
} = require("./manager-document-deletion-test-helper");

const CANDIDATE = Object.freeze({
  managerId: "manager-1",
  documentKey: "license",
  storagePath: "manager-documents/manager-1/license/old.jpg",
});
const CLAIMED_AT = new Date("2026-08-31T00:00:00.000Z");

test("삭제 claim ID와 스키마는 대상과 작업에 대해 결정적이다", () => {
  const first = buildManagerDocumentDeletionClaim({
    candidate: CANDIDATE,
    operation: "REPLACEMENT",
    claimedAt: CLAIMED_AT,
  });
  const second = buildManagerDocumentDeletionClaim({
    candidate: CANDIDATE,
    operation: "REPLACEMENT",
    claimedAt: new Date("2026-09-01T00:00:00.000Z"),
  });

  assert.equal(first.claimId, second.claimId);
  assert.equal(first.state, "CLAIMED");
  assert.equal(first.claimedAt, "2026-08-31T00:00:00.000Z");
  assert.deepEqual(parseManagerDocumentDeletionClaim(first), first);
  assert.equal(parseManagerDocumentDeletionClaim({...first, unexpected: true}), null);
  assert.equal(parseManagerDocumentDeletionClaim({...first, claimedAt: "invalid"}), null);
  assert.equal(parseManagerDocumentDeletionClaim({
    ...first,
    state: "READY",
    objectGeneration: 17,
  }), null);
  assert.ok(buildManagerDocumentDeletionClaim({
    candidate: CANDIDATE,
    operation: "MIGRATION",
    claimedAt: CLAIMED_AT,
  }));
});

test("claim 뒤 재참조가 생기면 generation을 고정하거나 Storage를 삭제하지 않는다", async () => {
  const fixture = createTransactionalDocument(
      {role: "MANAGER"},
      {
        beforeTransaction({transactionCount, getData, setData}) {
          if (transactionCount === 2) {
            setData({...getData(), referencedPath: CANDIDATE.storagePath});
          }
        },
      },
  );
  let inspected = 0;
  let deleted = 0;

  await assert.rejects(
      executeManagerDocumentDeletion({
        firestore: fixture.firestore,
        documentReference: fixture.reference,
        candidate: CANDIDATE,
        operation: "REPLACEMENT",
        claimedAt: CLAIMED_AT,
        validateCurrentState: (data) => data.referencedPath ? "REFERENCED" : "",
        storage: {
          async inspectManagerDocument() {
            inspected += 1;
            return {objectGeneration: "7"};
          },
          async deleteManagerDocument() {
            deleted += 1;
          },
        },
      }),
      (error) => error.code === "MANAGER_DOCUMENT_CLAIM_STATE_CHANGED",
  );

  assert.equal(inspected, 1);
  assert.equal(deleted, 0);
  assert.equal(
      fixture.getData()[MANAGER_DOCUMENT_DELETION_CLAIM_FIELD].state,
      "CLAIMED",
  );
});

test("Storage 실패는 READY claim을 남기고 다음 실행이 같은 generation으로 재시도한다", async () => {
  const fixture = createTransactionalDocument({role: "MANAGER"});
  let inspected = 0;
  let deleteAttempt = 0;
  const generations = [];
  const storage = {
    async inspectManagerDocument() {
      inspected += 1;
      return {objectGeneration: "17"};
    },
    async deleteManagerDocument(_path, _managerId, _documentKey, generation) {
      deleteAttempt += 1;
      generations.push(generation);
      if (deleteAttempt === 1) {
        throw new Error("storage unavailable");
      }
    },
  };

  await assert.rejects(
      executeManagerDocumentDeletion({
        firestore: fixture.firestore,
        documentReference: fixture.reference,
        candidate: CANDIDATE,
        operation: "RETENTION",
        claimedAt: CLAIMED_AT,
        validateCurrentState: () => "",
        storage,
      }),
      /storage unavailable/,
  );
  const readyClaim = fixture.getData()[MANAGER_DOCUMENT_DELETION_CLAIM_FIELD];
  assert.equal(readyClaim.state, "READY");
  assert.equal(readyClaim.objectGeneration, "17");

  const result = await executeManagerDocumentDeletion({
    firestore: fixture.firestore,
    documentReference: fixture.reference,
    candidate: CANDIDATE,
    operation: "RETENTION",
    claimedAt: new Date("2026-09-01T00:00:00.000Z"),
    validateCurrentState: () => "",
    storage,
  });

  assert.equal(result.status, "COMPLETED");
  assert.equal(inspected, 1);
  assert.deepEqual(generations, ["17", "17"]);
  assert.equal(
      Object.hasOwn(fixture.getData(), MANAGER_DOCUMENT_DELETION_CLAIM_FIELD),
      false,
  );
});

test("다른 현재 claim은 새 대상의 검사와 삭제를 모두 차단한다", async () => {
  const currentClaim = buildManagerDocumentDeletionClaim({
    candidate: {...CANDIDATE, storagePath: "manager-documents/manager-1/license/other.jpg"},
    operation: "REPLACEMENT",
    claimedAt: CLAIMED_AT,
  });
  const fixture = createTransactionalDocument({
    role: "MANAGER",
    [MANAGER_DOCUMENT_DELETION_CLAIM_FIELD]: currentClaim,
  });
  let storageCalls = 0;

  const result = await executeManagerDocumentDeletion({
    firestore: fixture.firestore,
    documentReference: fixture.reference,
    candidate: CANDIDATE,
    operation: "REPLACEMENT",
    claimedAt: CLAIMED_AT,
    validateCurrentState: () => "",
    storage: {
      async inspectManagerDocument() {
        storageCalls += 1;
        return {objectGeneration: "1"};
      },
      async deleteManagerDocument() {
        storageCalls += 1;
      },
    },
  });

  assert.equal(result.status, "CLAIM_CONFLICT");
  assert.equal(storageCalls, 0);
  assert.equal(
      sameManagerDocumentDeletionClaim(
          fixture.getData()[MANAGER_DOCUMENT_DELETION_CLAIM_FIELD],
          currentClaim,
      ),
      true,
  );
});

test("부적합해진 CLAIMED claim만 transaction에서 해제하고 READY는 보존한다", async () => {
  const claimed = buildManagerDocumentDeletionClaim({
    candidate: CANDIDATE,
    operation: "REPLACEMENT",
    claimedAt: CLAIMED_AT,
  });
  const claimedFixture = createTransactionalDocument({
    role: "MANAGER",
    referencedPath: CANDIDATE.storagePath,
    [MANAGER_DOCUMENT_DELETION_CLAIM_FIELD]: claimed,
  });

  const released = await acquireManagerDocumentDeletionClaim({
    firestore: claimedFixture.firestore,
    documentReference: claimedFixture.reference,
    requestedClaim: buildManagerDocumentDeletionClaim({
      candidate: CANDIDATE,
      operation: "REPLACEMENT",
      claimedAt: new Date("2026-09-01T00:00:00.000Z"),
    }),
    validateCurrentState: (data) => data.referencedPath ? "REFERENCED" : "",
  });

  assert.equal(released.status, "CLAIM_RELEASED");
  assert.equal(released.reason, "REFERENCED");
  assert.equal(
      Object.hasOwn(
          claimedFixture.getData(),
          MANAGER_DOCUMENT_DELETION_CLAIM_FIELD,
      ),
      false,
  );

  const ready = {...claimed, state: "READY", objectGeneration: "17"};
  const readyFixture = createTransactionalDocument({
    role: "MANAGER",
    referencedPath: CANDIDATE.storagePath,
    [MANAGER_DOCUMENT_DELETION_CLAIM_FIELD]: ready,
  });
  const blocked = await acquireManagerDocumentDeletionClaim({
    firestore: readyFixture.firestore,
    documentReference: readyFixture.reference,
    requestedClaim: claimed,
    validateCurrentState: (data) => data.referencedPath ? "REFERENCED" : "",
  });

  assert.equal(blocked.status, "CLAIM_BLOCKED");
  assert.equal(blocked.reason, "REFERENCED");
  assert.deepEqual(
      readyFixture.getData()[MANAGER_DOCUMENT_DELETION_CLAIM_FIELD],
      ready,
  );
});

test("CLAIMED 해제와 READY 바인드가 경합하면 READY를 해제하지 않는다", async () => {
  const claimed = buildManagerDocumentDeletionClaim({
    candidate: CANDIDATE,
    operation: "REPLACEMENT",
    claimedAt: CLAIMED_AT,
  });
  const ready = {...claimed, state: "READY", objectGeneration: "23"};
  const fixture = createTransactionalDocument(
      {
        role: "MANAGER",
        referencedPath: CANDIDATE.storagePath,
        [MANAGER_DOCUMENT_DELETION_CLAIM_FIELD]: claimed,
      },
      {
        beforeTransaction({setData}) {
          setData({
            role: "MANAGER",
            referencedPath: CANDIDATE.storagePath,
            [MANAGER_DOCUMENT_DELETION_CLAIM_FIELD]: ready,
          });
        },
      },
  );

  const result = await acquireManagerDocumentDeletionClaim({
    firestore: fixture.firestore,
    documentReference: fixture.reference,
    requestedClaim: claimed,
    validateCurrentState: (data) => data.referencedPath ? "REFERENCED" : "",
  });

  assert.equal(result.status, "CLAIM_BLOCKED");
  assert.deepEqual(
      fixture.getData()[MANAGER_DOCUMENT_DELETION_CLAIM_FIELD],
      ready,
  );
});

test("이미 사라진 객체도 READY claim으로 고정한 뒤 참조와 claim을 정리한다", async () => {
  const fixture = createTransactionalDocument({role: "MANAGER", retained: true});
  let deleted = 0;

  const result = await executeManagerDocumentDeletion({
    firestore: fixture.firestore,
    documentReference: fixture.reference,
    candidate: CANDIDATE,
    operation: "RETENTION",
    claimedAt: CLAIMED_AT,
    validateCurrentState: (data) => data.retained ? "" : "NOT_RETAINED",
    buildFinalizeUpdates: () => ({retained: false}),
    storage: {
      async inspectManagerDocument() {
        return {objectMissing: true};
      },
      async deleteManagerDocument() {
        deleted += 1;
      },
    },
  });

  assert.equal(result.status, "COMPLETED");
  assert.equal(result.objectMissing, true);
  assert.equal(deleted, 0);
  assert.equal(fixture.getData().retained, false);
  assert.equal(
      Object.hasOwn(fixture.getData(), MANAGER_DOCUMENT_DELETION_CLAIM_FIELD),
      false,
  );
});

test("삭제 뒤 claim이 바뀌면 finalize를 거부하고 현재 claim을 보존한다", async () => {
  const fixture = createTransactionalDocument(
      {role: "MANAGER"},
      {
        beforeTransaction({transactionCount, getData, setData}) {
          if (transactionCount === 3) {
            const current = getData()[MANAGER_DOCUMENT_DELETION_CLAIM_FIELD];
            setData({
              ...getData(),
              [MANAGER_DOCUMENT_DELETION_CLAIM_FIELD]: {
                ...current,
                claimId: "f".repeat(64),
              },
            });
          }
        },
      },
  );
  let deleted = 0;

  await assert.rejects(
      executeManagerDocumentDeletion({
        firestore: fixture.firestore,
        documentReference: fixture.reference,
        candidate: CANDIDATE,
        operation: "REPLACEMENT",
        claimedAt: CLAIMED_AT,
        validateCurrentState: () => "",
        storage: {
          async inspectManagerDocument() {
            return {objectGeneration: "29"};
          },
          async deleteManagerDocument() {
            deleted += 1;
          },
        },
      }),
      (error) => error.code === "MANAGER_DOCUMENT_FINALIZE_CLAIM_CHANGED",
  );

  assert.equal(deleted, 1);
  assert.equal(
      fixture.getData()[MANAGER_DOCUMENT_DELETION_CLAIM_FIELD].claimId,
      "f".repeat(64),
  );
});

test("Storage 삭제 뒤 finalize가 중단되면 같은 generation으로 재개한다", async () => {
  let finalizeFailed = false;
  const fixture = createTransactionalDocument(
      {role: "MANAGER"},
      {
        beforeTransaction({transactionCount}) {
          if (transactionCount === 3 && !finalizeFailed) {
            finalizeFailed = true;
            throw new Error("firestore temporarily unavailable");
          }
        },
      },
  );
  let inspected = 0;
  const generations = [];
  const storage = {
    async inspectManagerDocument() {
      inspected += 1;
      return {objectGeneration: "31"};
    },
    async deleteManagerDocument(_path, _managerId, _documentKey, generation) {
      generations.push(generation);
    },
  };

  await assert.rejects(
      executeManagerDocumentDeletion({
        firestore: fixture.firestore,
        documentReference: fixture.reference,
        candidate: CANDIDATE,
        operation: "REPLACEMENT",
        claimedAt: CLAIMED_AT,
        validateCurrentState: () => "",
        storage,
      }),
      /firestore temporarily unavailable/,
  );
  assert.equal(
      fixture.getData()[MANAGER_DOCUMENT_DELETION_CLAIM_FIELD].state,
      "READY",
  );

  const result = await executeManagerDocumentDeletion({
    firestore: fixture.firestore,
    documentReference: fixture.reference,
    candidate: CANDIDATE,
    operation: "REPLACEMENT",
    claimedAt: new Date("2026-09-01T00:00:00.000Z"),
    validateCurrentState: () => "",
    storage,
  });

  assert.equal(result.status, "COMPLETED");
  assert.equal(inspected, 1);
  assert.deepEqual(generations, ["31", "31"]);
  assert.equal(
      Object.hasOwn(fixture.getData(), MANAGER_DOCUMENT_DELETION_CLAIM_FIELD),
      false,
  );
});

test("generation 불일치는 READY claim을 유지하고 같은 generation만 재시도한다", async () => {
  const claimed = buildManagerDocumentDeletionClaim({
    candidate: CANDIDATE,
    operation: "RETENTION",
    claimedAt: CLAIMED_AT,
  });
  const ready = {...claimed, state: "READY", objectGeneration: "43"};
  const fixture = createTransactionalDocument({
    role: "MANAGER",
    [MANAGER_DOCUMENT_DELETION_CLAIM_FIELD]: ready,
  });
  let inspected = 0;
  const generations = [];
  const storage = {
    async inspectManagerDocument() {
      inspected += 1;
      return {objectGeneration: "44"};
    },
    async deleteManagerDocument(_path, _managerId, _documentKey, generation) {
      generations.push(generation);
      const error = new Error("generation mismatch");
      error.code = 412;
      throw error;
    },
  };

  for (const claimedAt of [
    CLAIMED_AT,
    new Date("2026-09-01T00:00:00.000Z"),
  ]) {
    await assert.rejects(
        executeManagerDocumentDeletion({
          firestore: fixture.firestore,
          documentReference: fixture.reference,
          candidate: CANDIDATE,
          operation: "RETENTION",
          claimedAt,
          validateCurrentState: () => "",
          storage,
        }),
        (error) => error.code === 412,
    );
  }

  assert.equal(inspected, 0);
  assert.deepEqual(generations, ["43", "43"]);
  assert.deepEqual(
      fixture.getData()[MANAGER_DOCUMENT_DELETION_CLAIM_FIELD],
      ready,
  );
});
