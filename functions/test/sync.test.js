const test = require("node:test");
const assert = require("node:assert/strict");
const {Timestamp} = require("firebase-admin/firestore");

const {
  resolveManagerDocumentSubmissionEvent,
  appendManagerDocumentSubmissionHistory,
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
      {role: "MANAGER", managerDocumentStatus: "PENDING_REVIEW"},
      "manager-1",
      "event-2",
      happenedAt,
  ), null);
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
