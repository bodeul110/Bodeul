"use strict";

const VERIFIED_SECURITY_STATES = new Set(["VALID", "CONSUMED"]);

function summarizeVerificationSeries(timeSeries) {
  const rows = [];
  const securityTotals = {};
  const verifiedAppIds = new Set();
  let totalCount = 0;
  let verifiedCount = 0;

  for (const series of Array.isArray(timeSeries) ? timeSeries : []) {
    const count = sumSeriesPoints(series?.points);
    const security = sanitizeText(series?.metric?.labels?.security) || "UNKNOWN";
    const result = sanitizeText(series?.metric?.labels?.result) || "UNKNOWN";
    const appId = sanitizeText(series?.metric?.labels?.app_id) || "UNKNOWN";
    const service = sanitizeText(series?.resource?.labels?.service_id) || "UNKNOWN";

    rows.push({service, appId, result, security, count});
    securityTotals[security] = (securityTotals[security] || 0) + count;
    totalCount += count;

    if (result === "ALLOW" && VERIFIED_SECURITY_STATES.has(security)) {
      verifiedCount += count;
      if (appId !== "UNKNOWN") {
        verifiedAppIds.add(appId);
      }
    }
  }

  rows.sort((left, right) => [left.service, left.appId, left.result, left.security]
      .join("|")
      .localeCompare([right.service, right.appId, right.result, right.security].join("|")));

  return {
    rows,
    securityTotals,
    totalCount,
    verifiedCount,
    unverifiedCount: totalCount - verifiedCount,
    verifiedAppIds: [...verifiedAppIds].sort(),
  };
}

function buildReadinessDecision({androidApps, webApps, verification}) {
  const safeAndroidApps = Array.isArray(androidApps) ? androidApps : [];
  const safeWebApps = Array.isArray(webApps) ? webApps : [];
  const verifiedAppIds = new Set(verification?.verifiedAppIds || []);
  const blockers = [];

  const androidProviderReady = safeAndroidApps.length > 0 && safeAndroidApps.every((app) =>
    app.playIntegrityConfigAvailable && app.sha256Count > 0);
  const androidDebugReady = safeAndroidApps.length > 0 && safeAndroidApps.every((app) =>
    app.debugTokenCount > 0);
  const webProviderReady = safeWebApps.length > 0 && safeWebApps.every((app) =>
    app.provider !== "none");
  const webDebugReady = safeWebApps.length > 0 && safeWebApps.every((app) =>
    app.debugTokenCount > 0);
  const registeredApps = [...safeAndroidApps, ...safeWebApps];
  const verifiedTrafficReady = registeredApps.length > 0 && registeredApps.every((app) =>
    verifiedAppIds.has(app.appId));

  if (!androidProviderReady) {
    blockers.push("Android Play Integrity 설정 또는 SHA-256 등록이 완료되지 않았습니다.");
  }
  if (!androidDebugReady) {
    blockers.push("Android debug token allowlist가 비어 있습니다.");
  }
  if (!webProviderReady) {
    blockers.push("관리자 웹 App Check provider가 등록되지 않았습니다.");
  }
  if (!webDebugReady) {
    blockers.push("관리자 웹 debug token allowlist가 비어 있습니다.");
  }
  if (!verifiedTrafficReady) {
    blockers.push("등록된 Android/Web 앱 모두에서 VALID 요청이 관측되지 않았습니다.");
  }

  return {
    status: blockers.length === 0 ? "READY_FOR_CONTROLLED_FUNCTIONS_TEST" : "HOLD",
    gates: {
      androidProviderReady,
      androidDebugReady,
      webProviderReady,
      webDebugReady,
      verifiedTrafficReady,
    },
    blockers,
    manualChecks: [
      "Android release Play Integrity 토큰과 주요 사용자 흐름을 실기기에서 확인합니다.",
      "관리자 웹 preview/live에서 App Check 토큰과 주요 운영 흐름을 확인합니다.",
      "강제 전환 직전 서비스별 App Check 메트릭을 다시 확인합니다.",
    ],
  };
}

function sumSeriesPoints(points) {
  let total = 0;
  for (const point of Array.isArray(points) ? points : []) {
    const value = Number(point?.value?.int64Value || 0);
    if (Number.isFinite(value)) {
      total += value;
    }
  }
  return total;
}

function sanitizeText(value) {
  if (value === null || value === undefined) {
    return "";
  }
  return String(value).trim();
}

module.exports = {
  buildReadinessDecision,
  summarizeVerificationSeries,
};
