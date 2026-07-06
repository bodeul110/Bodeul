#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }

  validateOptions(options);

  const backupPath = resolvePath(options.backupPath);
  const backup = readJsonFile(backupPath);
  const firestoreGuides = readFirestoreGuides(backup);
  const actualSource = options.apiResponsePath ? "api-response" : "seed-input";
  const actualPath = resolvePath(options.apiResponsePath || options.seedInputPath);
  const actualItems = options.apiResponsePath
    ? readApiResponseGuides(readJsonFile(actualPath), options.limit)
    : readSeedInputGuides(readJsonFile(actualPath), options.limit);

  const result = compareGuides({
    backup,
    backupPath,
    actualItems,
    actualPath,
    actualSource,
    firestoreGuides,
    limit: options.limit,
  });

  if (options.outputPath) {
    writeTextFile(resolvePath(options.outputPath), `${JSON.stringify(result, null, 2)}\n`);
  }
  if (options.markdownPath) {
    writeTextFile(resolvePath(options.markdownPath), renderMarkdown(result));
  }

  printSummary(result);
  if (result.status !== "passed") {
    process.exitCode = 1;
  }
}

function parseOptions(args) {
  const options = {
    backupPath: "",
    apiResponsePath: "",
    seedInputPath: "",
    outputPath: "",
    markdownPath: "",
    limit: 50,
    help: false,
  };

  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--help" || arg === "-h") {
      options.help = true;
      continue;
    }
    if (arg === "--backup") {
      options.backupPath = requiredValue(args, index, arg);
      index += 1;
      continue;
    }
    if (arg === "--api-response") {
      options.apiResponsePath = requiredValue(args, index, arg);
      index += 1;
      continue;
    }
    if (arg === "--seed-input") {
      options.seedInputPath = requiredValue(args, index, arg);
      index += 1;
      continue;
    }
    if (arg === "--output") {
      options.outputPath = requiredValue(args, index, arg);
      index += 1;
      continue;
    }
    if (arg === "--markdown") {
      options.markdownPath = requiredValue(args, index, arg);
      index += 1;
      continue;
    }
    if (arg === "--limit") {
      options.limit = parseLimit(requiredValue(args, index, arg));
      index += 1;
      continue;
    }
    throw new Error(`알 수 없는 옵션입니다: ${arg}`);
  }

  return options;
}

function validateOptions(options) {
  if (!options.backupPath) {
    throw new Error("Firestore 백업 JSON 경로가 필요합니다. --backup 옵션을 지정해 주세요.");
  }
  if (!options.apiResponsePath && !options.seedInputPath) {
    throw new Error("--api-response 또는 --seed-input 중 하나가 필요합니다.");
  }
  if (options.apiResponsePath && options.seedInputPath) {
    throw new Error("--api-response와 --seed-input은 동시에 사용할 수 없습니다.");
  }
}

function requiredValue(args, index, optionName) {
  const value = args[index + 1];
  if (!value || value.startsWith("--")) {
    throw new Error(`${optionName} 옵션 값이 필요합니다.`);
  }
  return value;
}

function parseLimit(rawValue) {
  const limit = Number(rawValue);
  if (!Number.isInteger(limit) || limit < 1 || limit > 100) {
    throw new Error("limit은 1부터 100 사이의 정수여야 합니다.");
  }
  return limit;
}

function printHelp() {
  console.log("병원 가이드 Firestore/API 응답 비교 도구");
  console.log("");
  console.log("사용법:");
  console.log("  node compare-hospital-guides-api.js --backup backups/firestore-backup.json --api-response reports/hospital-guides-api-response.json");
  console.log("  node compare-hospital-guides-api.js --backup backups/firestore-backup.json --seed-input reports/postgres-seed-input.json");
  console.log("");
  console.log("옵션:");
  console.log("  --backup <path>        Firestore 백업 JSON 경로");
  console.log("  --api-response <path>  /admin/hospital-guides API 응답 JSON 경로");
  console.log("  --seed-input <path>    PostgreSQL seed 입력 JSON 경로");
  console.log("  --limit <number>       API limit 기준값, 기본값 50");
  console.log("  --output <path>        비교 결과 JSON 저장 경로");
  console.log("  --markdown <path>      비교 결과 Markdown 저장 경로");
  console.log("");
  console.log("주의:");
  console.log("- 이 도구는 네트워크 호출을 하지 않습니다.");
  console.log("- Firebase ID token, DATABASE_URL, 서비스 계정 키는 입력 파일이나 출력 파일에 남기지 않습니다.");
}

