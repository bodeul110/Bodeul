const test = require("node:test");
const assert = require("node:assert/strict");

const {
  buildSessionSeedPlan,
  buildSessionSeedSql,
} = require("../lib/session-postgres-seed");

test("동행 세션, 리포트, 후속 처리의 FK와 상태 코드를 검증한다", () => {
  const plan = buildSessionSeedPlan(snapshot());

  assert.equal(plan.status, "passed");
  assert.deepEqual(plan.rowCounts, {
    companion_sessions: 1,
    session_reports: 1,
    appointment_follow_ups: 1,
  });
  assert.equal(plan.errors.length, 0);
  assert.equal(plan.rows.companion_sessions[0].current_status, "READY");
  assert.equal(plan.rows.session_reports[0].medication_comparison_decision_code, "MATCHED");
  assert.equal(plan.rows.appointment_follow_ups[0].review_rating_code, "good");
});

test("참조가 없거나 상태 코드가 잘못되면 SQL을 만들지 않는다", () => {
  const invalid = snapshot();
  invalid.collections.companionSessions[0].data.appointmentRequestId = "missing-request";
  invalid.collections.companionSessions[0].data.currentStatus = "UNKNOWN";

  const plan = buildSessionSeedPlan(invalid);

  assert.equal(plan.status, "needs_review");
  assert.equal(plan.errors.length, 2);
  assert.throws(() => buildSessionSeedSql(plan), /검증 오류 2건/);
});

test("적용 SQL은 migration role과 schema-qualified upsert를 사용한다", () => {
  const sql = buildSessionSeedSql(buildSessionSeedPlan(snapshot()));

  assert.match(sql, /set local role bodeul_migration;/);
  assert.match(sql, /insert into bodeul\.companion_sessions/);
  assert.match(sql, /on conflict \("firestore_id"\) do update/);
  assert.match(sql, /insert into bodeul\.session_reports/);
  assert.match(sql, /"next_visit_note"/);
  assert.match(sql, /'다음 방문 일정은 보호자와 협의'/);
  assert.match(sql, /insert into bodeul\.appointment_follow_ups/);
  assert.match(sql, /"imported_at"\)\nvalues \(.+now\(\)\)/s);
  assert.match(sql, /"imported_at" = now\(\)/);
  assert.match(sql, /commit;\s*$/);
});

test("rollback SQL은 리포트와 후속 처리 후 세션 순서로 삭제한다", () => {
  const sql = buildSessionSeedSql(buildSessionSeedPlan(snapshot()), {rollback: true});

  const reportIndex = sql.indexOf("delete from bodeul.session_reports");
  const followUpIndex = sql.indexOf("delete from bodeul.appointment_follow_ups");
  const sessionIndex = sql.indexOf("delete from bodeul.companion_sessions");
  assert.ok(reportIndex >= 0);
  assert.ok(followUpIndex > reportIndex);
  assert.ok(sessionIndex > followUpIndex);
});

function snapshot() {
  return {
    collections: {
      users: [
        document("users", "manager-1", {}),
        document("users", "patient-1", {}),
        document("users", "admin-1", {}),
      ],
      appointmentRequests: [document("appointmentRequests", "request-1", {})],
      companionSessions: [document("companionSessions", "session-1", {
        appointmentRequestId: "request-1",
        managerUserId: "manager-1",
        currentStepOrder: 0,
        currentStatus: "READY",
        guardianUpdate: "대기 중",
        createdAt: "2026-07-18T00:00:00Z",
        updatedAt: "2026-07-18T00:01:00Z",
      })],
      sessionReports: [document("sessionReports", "report-1", {
        sessionId: "session-1",
        summary: "진료 완료",
        medicationComparisonDecisionCode: "MATCHED",
        nextVisitAt: "다음 방문 일정은 보호자와 협의",
        createdAt: "2026-07-18T01:00:00Z",
      })],
      appointmentFollowUps: [document("appointmentFollowUps", "request-1", {
        requestId: "request-1",
        reviewRatingCode: "good",
        reviewSavedByUserId: "patient-1",
        reviewSavedAt: "2026-07-18T02:00:00Z",
        settlementFollowUpStatus: "CONFIRMED",
        supportEscalationStatus: "",
        updatedAt: "2026-07-18T02:00:00Z",
      })],
    },
  };
}

function document(collection, id, data) {
  return {id, path: `${collection}/${id}`, data};
}
