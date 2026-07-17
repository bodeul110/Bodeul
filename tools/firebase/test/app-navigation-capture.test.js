const assert = require("node:assert/strict");
const test = require("node:test");

const {
  DEFAULT_CAPTURE_SETTLE_MILLIS,
  matchesActivity,
  resolveDurationOption,
  resolveStatus,
} = require("../capture-app-navigation-evidence");

const preset = {
  expectedActivity: "com.example.bodeul/com.example.bodeul.ui.booking.BookingStatusActivity",
};

test("Android 16의 축약 액티비티 표기를 프리셋과 일치시킨다", () => {
  assert.equal(
      matchesActivity(
          "com.example.bodeul/.ui.booking.BookingStatusActivity",
          preset.expectedActivity,
      ),
      true,
  );
});

test("프리셋 캡처는 수동 통과값보다 실제 포커스를 우선한다", () => {
  assert.equal(
      resolveStatus(
          "passed",
          preset,
          "com.example.bodeul/.debug.AutomationEntryActivity",
      ),
      "failed",
  );
});

test("프리셋이 없는 수동 캡처는 지정한 상태를 유지한다", () => {
  assert.equal(resolveStatus("warning", null, ""), "warning");
});

test("화면 캡처는 대상 액티비티 확인 뒤 기본 3초를 기다린다", () => {
  assert.equal(DEFAULT_CAPTURE_SETTLE_MILLIS, 3000);
  assert.equal(
      resolveDurationOption("", DEFAULT_CAPTURE_SETTLE_MILLIS, "--capture-settle-ms"),
      3000,
  );
});

test("화면 안정화 대기 시간은 0 이상인 값만 허용한다", () => {
  assert.equal(resolveDurationOption("0", 3000, "--capture-settle-ms"), 0);
  assert.throws(
      () => resolveDurationOption("-1", 3000, "--capture-settle-ms"),
      /0 이상의 숫자/,
  );
});