function readFirestoreGuides(backup) {
  const documents = Array.isArray(backup?.collections?.hospitalGuides)
    ? backup.collections.hospitalGuides
    : [];

  return documents.map((document) => {
    const data = document.data || fromFirestoreFields(document.fields || {});
    return {
      firestoreId: stringValue(document.id),
      firestorePath: stringValue(document.path),
      hospitalName: stringValue(data.hospitalName),
      departmentName: stringValue(data.departmentName),
      steps: arrayValue(data.steps),
      createdAt: toTimestamp(data.createdAt),
      updatedAt: toTimestamp(data.updatedAt),
    };
  });
}

function readApiResponseGuides(response, limit) {
  const items = Array.isArray(response?.items) ? response.items : null;
  if (!items) {
    throw new Error("API 응답 JSON에는 items 배열이 있어야 합니다.");
  }

  return items.slice(0, limit).map((item) => ({
    id: stringValue(item.id),
    hospitalName: stringValue(item.hospitalName),
    departmentName: stringValue(item.departmentName),
    steps: arrayValue(item.steps),
    createdAt: toTimestamp(item.createdAt),
    updatedAt: toTimestamp(item.updatedAt),
  }));
}

function readSeedInputGuides(seedInput, limit) {
  const rows = Array.isArray(seedInput?.rows?.hospital_guides)
    ? seedInput.rows.hospital_guides
    : null;
  if (!rows) {
    throw new Error("seed 입력 JSON에는 rows.hospital_guides 배열이 있어야 합니다.");
  }

  return rows
      .map((row) => ({
        id: stringValue(row.id),
        hospitalName: stringValue(row.hospital_name),
        departmentName: stringValue(row.department_name),
        steps: arrayValue(row.steps),
        createdAt: toTimestamp(row.created_at),
        updatedAt: toTimestamp(row.updated_at),
      }))
      .sort(compareApiOrder)
      .slice(0, limit);
}

function compareGuides({backup, backupPath, actualItems, actualPath, actualSource, firestoreGuides, limit}) {
  const diagnostics = [];
  const expectedByKey = indexByGuideKey(firestoreGuides, "Firestore", diagnostics);
  const actualByKey = indexByGuideKey(actualItems, actualSource, diagnostics);
  const missingInActual = [];
  const extraInActual = [];
  const mismatches = [];
  const matches = [];

  for (const [key, expected] of expectedByKey.entries()) {
    const actual = actualByKey.get(key);
    if (!actual) {
      missingInActual.push(toExpectedSummary(expected));
      continue;
    }

    const fieldMismatches = compareGuideFields(expected, actual);
    if (fieldMismatches.length > 0) {
      mismatches.push({
        key,
        firestoreId: expected.firestoreId || "",
        apiId: actual.id || "",
        fields: fieldMismatches,
      });
    } else {
      matches.push({
        key,
        firestoreId: expected.firestoreId || "",
        apiId: actual.id || "",
        hospitalName: expected.hospitalName,
        departmentName: expected.departmentName,
      });
    }
  }

  for (const [key, actual] of actualByKey.entries()) {
    if (!expectedByKey.has(key)) {
      extraInActual.push(toActualSummary(actual));
    }
  }

  const status = (
    diagnostics.every((item) => item.level !== "error") &&
    missingInActual.length === 0 &&
    extraInActual.length === 0 &&
    mismatches.length === 0
  )
    ? "passed"
    : "needs_review";

  return {
    schemaVersion: 1,
    mode: "hospital-guides-firestore-api-comparison",
    status,
    generatedAt: new Date().toISOString(),
    source: {
      backupPath,
      backupProjectId: stringValue(backup?.projectId),
      backupGeneratedAt: stringValue(backup?.generatedAt),
      actualSource,
      actualPath,
      limit,
    },
    counts: {
      firestoreHospitalGuides: firestoreGuides.length,
      actualItems: actualItems.length,
      matchedItems: matches.length,
      missingInActual: missingInActual.length,
      extraInActual: extraInActual.length,
      mismatches: mismatches.length,
    },
    diagnostics,
    missingInActual,
    extraInActual,
    mismatches,
    matches,
  };
}

