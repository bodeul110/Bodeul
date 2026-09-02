const assert = require("node:assert/strict");
const test = require("node:test");

const {
  buildAppointmentSeedPlan,
  buildAppointmentSeedSql,
  stableUuid,
} = require("../lib/appointment-postgres-seed");

test("예약 요청 백업을 PostgreSQL 예약 projection으로 변환한다", () => {
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

test("무통장입금 생성 상태를 PostgreSQL 예약 projection으로 보존한다", () => {
  const snapshot = createSnapshot();
  const request = snapshot.collections.appointmentRequests[0].data;
  request.paymentMethodCode = "BANK_TRANSFER";
  request.paymentStatusCode = "AWAITING_DEPOSIT";

  const plan = buildAppointmentSeedPlan(snapshot);

  assert.equal(plan.status, "passed");
  assert.equal(plan.rows[0].payment_method_code, "BANK_TRANSFER");
  assert.equal(plan.rows[0].payment_status_code, "AWAITING_DEPOSIT");
});

test("무통장입금 seed 재적용은 기존 projection과 상세 원장이 다르면 중단한다", () => {
  const snapshot = createSnapshot();
  const request = snapshot.collections.appointmentRequests[0].data;
  request.paymentMethodCode = "BANK_TRANSFER";
  request.paymentStatusCode = "AWAITING_DEPOSIT";

  const sql = buildAppointmentSeedSql(buildAppointmentSeedPlan(snapshot));

  assert.match(sql, /for update;/);
  assert.match(sql, /기존 무통장입금 예약이 seed와 달라 재적용을 중단합니다/);
  assert.match(sql, /existing\."payment_status_code" is not distinct from 'AWAITING_DEPOSIT'/);
  assert.match(sql, /payment\.expected_amount is not distinct from existing\.final_price/);
  assert.match(sql, /on conflict \(firestore_id\) do nothing;/);
  assert.doesNotMatch(sql, /on conflict \(firestore_id\) do update set/);
  assert.match(sql, /using errcode = '55000'/);
});

test("이미 전이된 무통장입금 상태는 상세 원장 backfill 없이는 차단한다", () => {
  const transitionedStatuses = [
    "DEPOSIT_CONFIRMED",
    "REVIEW_REQUIRED",
    "REFUND_REQUESTED",
    "REFUNDED",
    "CANCELED",
  ];

  for (const paymentStatusCode of transitionedStatuses) {
    const snapshot = createSnapshot();
    const request = snapshot.collections.appointmentRequests[0].data;
    request.paymentMethodCode = "BANK_TRANSFER";
    request.paymentStatusCode = paymentStatusCode;

    const plan = buildAppointmentSeedPlan(snapshot);

    assert.equal(plan.status, "needs_review", paymentStatusCode);
    assert.ok(plan.errors.some((error) => (
      error.field === "paymentStatusCode"
        && /상세 결제 원장과 이벤트 backfill/.test(error.message)
    )), paymentStatusCode);
    assert.throws(() => buildAppointmentSeedSql(plan), /검증 오류/);
  }
});

test("결제 수단과 상태가 서로 다른 계약이면 SQL 생성을 차단한다", () => {
  const invalidPairs = [
    {paymentMethodCode: "BANK_TRANSFER", paymentStatusCode: "PENDING"},
    {paymentMethodCode: "CARD", paymentStatusCode: "AWAITING_DEPOSIT"},
  ];

  for (const invalidPair of invalidPairs) {
    const snapshot = createSnapshot();
    const request = snapshot.collections.appointmentRequests[0].data;
    request.paymentMethodCode = invalidPair.paymentMethodCode;
    request.paymentStatusCode = invalidPair.paymentStatusCode;

    const plan = buildAppointmentSeedPlan(snapshot);

    assert.equal(plan.status, "needs_review");
    assert.ok(plan.errors.some((error) => (
      error.field === "paymentStatusCode"
        && /결제 수단과 상태 조합/.test(error.message)
    )));
    assert.throws(() => buildAppointmentSeedSql(plan), /검증 오류/);
  }
});

test("현재 계약에 없는 결제 수단과 상태는 SQL 생성을 차단한다", () => {
  const snapshot = createSnapshot();
  const request = snapshot.collections.appointmentRequests[0].data;
  request.paymentMethodCode = "UNKNOWN_METHOD";
  request.paymentStatusCode = "UNKNOWN_STATUS";

  const plan = buildAppointmentSeedPlan(snapshot);

  assert.equal(plan.status, "needs_review");
  assert.ok(plan.errors.some((error) => error.field === "paymentMethodCode"));
  assert.ok(plan.errors.some((error) => error.field === "paymentStatusCode"));
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

test("무통장입금 rollback은 초기 원장만 자식부터 삭제하고 전이 이력이 있으면 중단한다", () => {
  const snapshot = createSnapshot();
  const request = snapshot.collections.appointmentRequests[0].data;
  request.paymentMethodCode = "BANK_TRANSFER";
  request.paymentStatusCode = "AWAITING_DEPOSIT";
  const sql = buildAppointmentSeedSql(
      buildAppointmentSeedPlan(snapshot),
      {rollback: true},
  );

  assert.match(sql, /target\.payment_status_code <> 'AWAITING_DEPOSIT'/);
  assert.match(sql, /payment\.payment_version = 0/);
  assert.match(sql, /event\.event_type = 'CREATED'/);
  assert.match(sql, /select count\(\*\) from bodeul\.appointment_payment_events/);
  assert.match(sql, /결제 전이 또는 추가 이벤트가 있는 무통장입금 예약은 seed rollback할 수 없습니다/);
  assert.match(sql, /using errcode = '55000'/);

  const eventDelete = sql.indexOf("delete from bodeul.appointment_payment_events");
  const paymentDelete = sql.indexOf("delete from bodeul.appointment_bank_transfer_payments");
  const appointmentDelete = sql.indexOf("delete from bodeul.appointment_requests");
  assert.ok(eventDelete >= 0);
  assert.ok(eventDelete < paymentDelete);
  assert.ok(paymentDelete < appointmentDelete);
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
