const assert = require("node:assert/strict");
const test = require("node:test");

const {
  buildReadinessDecision,
  summarizeVerificationSeries,
} = require("../lib/app-check-readiness");

test("App Check 검증 메트릭을 서비스와 보안 상태별로 집계한다", () => {
  const summary = summarizeVerificationSeries([
    buildSeries("firestore.googleapis.com", "android-app", "ALLOW", "VALID", [8, 2]),
    buildSeries("firestore.googleapis.com", "UNKNOWN", "ALLOW", "INVALID", [3]),
    buildSeries("firebasestorage.googleapis.com", "web-app", "ALLOW", "VALID", [4]),
  ]);

  assert.equal(summary.totalCount, 17);
  assert.equal(summary.verifiedCount, 14);
  assert.equal(summary.unverifiedCount, 3);
  assert.deepEqual(summary.verifiedAppIds, ["android-app", "web-app"]);
  assert.deepEqual(summary.securityTotals, {VALID: 14, INVALID: 3});
});

test("provider와 valid 요청이 없으면 enforcement 보류로 판단한다", () => {
  const decision = buildReadinessDecision({
    androidApps: [{
      appId: "android-app",
      playIntegrityConfigAvailable: true,
      sha256Count: 1,
      debugTokenCount: 0,
    }],
    webApps: [{appId: "web-app", provider: "none", debugTokenCount: 0}],
    verification: {verifiedAppIds: []},
  });

  assert.equal(decision.status, "HOLD");
  assert.equal(decision.gates.androidProviderReady, true);
  assert.equal(decision.gates.androidDebugReady, false);
  assert.equal(decision.gates.webProviderReady, false);
  assert.equal(decision.gates.verifiedTrafficReady, false);
});

test("각 앱의 provider, debug token, valid 요청이 있으면 Functions 시험 단계로 넘어간다", () => {
  const decision = buildReadinessDecision({
    androidApps: [{
      appId: "android-app",
      playIntegrityConfigAvailable: true,
      sha256Count: 1,
      debugTokenCount: 1,
    }],
    webApps: [{appId: "web-app", provider: "recaptcha_enterprise", debugTokenCount: 1}],
    verification: {verifiedAppIds: ["android-app", "web-app"]},
  });

  assert.equal(decision.status, "READY_FOR_CONTROLLED_FUNCTIONS_TEST");
  assert.deepEqual(decision.blockers, []);
});

function buildSeries(service, appId, result, security, values) {
  return {
    resource: {labels: {service_id: service}},
    metric: {labels: {app_id: appId, result, security}},
    points: values.map((value) => ({value: {int64Value: String(value)}})),
  };
}