function indexByGuideKey(guides, sourceName, diagnostics) {
  const index = new Map();
  for (const guide of guides) {
    const key = guideKey(guide);
    if (!key) {
      diagnostics.push({
        level: "error",
        source: sourceName,
        message: "hospitalName 또는 departmentName이 비어 있는 병원 가이드가 있습니다.",
        item: toActualSummary(guide),
      });
      continue;
    }
    if (index.has(key)) {
      diagnostics.push({
        level: "error",
        source: sourceName,
        message: `중복 병원 가이드 키가 있습니다: ${key}`,
      });
      continue;
    }
    index.set(key, guide);
  }
  return index;
}

function compareGuideFields(expected, actual) {
  const mismatches = [];
  compareField(mismatches, "hospitalName", expected.hospitalName, actual.hospitalName);
  compareField(mismatches, "departmentName", expected.departmentName, actual.departmentName);
  compareField(mismatches, "steps.length", expected.steps.length, actual.steps.length);
  compareField(mismatches, "steps.title[]", stepTitles(expected.steps), stepTitles(actual.steps));
  compareField(mismatches, "createdAt", expected.createdAt, actual.createdAt);
  compareField(mismatches, "updatedAt", expected.updatedAt, actual.updatedAt);
  return mismatches;
}

function compareField(mismatches, field, expected, actual) {
  if (JSON.stringify(expected) === JSON.stringify(actual)) {
    return;
  }
  mismatches.push({field, expected, actual});
}

function toExpectedSummary(item) {
  return {
    firestoreId: item.firestoreId || "",
    hospitalName: item.hospitalName,
    departmentName: item.departmentName,
  };
}

function toActualSummary(item) {
  return {
    id: item.id || "",
    hospitalName: item.hospitalName || "",
    departmentName: item.departmentName || "",
  };
}

function renderMarkdown(result) {
  const lines = [
    "# 병원 가이드 Firestore/API 비교 결과",
    "",
    `생성 시각: ${result.generatedAt}`,
    "",
    "## 요약",
    "",
    `- 상태: ${result.status}`,
    `- Firestore 기준: ${result.counts.firestoreHospitalGuides}건`,
    `- 비교 대상 응답: ${result.counts.actualItems}건`,
    `- 일치: ${result.counts.matchedItems}건`,
    `- 누락: ${result.counts.missingInActual}건`,
    `- 추가: ${result.counts.extraInActual}건`,
    `- 불일치: ${result.counts.mismatches}건`,
    "",
    "## 입력",
    "",
    `- Firestore 백업: \`${result.source.backupPath}\``,
    `- Firebase 프로젝트: \`${result.source.backupProjectId || "알 수 없음"}\``,
    `- 백업 생성 시각: \`${result.source.backupGeneratedAt || "알 수 없음"}\``,
    `- 비교 대상: \`${result.source.actualSource}\``,
    `- 비교 대상 파일: \`${result.source.actualPath}\``,
    `- limit: \`${result.source.limit}\``,
  ];

  appendDiagnostics(lines, result.diagnostics);
  appendItems(lines, "누락 항목", result.missingInActual);
  appendItems(lines, "추가 항목", result.extraInActual);
  appendMismatches(lines, result.mismatches);
  return `${lines.join("\n")}\n`;
}

function appendDiagnostics(lines, diagnostics) {
  if (diagnostics.length === 0) {
    return;
  }
  lines.push("", "## 진단", "");
  for (const item of diagnostics) {
    lines.push(`- ${item.level}: ${item.message}`);
  }
}

function appendItems(lines, title, items) {
  if (items.length === 0) {
    return;
  }
  lines.push("", `## ${title}`, "");
  for (const item of items) {
    lines.push(`- ${item.hospitalName} / ${item.departmentName}`);
  }
}

function appendMismatches(lines, mismatches) {
  if (mismatches.length === 0) {
    return;
  }
  lines.push("", "## 불일치", "");
  for (const mismatch of mismatches) {
    lines.push(`- ${mismatch.key}`);
    for (const field of mismatch.fields) {
      lines.push(`  - ${field.field}: expected=${JSON.stringify(field.expected)}, actual=${JSON.stringify(field.actual)}`);
    }
  }
}

function fromFirestoreFields(fields) {
  const data = {};
  for (const [key, value] of Object.entries(fields || {})) {
    data[key] = fromFirestoreValue(value);
  }
  return data;
}

