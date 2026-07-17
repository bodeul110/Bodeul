const assert = require("node:assert/strict");
const test = require("node:test");

const {
  quoteAndroidShellArgument,
} = require("../lib/android-toolkit");

test("ADB 원격 셸에서 공백이 있는 자동화 문구를 한 인자로 유지한다", () => {
  assert.equal(
      quoteAndroidShellArgument("실기기 첨부 검증"),
      "'실기기 첨부 검증'",
  );
});

test("ADB 원격 셸 인자 안의 작은따옴표를 안전하게 이스케이프한다", () => {
  assert.equal(
      quoteAndroidShellArgument("보호자's 확인"),
      "'보호자'\\''s 확인'",
  );
});
