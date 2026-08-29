const test = require("node:test");
const assert = require("node:assert/strict");
const {Timestamp} = require("firebase-admin/firestore");

const {
  recordManagerDocumentSubmission,
  resolveManagerDocumentSubmissionEvent,
  appendManagerDocumentSubmissionHistory,
  managerDocumentSubmissionContentChanged,
  resolveManagerDocumentEventTime,
} = require("../src/sync");

test("심사 전 상태에서 재심사 대기로 바뀔 때만 제출 이력을 만든다", () => {
  const happenedAt = Timestamp.fromMillis(1_760_000_000_000);
  const event = resolveManagerDocumentSubmissionEvent(
      {role: "MANAGER", managerDocumentStatus: "APPROVED"},
      {
        role: "MANAGER",
        name: "매니저",
        managerDocumentStatus: "PENDING_REVIEW",
        managerDocumentSummary: "재제출 요약",
      },
      "manager-1",
      "event-1",
      happenedAt,
  );

  assert.deepEqual(event, {
    eventId: "event-1",
    eventType: "SUBMITTED",
    happenedAt,
    actorUserId: "manager-1",
    actorName: "매니저 본인",
    summary: "재제출 요약",
    reviewNote: "",
  });
  assert.equal(resolveManagerDocumentSubmissionEvent(
      {role: "MANAGER", managerDocumentStatus: "PENDING_REVIEW"},
      {
        role: "MANAGER",
        managerDocumentStatus: "PENDING_REVIEW",
        managerDocumentHistory: [{eventId: "history-only"}],
      },
      "manager-1",
      "event-2",
      happenedAt,
  ), null);
});

test("심사 대기 중 제출 파일이나 요약이 바뀌면 새 제출 이력을 만든다", () => {
  const happenedAt = Timestamp.fromMillis(1_760_000_000_100);
  const beforeData = {
    role: "MANAGER",
    managerDocumentStatus: "PENDING_REVIEW",
    managerDocumentSummary: "첫 제출",
    managerDocumentFiles: {idCard: {fullPath: "manager-documents/manager-1/idCard/old.jpg"}},
  };
  const afterData = {
    ...beforeData,
    managerDocumentSummary: "수정 제출",
    managerDocumentFiles: {idCard: {fullPath: "manager-documents/manager-1/idCard/new.jpg"}},
  };

  const event = resolveManagerDocumentSubmissionEvent(
      beforeData,
      afterData,
      "manager-1",
      "event-pending-revision",
      happenedAt,
  );

  assert.equal(managerDocumentSubmissionContentChanged(beforeData, afterData), true);
  assert.equal(event.eventId, "event-pending-revision");
  assert.equal(event.happenedAt, happenedAt);
  assert.equal(event.summary, "수정 제출");
});

test("반려 뒤 같은 자료로 다시 요청해도 새 제출 이력을 만든다", () => {
  const happenedAt = Timestamp.fromMillis(1_760_000_000_150);
  const unchangedSubmission = {
    role: "MANAGER",
    managerDocumentSummary: "기존 제출",
    managerDocumentFiles: {idCard: {fullPath: "manager-documents/manager-1/idCard/current.jpg"}},
  };

  const event = resolveManagerDocumentSubmissionEvent(
      {...unchangedSubmission, managerDocumentStatus: "REJECTED"},
      {...unchangedSubmission, managerDocumentStatus: "PENDING_REVIEW"},
      "manager-1",
      "event-requested-again",
      happenedAt,
  );

  assert.equal(event.eventId, "event-requested-again");
  assert.equal(event.happenedAt, happenedAt);
});

test("시간이나 이력만 바뀐 심사 대기 문서는 새 제출 버전으로 보지 않는다", () => {
  const beforeData = {
    role: "MANAGER",
    managerDocumentStatus: "PENDING_REVIEW",
    managerDocumentSummary: "제출 요약",
  };
  const afterData = {
    ...beforeData,
    managerDocumentUpdatedAt: Timestamp.fromMillis(4_102_444_800_000),
    managerDocumentHistory: [{eventId: "server-history"}],
  };

  assert.equal(managerDocumentSubmissionContentChanged(beforeData, afterData), false);
  assert.equal(resolveManagerDocumentSubmissionEvent(
      beforeData,
      afterData,
      "manager-1",
      "event-history-only",
      Timestamp.fromMillis(1_760_000_000_200),
  ), null);
});

test("제출 이력 시각은 Firestore update time을 우선하고 CloudEvent time을 대체값으로 쓴다", () => {
  const updateTime = Timestamp.fromMillis(1_760_000_000_300);

  assert.equal(
      resolveManagerDocumentEventTime(updateTime, "2100-01-01T00:00:00.000Z"),
      updateTime,
  );
  assert.equal(
      resolveManagerDocumentEventTime(undefined, "2026-08-29T09:10:00.000Z").toDate().toISOString(),
      "2026-08-29T09:10:00.000Z",
  );
  assert.throws(
      () => resolveManagerDocumentEventTime(undefined, "invalid"),
      /서버 시각/,
  );
});

test("제출 이력 trigger는 실패 이벤트 재처리를 사용한다", () => {
  assert.equal(recordManagerDocumentSubmission.__endpoint.eventTrigger.retry, true);
});

test("동일 Functions 이벤트는 제출 이력에 한 번만 기록한다", async () => {
  const history = [];
  const snapshot = {
    exists: true,
    get(field) {
      return field === "managerDocumentHistory" ? history : undefined;
    },
  };
  const transaction = {
    async get() {
      return snapshot;
    },
    update(_reference, updates) {
      history.push(...updates.managerDocumentHistory.elements);
    },
  };
  const firestore = {
    async runTransaction(callback) {
      return callback(transaction);
    },
  };
  const submissionEvent = {
    eventId: "event-once",
    eventType: "SUBMITTED",
    happenedAt: Timestamp.fromMillis(1_760_000_000_000),
    actorUserId: "manager-1",
    actorName: "매니저",
    summary: "제출 요약",
    reviewNote: "",
  };

  assert.equal(await appendManagerDocumentSubmissionHistory(firestore, {}, submissionEvent), true);
  assert.equal(await appendManagerDocumentSubmissionHistory(firestore, {}, submissionEvent), false);
  assert.equal(history.length, 1);
});

test("첫 기록 실패 뒤 같은 이벤트를 재처리해도 이력은 정확히 한 번만 남는다", async () => {
  const history = [];
  let transactionAttempts = 0;
  const firestore = {
    async runTransaction(callback) {
      transactionAttempts += 1;
      if (transactionAttempts === 1) {
        throw new Error("temporary failure");
      }
      return callback({
        async get() {
          return {
            exists: true,
            get(field) {
              return field === "managerDocumentHistory" ? history : undefined;
            },
          };
        },
        update(_reference, updates) {
          history.push(...updates.managerDocumentHistory.elements);
        },
      });
    },
  };
  const submissionEvent = {
    eventId: "event-retried",
    eventType: "SUBMITTED",
    happenedAt: Timestamp.fromMillis(1_760_000_000_400),
    actorUserId: "manager-1",
    actorName: "매니저",
    summary: "재처리 제출",
    reviewNote: "",
  };

  await assert.rejects(
      appendManagerDocumentSubmissionHistory(firestore, {}, submissionEvent),
      /temporary failure/,
  );
  assert.equal(await appendManagerDocumentSubmissionHistory(firestore, {}, submissionEvent), true);
  assert.equal(await appendManagerDocumentSubmissionHistory(firestore, {}, submissionEvent), false);
  assert.equal(history.length, 1);
});