function fromFirestoreValue(value) {
  if (!value || typeof value !== "object") {
    return value;
  }
  if (Object.prototype.hasOwnProperty.call(value, "stringValue")) {
    return value.stringValue;
  }
  if (Object.prototype.hasOwnProperty.call(value, "integerValue")) {
    return Number(value.integerValue);
  }
  if (Object.prototype.hasOwnProperty.call(value, "doubleValue")) {
    return Number(value.doubleValue);
  }
  if (Object.prototype.hasOwnProperty.call(value, "booleanValue")) {
    return Boolean(value.booleanValue);
  }
  if (Object.prototype.hasOwnProperty.call(value, "nullValue")) {
    return null;
  }
  if (Object.prototype.hasOwnProperty.call(value, "timestampValue")) {
    return value.timestampValue;
  }
  if (Object.prototype.hasOwnProperty.call(value, "mapValue")) {
    return fromFirestoreFields(value.mapValue?.fields || {});
  }
  if (Object.prototype.hasOwnProperty.call(value, "arrayValue")) {
    return (value.arrayValue?.values || []).map((item) => fromFirestoreValue(item));
  }
  return value;
}

function compareApiOrder(left, right) {
  const updatedAtDiff = timestampMillis(right.updatedAt) - timestampMillis(left.updatedAt);
  if (updatedAtDiff !== 0) {
    return updatedAtDiff;
  }
  const hospitalDiff = left.hospitalName.localeCompare(right.hospitalName, "ko");
  if (hospitalDiff !== 0) {
    return hospitalDiff;
  }
  const departmentDiff = left.departmentName.localeCompare(right.departmentName, "ko");
  if (departmentDiff !== 0) {
    return departmentDiff;
  }
  return left.id.localeCompare(right.id);
}

function guideKey(item) {
  const hospitalName = stringValue(item.hospitalName).trim();
  const departmentName = stringValue(item.departmentName).trim();
  return hospitalName && departmentName ? `${hospitalName}\u0000${departmentName}` : "";
}

function stepTitles(steps) {
  return steps.map((step) => stringValue(step?.title));
}

function toTimestamp(value) {
  if (value === undefined || value === null || value === "") {
    return "";
  }
  if (typeof value === "number") {
    return new Date(value).toISOString();
  }
  if (typeof value === "string") {
    const numberValue = Number(value);
    if (Number.isFinite(numberValue) && /^\d+$/.test(value)) {
      return new Date(numberValue).toISOString();
    }
    const parsed = Date.parse(value);
    return Number.isNaN(parsed) ? "" : new Date(parsed).toISOString();
  }
  if (value instanceof Date) {
    return value.toISOString();
  }
  if (typeof value === "object") {
    if (typeof value.seconds === "number") {
      return new Date(value.seconds * 1000).toISOString();
    }
    if (typeof value._seconds === "number") {
      return new Date(value._seconds * 1000).toISOString();
    }
  }
  return "";
}

function timestampMillis(value) {
  const millis = Date.parse(value);
  return Number.isNaN(millis) ? 0 : millis;
}

function arrayValue(value) {
  return Array.isArray(value) ? value : [];
}

function stringValue(value) {
  return value === undefined || value === null ? "" : String(value);
}

function readJsonFile(filePath) {
  return JSON.parse(fs.readFileSync(filePath, "utf8").replace(/^\uFEFF/, ""));
}

function writeTextFile(filePath, content) {
  fs.mkdirSync(path.dirname(filePath), {recursive: true});
  fs.writeFileSync(filePath, content, "utf8");
}

function resolvePath(filePath) {
  return path.resolve(process.cwd(), filePath);
}

function printSummary(result) {
  console.log("병원 가이드 Firestore/API 비교 결과");
  console.log(`- 상태: ${result.status}`);
  console.log(`- Firestore 기준: ${result.counts.firestoreHospitalGuides}건`);
  console.log(`- 비교 대상 응답: ${result.counts.actualItems}건`);
  console.log(`- 일치: ${result.counts.matchedItems}건`);
  console.log(`- 누락: ${result.counts.missingInActual}건`);
  console.log(`- 추가: ${result.counts.extraInActual}건`);
  console.log(`- 불일치: ${result.counts.mismatches}건`);
}

main().catch((error) => {
  console.error("병원 가이드 Firestore/API 비교 중 오류가 발생했습니다.");
  console.error(error);
  process.exitCode = 1;
});
