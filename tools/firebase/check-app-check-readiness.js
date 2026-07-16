#!/usr/bin/env node

"use strict";

const fs = require("node:fs");
const path = require("node:path");

const {
  buildReadinessDecision,
  summarizeVerificationSeries,
} = require("./lib/app-check-readiness");
const {resolveProjectId} = require("./lib/firebase-toolkit");

const DEFAULT_DAYS = 30;
const DEFAULT_REGION = "asia-northeast3";
const VERIFICATION_METRIC =
  "firebaseappcheck.googleapis.com/services/verification_count";
const PROJECT_ID_PATTERN = /^[a-z][a-z0-9-]{4,28}[a-z0-9]$/;
const REGION_PATTERN = /^[a-z]+-[a-z]+[0-9]$/;

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }

  const projectId = validateProjectId(options.projectId || resolveProjectId());
  const accessToken = resolveAccessToken();
  const report = await collectReadiness({
    projectId,
    accessToken,
    days: options.days,
    region: options.region,
  });

  if (options.outputPath) {
    const outputPath = path.resolve(options.outputPath);
    fs.mkdirSync(path.dirname(outputPath), {recursive: true});
    fs.writeFileSync(outputPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  }

  if (options.json) {
    console.log(JSON.stringify(report, null, 2));
    return;
  }

  printHumanReport(report, options.outputPath);
}

async function collectReadiness({projectId, accessToken, days, region}) {
  const headers = buildHeaders(projectId, accessToken);
  const firebaseProject = await requestJson(
      `https://firebase.googleapis.com/v1beta1/projects/${encodeURIComponent(projectId)}`,
      headers,
      "Firebase 프로젝트",
  );
  const projectNumber = sanitizeText(firebaseProject.projectNumber);
  if (!/^\d+$/.test(projectNumber)) {
    throw new Error("Firebase project number를 확인하지 못했습니다.");
  }

  const androidApps = await loadAndroidApps({projectId, projectNumber, headers});
  const webApps = await loadWebApps({projectId, projectNumber, headers});
  const services = await loadServices({projectNumber, headers});
  const functions = await loadFunctions({projectId, region, headers});
  const timeSeries = await loadVerificationSeries({projectId, days, headers});
  const verification = summarizeVerificationSeries(timeSeries);
  const readiness = buildReadinessDecision({androidApps, webApps, verification});

  return {
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    projectId,
    projectNumber,
    observationDays: days,
    region,
    androidApps,
    webApps,
    services,
    functions,
    verification,
    readiness,
  };
}

async function loadAndroidApps({projectId, projectNumber, headers}) {
  const apps = await listPaged(
      `https://firebase.googleapis.com/v1beta1/projects/${encodeURIComponent(projectId)}` +
        "/androidApps?pageSize=100",
      headers,
      "apps",
      "Firebase Android 앱",
  );

  return Promise.all(apps.map(async (app) => {
    const appId = sanitizeText(app.appId);
    const certificates = await requestJson(
        `https://firebase.googleapis.com/v1beta1/${app.name}/sha`,
        headers,
        "Android SHA 인증서",
    );
    const playIntegrity = await requestOptionalJson(
        `https://firebaseappcheck.googleapis.com/v1/projects/${projectNumber}` +
          `/apps/${appId}/playIntegrityConfig`,
        headers,
        "Play Integrity 설정",
    );
    const debugTokens = await requestJson(
        `https://firebaseappcheck.googleapis.com/v1/projects/${projectNumber}` +
          `/apps/${appId}/debugTokens?pageSize=100`,
        headers,
        "Android App Check debug token",
    );

    return {
      displayName: sanitizeText(app.displayName),
      appId,
      packageName: sanitizeText(app.packageName),
      sha256Count: asArray(certificates.certificates)
          .filter((certificate) => certificate.certType === "SHA_256").length,
      debugTokenCount: asArray(debugTokens.debugTokens).length,
      playIntegrityConfigAvailable: Boolean(playIntegrity),
      playIntegrityTokenTtl: sanitizeText(playIntegrity?.tokenTtl),
      minDeviceRecognitionLevel: sanitizeText(
          playIntegrity?.deviceIntegrity?.minDeviceRecognitionLevel,
      ),
    };
  }));
}

