const assert = require("node:assert/strict");
const test = require("node:test");

const {
  buildDiffSummary,
  buildFirestoreBaselineStatuses,
  buildRoleReadiness,
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

test("완료 후속 준비상태는 긴급 이슈 없이 후기와 정산만 요구한다", () => {
  const users = [
    document("admin-id", {email: "admin@bodeul.app", role: "ADMIN"}),
    document("patient-id", {email: "patient@bodeul.app", role: "PATIENT"}),
    document("guardian-id", {email: "guardian@bodeul.app", role: "GUARDIAN"}),
    document("manager-id", {
      email: "manager@bodeul.app",
      role: "MANAGER",
      managerDocumentStatus: "APPROVED",
    }),
  ];
  const collections = {
    users,
    hospitalGuides: [],
    appointmentRequests: [document("request-seed-completed", {
      patientUserId: "patient-id",
      guardianUserId: "guardian-id",
      managerUserId: "manager-id",
      status: "COMPLETED",
    })],
    companionSessions: [document("session-completed", {
      appointmentRequestId: "request-seed-completed",
      managerUserId: "manager-id",
    })],
    sessionReports: [document("report-completed", {sessionId: "session-completed"})],
    appointmentFollowUps: [document("request-seed-completed", {
      requestId: "request-seed-completed",
      reviewRatingCode: "good",
    })],
    adminSettlementRecords: [document("request-seed-completed", {
      requestId: "request-seed-completed",
      status: "NEEDS_REVIEW",
    })],
    adminActionNotifications: [document("notification-completed", {
      requestId: "request-seed-completed",
    })],
    adminActionDeliveries: [document("delivery-completed", {
      requestId: "request-seed-completed",
    })],
  };
  const snapshot = {
    verificationScope: "firestore_only",
    baselineStatuses: buildFirestoreBaselineStatuses(users),
    collections,
  };

  const readiness = buildRoleReadiness(snapshot);
  const admin = readiness.roles.find((role) => role.role === "ADMIN");
  const followUpCheck = admin.checks.find((check) => check.id === "follow_up");
  const completedScenario = readiness.scenarios.find((scenario) => scenario.id === "completed");

  assert.equal(followUpCheck.pass, true);
  assert.equal(completedScenario.pass, true);
  assert.match(completedScenario.detail, /legacyEmergency=false/);

  collections.adminEmergencyIssues = [document("request-seed-completed", {
    requestId: "request-seed-completed",
    status: "REPORTED",
  })];
  const legacyReadiness = buildRoleReadiness(snapshot);
  const legacyCompletedScenario = legacyReadiness.scenarios
      .find((scenario) => scenario.id === "completed");

  assert.equal(legacyCompletedScenario.pass, true);
  assert.match(legacyCompletedScenario.detail, /legacyEmergency=true/);
});

function document(id, data) {
  return {id, path: `test/${id}`, data};
}
