const test = require("node:test");
const assert = require("node:assert/strict");

const {
  isLegacyManagerLocationEnabled,
} = require("../src/location-feature-policy");

test("기존 매니저 위치 알림은 기본적으로 비활성화된다", () => {
  assert.equal(isLegacyManagerLocationEnabled({}), false);
});

test("개발 환경은 명시적으로 설정한 경우에만 기존 위치 알림을 허용한다", () => {
  assert.equal(isLegacyManagerLocationEnabled({
    configuredValue: "true",
    projectId: "bodeul-dev",
  }), true);
  assert.equal(isLegacyManagerLocationEnabled({
    configuredValue: "false",
    projectId: "bodeul-dev",
  }), false);
});

test("production은 설정값과 무관하게 기존 위치 알림을 차단한다", () => {
  assert.equal(isLegacyManagerLocationEnabled({
    configuredValue: "true",
    projectId: "bodeul-prod-110",
  }), false);
});