async function loadWebApps({projectId, projectNumber, headers}) {
  const apps = await listPaged(
      `https://firebase.googleapis.com/v1beta1/projects/${encodeURIComponent(projectId)}` +
        "/webApps?pageSize=100",
      headers,
      "apps",
      "Firebase Web 앱",
  );

  return Promise.all(apps.map(async (app) => {
    const appId = sanitizeText(app.appId);
    const baseUrl = `https://firebaseappcheck.googleapis.com/v1/projects/${projectNumber}` +
      `/apps/${appId}`;
    const [recaptchaV3, recaptchaEnterprise, debugTokens] = await Promise.all([
      requestOptionalJson(
          `${baseUrl}/recaptchaV3Config`,
          headers,
          "reCAPTCHA v3 설정",
      ),
      requestOptionalJson(
          `${baseUrl}/recaptchaEnterpriseConfig`,
          headers,
          "reCAPTCHA Enterprise 설정",
      ),
      requestJson(
          `${baseUrl}/debugTokens?pageSize=100`,
          headers,
          "Web App Check debug token",
      ),
    ]);
    const enterpriseConfigured = Boolean(sanitizeText(recaptchaEnterprise?.siteKey));
    const v3Configured = recaptchaV3?.siteSecretSet === true;

    return {
      displayName: sanitizeText(app.displayName),
      appId,
      provider: enterpriseConfigured ?
        "recaptcha_enterprise" :
        (v3Configured ? "recaptcha_v3" : "none"),
      debugTokenCount: asArray(debugTokens.debugTokens).length,
      tokenTtl: enterpriseConfigured ?
        sanitizeText(recaptchaEnterprise?.tokenTtl) :
        sanitizeText(recaptchaV3?.tokenTtl),
    };
  }));
}

async function loadServices({projectNumber, headers}) {
  const services = await listPaged(
      `https://firebaseappcheck.googleapis.com/v1/projects/${projectNumber}` +
        "/services?pageSize=100",
      headers,
      "services",
      "App Check 서비스",
  );

  return services.map((service) => ({
    service: sanitizeText(service.name).split("/").pop(),
    enforcementMode: sanitizeText(service.enforcementMode) || "UNSPECIFIED",
  })).sort((left, right) => left.service.localeCompare(right.service));
}

async function loadFunctions({projectId, region, headers}) {
  const functions = await listPaged(
      `https://cloudfunctions.googleapis.com/v2/projects/${encodeURIComponent(projectId)}` +
        `/locations/${region}/functions?pageSize=100`,
      headers,
      "functions",
      "Cloud Functions",
  );
  const enforcementValues = functions.map((entry) => sanitizeText(
      entry?.serviceConfig?.environmentVariables?.ENABLE_APPCHECK_ENFORCEMENT,
  ).toLowerCase());

  return {
    deployedCount: functions.length,
    enforcementTrueCount: enforcementValues.filter((value) => value === "true").length,
    enforcementFalseOrUnsetCount: enforcementValues.filter((value) => value !== "true").length,
  };
}

async function loadVerificationSeries({projectId, days, headers}) {
  const endTime = new Date();
  const startTime = new Date(endTime.getTime() - (days * 24 * 60 * 60 * 1000));
  const url = new URL(
      `https://monitoring.googleapis.com/v3/projects/${encodeURIComponent(projectId)}` +
        "/timeSeries",
  );
  url.searchParams.set("filter", `metric.type="${VERIFICATION_METRIC}"`);
  url.searchParams.set("interval.startTime", startTime.toISOString());
  url.searchParams.set("interval.endTime", endTime.toISOString());
  url.searchParams.set("view", "FULL");
  url.searchParams.set("pageSize", "1000");

  return listPaged(url.toString(), headers, "timeSeries", "App Check 검증 메트릭");
}

async function listPaged(url, headers, propertyName, label) {
  const entries = [];
  let nextUrl = new URL(url);

  while (nextUrl) {
    const payload = await requestJson(nextUrl.toString(), headers, label);
    entries.push(...asArray(payload[propertyName]));
    const pageToken = sanitizeText(payload.nextPageToken);
    if (!pageToken) {
      break;
    }
    nextUrl.searchParams.set("pageToken", pageToken);
  }

  return entries;
}

async function requestOptionalJson(url, headers, label) {
  return requestJson(url, headers, label, true);
}

