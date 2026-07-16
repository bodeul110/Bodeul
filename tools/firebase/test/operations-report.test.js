const assert = require("node:assert/strict");
const test = require("node:test");

const {
  buildDiffSummary,
  buildFirestoreBaselineStatuses,
} = require("../lib/operations-report");

test("Firestore 전용 기준선은 users 문서로 UID를 찾고 Auth를 미검증으로 표시한다", () => {
  const statuses = buildFirestoreBaselineStatuses([
    {
      id: "patient-user-id",
      data: {
        email: "patient@bodeul.app",
        role: "PATIENT",
      },
    },
  ]);

  const patient = statuses.find((status) => status.role === "PATIENT");
  const guardian = statuses.find((status) => status.role === "GUARDIAN");

  assert.equal(patient.uid, "patient-user-id");
  assert.equal(patient.authStatus, "not_checked");
  assert.equal(patient.userDocumentStatus, "present");
  assert.equal(guardian.uid, "");
  assert.equal(guardian.authStatus, "not_checked");
  assert.equal(guardian.userDocumentStatus, "missing");
});

test("workflow diff는 timestamp를 재인코딩하지 않고 원본 Firestore field를 비교한다", () => {
  const fields = {
    createdAt: {timestampValue: "2026-07-16T09:00:00.000Z"},
    count: {integerValue: "1"},
  };
  const baseSnapshot = {
    collections: {
      users: [{path: "users/example", fields}],
    },
  };
  const currentSnapshot = {
    collections: {
      users: [{
        path: "users/example",
        fields,
        data: {createdAt: "2026-07-16T09:00:00.000Z", count: 1},
      }],
    },
  };

  const diff = buildDiffSummary(baseSnapshot, currentSnapshot);
  assert.equal(diff.totalAdded, 0);
  assert.equal(diff.totalRemoved, 0);
  assert.equal(diff.totalChanged, 0);
});
