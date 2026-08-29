"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  canUsePatientRequesterPhoneFallback,
  resolveLegacyChatRecipientUserIds,
  resolveLegacyLocationRecipientUserIds,
  resolveLegacyReminderRecipientUserIds,
} = require("../src/guardian-delivery-policy");

const request = {
  patientUserId: "patient-1",
  guardianUserId: "guardian-1",
  requesterUserId: "guardian-1",
  requesterRole: "GUARDIAN",
};

test("레거시 채팅 알림은 보호자를 수신자로 추가하지 않는다", () => {
  assert.deepEqual(
      resolveLegacyChatRecipientUserIds("MANAGER", request, "manager-1"),
      ["patient-1"],
  );
  assert.deepEqual(
      resolveLegacyChatRecipientUserIds("PATIENT", request, "manager-1"),
      ["manager-1"],
  );
});

test("레거시 위치와 예약 알림은 환자에게만 발송한다", () => {
  assert.deepEqual(resolveLegacyLocationRecipientUserIds(request), ["patient-1"]);
  assert.deepEqual(resolveLegacyReminderRecipientUserIds(request), ["patient-1"]);
});

test("보호자 요청자 연락처는 환자 예약 알림 대체값으로 사용하지 않는다", () => {
  assert.equal(canUsePatientRequesterPhoneFallback(request), false);
  assert.equal(canUsePatientRequesterPhoneFallback({
    ...request,
    requesterUserId: "patient-1",
    requesterRole: "PATIENT",
  }), true);
});