async function requestJson(url, headers, label, allowNotFound = false) {
  const response = await fetch(url, {
    headers,
    signal: AbortSignal.timeout(30000),
  });
  if (allowNotFound && response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`${label} 조회 실패: HTTP ${response.status}`);
  }
  return response.json();
}

function parseOptions(args) {
  const options = {
    projectId: "",
    days: DEFAULT_DAYS,
    region: DEFAULT_REGION,
    outputPath: "",
    json: false,
    help: false,
  };

  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--help" || arg === "-h") {
      options.help = true;
    } else if (arg === "--json") {
      options.json = true;
    } else if (arg === "--project") {
      options.projectId = requireOptionValue(args, ++index, arg);
    } else if (arg === "--days") {
      options.days = Number(requireOptionValue(args, ++index, arg));
    } else if (arg === "--region") {
      options.region = requireOptionValue(args, ++index, arg);
    } else if (arg === "--output") {
      options.outputPath = requireOptionValue(args, ++index, arg);
    } else {
      throw new Error(`지원하지 않는 인자입니다: ${arg}`);
    }
  }

  if (!Number.isInteger(options.days) || options.days < 1 || options.days > 90) {
    throw new Error("--days는 1에서 90 사이의 정수여야 합니다.");
  }
  if (!REGION_PATTERN.test(options.region)) {
    throw new Error("--region 형식이 올바르지 않습니다.");
  }
  return options;
}

function requireOptionValue(args, index, optionName) {
  const value = sanitizeText(args[index]);
  if (!value || value.startsWith("--")) {
    throw new Error(`${optionName} 값이 필요합니다.`);
  }
  return value;
}

function resolveAccessToken() {
  const accessToken = sanitizeText(
      process.env.GOOGLE_OAUTH_ACCESS_TOKEN || process.env.GCLOUD_ACCESS_TOKEN,
  );
  if (!accessToken) {
    throw new Error(
        "GOOGLE_OAUTH_ACCESS_TOKEN이 필요합니다. gcloud auth print-access-token 결과를 " +
        "현재 프로세스 환경변수로만 전달해 주세요.",
    );
  }
  return accessToken;
}

function buildHeaders(projectId, accessToken) {
  return {
    Authorization: `Bearer ${accessToken}`,
    "x-goog-user-project": projectId,
  };
}

function validateProjectId(value) {
  const projectId = sanitizeText(value);
  if (!PROJECT_ID_PATTERN.test(projectId)) {
    throw new Error("Firebase project ID 형식이 올바르지 않습니다.");
  }
  return projectId;
}

function sanitizeText(value) {
  if (value === null || value === undefined) {
    return "";
  }
  return String(value).trim();
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function printHumanReport(report, outputPath) {
  console.log("보들 App Check 준비 상태");
  console.log(`프로젝트: ${report.projectId}`);
  console.log(`관측 기간: 최근 ${report.observationDays}일`);
  console.log(`판단: ${report.readiness.status}`);
  console.log("");
  console.log(`검증 요청: ${report.verification.verifiedCount}/${report.verification.totalCount}`);
  console.log(`Android 앱: ${report.androidApps.length}개`);
  console.log(`Web 앱: ${report.webApps.length}개`);
  console.log(`Functions enforcement=true: ${report.functions.enforcementTrueCount}개`);
  console.log("");
  console.log("차단 요인:");
  for (const blocker of report.readiness.blockers) {
    console.log(`- ${blocker}`);
  }
  if (outputPath) {
    console.log("");
    console.log(`JSON 저장: ${path.resolve(outputPath)}`);
  }
}

function printHelp() {
  console.log("보들 App Check 준비 상태 읽기 전용 점검");
  console.log("");
  console.log("사용법:");
  console.log("  npm run check:app-check -- --project bodeul-dev");
  console.log("  npm run check:app-check -- --project bodeul-dev --json --output reports/app-check.json");
  console.log("");
  console.log("필수 환경변수:");
  console.log("  GOOGLE_OAUTH_ACCESS_TOKEN (현재 프로세스에서만 사용)");
}

main().catch((error) => {
  console.error("App Check 준비 상태 점검 중 오류가 발생했습니다.");
  console.error(error.message);
  process.exitCode = 1;
});
