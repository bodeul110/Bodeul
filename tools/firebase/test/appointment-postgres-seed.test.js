const assert = require("node:assert/strict");
const test = require("node:test");

const {
  buildAppointmentSeedPlan,
  buildAppointmentSeedSql,
  stableUuid,
} = require("../lib/appointment-postgres-seed");

test("예약 요청 백업을 private schema row로 변환한다", () => {
  const plan = buildAppointmentSeedPlan(createSnapshot());

  assert.equal(plan.status, "passed");
  assert.equal(plan.rowCount, 1);
  assert.equal(plan.rows[0].id, stableUuid("appointment_requests", "request-1"));
  assert.equal(plan.rows[0].requester_user_id, stableUuid("app_users", "guardian-1"));
  assert.equal(plan.rows[0].patient_name, "O'Brien");
  assert.deepEqual(plan.rows[0].reminder_stages, ["BEFORE_24_HOURS"]);
});

test("users 백업에 없는 참조가 있으면 SQL 생성을 차단한다", () => {
  const snapshot = createSnapshot();
  snapshot.collections.appointmentRequests[0].data.managerUserId = "manager-missing";
  const plan = buildAppointmentSeedPlan(snapshot);

  assert.equal(plan.status, "needs_review");
  assert.ok(plan.errors.some((error) => error.field === "managerUserId"));
  assert.throws(() => buildAppointmentSeedSql(plan), /검증 오류/);
});

test("apply SQL은 schema, migration role, upsert와 JSONB 계약을 고정한다", () => {
  const sql = buildAppointmentSeedSql(buildAppointmentSeedPlan(createSnapshot()));

  assert.match(sql, /set local role bodeul_migration;/);
  assert.match(sql, /insert into bodeul\.appointment_requests/);
  assert.match(sql, /on conflict \(firestore_id\) do update set/);
  assert.match(sql, /O''Brien/);
  assert.match(sql, /::jsonb/);
  assert.doesNotMatch(sql, /insert into "appointment_requests"/);
});

test("rollback SQL은 해당 백업의 Firestore 문서 ID만 삭제한다", () => {
  const sql = buildAppointmentSeedSql(
      buildAppointmentSeedPlan(createSnapshot()),
      {rollback: true},
  );

  assert.match(sql, /delete from bodeul\.appointment_requests/);
  assert.match(sql, /where firestore_id in \('request-1'\);/);
  assert.doesNotMatch(sql, /drop table/);
});

function createSnapshot() {
  return {
    schemaVersion: 1,
    projectId: "bodeul-test",
    generatedAt: "2026-07-17T00:00:00.000Z",
    collections: {
      users: [
        {id: "patient-1", data: {role: "PATIENT"}},
        {id: "guardian-1", data: {role: "GUARDIAN"}},
        {id: "manager-1", data: {role: "MANAGER"}},
      ],
      appointmentRequests: [
        {
          id: "request-1",
          path: "appointmentRequests/request-1",
          data: {
            patientUserId: "patient-1",
            guardianUserId: "guardian-1",
            managerUserId: "manager-1",
            requesterUserId: "guardian-1",
            requesterRole: "GUARDIAN",
            patientName: "O'Brien",
            patientPhone: "010-0000-0000",
            patientEmail: "patient@example.com",
            guardianName: "Guardian",
            guardianPhone: "010-1111-1111",
            guardianEmail: "guardian@example.com",
            requesterName: "Guardian",
            requesterPhone: "010-1111-1111",
            hospitalName: "Test Hospital",
            departmentName: "Internal Medicine",
            hospitalLatitude: 37.5,
            hospitalLongitude: 127.0,
            appointmentAt: "2026-07-20T01:00:00.000Z",
            appointmentAtEpochMillis: 1784509200000,
            appointmentDateKey: "2026-07-20",
            meetingPlace: "Lobby",
            specialNotes: "",
            patientConditionSummary: "",
            medicationSummary: "",
            mobilitySupportCode: "INDEPENDENT",
            tripTypeCode: "ROUND_TRIP",
            managerGenderPreferenceCode: "ANY",
            status: "REQUESTED",
            basePrice: 100000,
            optionSurchargePrice: 22000,
            couponDiscountPrice: 5000,
            finalPrice: 117000,
            paymentMethodCode: "CARD",
            couponCode: "FIRST_VISIT",
            paymentStatusCode: "PENDING",
            paymentApprovalCode: "",
            paymentApprovedAt: "",
            paymentProviderLabel: "",
            reminderStages: ["BEFORE_24_HOURS"],
            createdAt: "2026-07-17T00:00:00.000Z",
            updatedAt: "2026-07-17T00:05:00.000Z",
          },
        },
      ],
    },
  };
}
