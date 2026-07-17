const assert = require("node:assert/strict");
const test = require("node:test");

const {
  buildSessionParticipantPatch,
  fromFirestoreDocument,
} = require("../lib/session-participant-migration");

test("Firestore REST 문서에서 참가자 필드를 읽는다", () => {
  const document = {
    fields: {
      patientUserId: {stringValue: "patient-1"},
      guardianUserId: {stringValue: "guardian-1"},
    },
  };

  assert.deepEqual(fromFirestoreDocument(document), {
    patientUserId: "patient-1",
    guardianUserId: "guardian-1",
  });
});

test("세션에 없는 참가자 필드만 예약 기준으로 보완한다", () => {
  assert.deepEqual(
      buildSessionParticipantPatch(
          {patientUserId: "patient-1"},
          {patientUserId: "patient-1", guardianUserId: "guardian-1"},
      ),
      {guardianUserId: "guardian-1"},
  );
});

test("참가자 필드가 이미 일치하면 변경하지 않는다", () => {
  assert.deepEqual(
      buildSessionParticipantPatch(
          {patientUserId: "patient-1", guardianUserId: "guardian-1"},
          {patientUserId: "patient-1", guardianUserId: "guardian-1"},
      ),
      {},
  );
});

test("예약 참가자 정보가 비어 있으면 보완을 중단한다", () => {
  assert.throws(
      () => buildSessionParticipantPatch({}, {patientUserId: "patient-1"}),
      /guardianUserId/,
  );
});
