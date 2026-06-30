#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const {MANAGED_COLLECTIONS} = require("./lib/baseline-config");
const {validateBackupSnapshot} = require("./lib/backup-validator");

const TABLE_RULES = Object.freeze([
  {
    table: "app_users",
    sources: ["users"],
    required: [
      {name: "role", aliases: ["role"]},
    ],
    countRows: ({collections}) => documentsOf(collections, "users").length,
  },
  {
    table: "manager_document_files",
    sources: ["users"],
    required: [],
    countRows: ({collections}) => documentsOf(collections, "users")
        .reduce((total, document) => total + collectManagerDocumentFiles(document.data).length, 0),
  },
  {
    table: "manager_document_reviews",
    sources: ["users"],
    required: [],
    countRows: ({collections}) => documentsOf(collections, "users")
        .reduce((total, document) => total + asArray(document.data.managerDocumentHistory).length, 0),
  },
  {
    table: "hospital_guides",
    sources: ["hospitalGuides"],
    required: [
      {name: "hospital_name", aliases: ["hospitalName"]},
      {name: "department_name", aliases: ["departmentName"]},
    ],
    countRows: ({collections}) => documentsOf(collections, "hospitalGuides").length,
  },
  {
    table: "appointment_requests",
    sources: ["appointmentRequests"],
    required: [
      {name: "status", aliases: ["status"]},
    ],
    countRows: ({collections}) => documentsOf(collections, "appointmentRequests").length,
  },
  {
    table: "companion_sessions",
    sources: ["companionSessions"],
    required: [
      {name: "appointment_request_id", aliases: ["appointmentRequestId", "requestId"]},
      {name: "current_status", aliases: ["currentStatus", "status"]},
    ],
    countRows: ({collections}) => documentsOf(collections, "companionSessions").length,
  },
  {
    table: "session_reports",
    sources: ["sessionReports"],
    required: [
      {name: "companion_session_id", aliases: ["sessionId", "companionSessionId"]},
    ],
    countRows: ({collections}) => documentsOf(collections, "sessionReports").length,
  },
  {
    table: "appointment_follow_ups",
    sources: ["appointmentFollowUps", "adminSettlementRecords", "adminEmergencyIssues"],
    required: [],
    countRows: ({collections}) => collectAppointmentFollowUpKeys(collections).size,
  },
  {
    table: "support_requests",
    sources: ["supportInquiries", "clientSupportRequests"],
    required: [],
    countRows: ({collections}) =>
      documentsOf(collections, "supportInquiries").length +
      documentsOf(collections, "clientSupportRequests").length,
  },
  {
    table: "admin_audit_logs",
    sources: ["adminAuditLogs"],
    required: [
      {name: "action_summary", aliases: ["actionSummary", "summary", "message"]},
    ],
    countRows: ({collections}) => documentsOf(collections, "adminAuditLogs").length,
  },
]);

const POSTGRES_EXCLUDED_COLLECTIONS = Object.freeze([
  {
    collection: "adminActionNotifications",
    reason: "FCM/관리자 알림 전달 상태는 API/알림 경계 확정 후 별도 이전한다.",
  },
  {
    collection: "adminActionDeliveries",
    reason: "알림 전달 이력은 운영 API와 FCM 설계가 확정된 뒤 이전한다.",
  },
  {
    collection: "adminActionDeliveryJobs",
    reason: "잡 실행 상태는 PostgreSQL 초기 seed 검증 범위에서 제외한다.",
  },
  {
    collection: "appointmentReminderJobs",
    reason: "예약 리마인더 잡은 FCM/Functions 운영 경계가 확정된 뒤 이전한다.",
  },
]);

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }
  if (!options.filePath) {
    throw new Error("dry-run에 사용할 Firestore 백업 JSON 경로가 필요합니다. --file 옵션을 지정해 주세요.");
  }

  const backupPath = path.resolve(process.cwd(), options.filePath);
  const snapshot = readJsonFile(backupPath);
  const validation = validateBackupSnapshot(snapshot, MANAGED_COLLECTIONS);
  const collections = normalizeCollections(snapshot);
  const report = buildDryRunReport({backupPath, snapshot, validation, collections});
  const outputPath = resolveOutputPath(options.outputPath);

  fs.mkdirSync(path.dirname(outputPath), {recursive: true});
  fs.writeFileSync(outputPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");

  printSummary(report, outputPath);
  if (report.status !== "passed") {
    process.exitCode = 1;
  }
}

function readJsonFile(filePath) {
  const content = fs.readFileSync(filePath, "utf8").replace(/^\uFEFF/, "");
  return JSON.parse(content);
}

function parseOptions(args) {
  const fileIndex = args.indexOf("--file");
  const outputIndex = args.indexOf("--output");
  const optionValueIndexes = new Set();
  if (fileIndex >= 0) {
    optionValueIndexes.add(fileIndex + 1);
  }
  if (outputIndex >= 0) {
    optionValueIndexes.add(outputIndex + 1);
  }
  const positionalArgs = args.filter((arg, index) => {
    if (arg.startsWith("--")) {
      return false;
    }
    return !optionValueIndexes.has(index);
  });
  return {
    filePath: fileIndex >= 0 ? args[fileIndex + 1] : positionalArgs[0] || "",
    outputPath: outputIndex >= 0 ? args[outputIndex + 1] : positionalArgs[1] || "",
    help: args.includes("--help") || args.includes("-h"),
  };
}

function printHelp() {
  console.log("보들 Firestore -> PostgreSQL seed dry-run 리포트 생성기");
  console.log("");
  console.log("사용법:");
  console.log("  node prepare-postgres-seed-dry-run.js --file backups/firestore-backup.json");
  console.log("  node prepare-postgres-seed-dry-run.js --file backups/firestore-backup.json --output reports/postgres-seed-dry-run.json");
  console.log("  node prepare-postgres-seed-dry-run.js backups/firestore-backup.json reports/postgres-seed-dry-run.json");
  console.log("");
  console.log("주의:");
  console.log("- 이 스크립트는 Supabase나 Firestore에 쓰기 작업을 하지 않습니다.");
  console.log("- DB connection string, anon key, service role key가 필요하지 않습니다.");
}

function buildDryRunReport({backupPath, snapshot, validation, collections}) {
  const tableReports = TABLE_RULES.map((rule) => buildTableReport(rule, collections));
  const excludedCollections = POSTGRES_EXCLUDED_COLLECTIONS.map((item) => ({
    ...item,
    sourceDocuments: documentsOf(collections, item.collection).length,
  }));
  const collectionCounts = {};
  for (const collectionName of Object.keys(collections).sort()) {
    collectionCounts[collectionName] = documentsOf(collections, collectionName).length;
  }

  const missingRequiredCount = tableReports
      .reduce((total, table) => total + table.missingRequiredFields.length, 0);
  const status = validation.errors.length === 0 && missingRequiredCount === 0 ? "passed" : "needs_review";

  return {
    schemaVersion: 1,
    mode: "dry-run",
    status,
    generatedAt: new Date().toISOString(),
    source: {
      backupPath,
      projectId: snapshot.projectId || "",
      backupGeneratedAt: snapshot.generatedAt || "",
      backupSchemaVersion: snapshot.schemaVersion ?? null,
    },
    validation: {
      errors: validation.errors,
      warnings: validation.warnings,
    },
    collectionCounts,
    tableReports,
    excludedCollections,
    comparisonTemplate: buildComparisonTemplate(tableReports),
    notes: [
      "이 리포트는 seed 적용 전 후보 수와 필수 필드만 확인한다.",
      "PostgreSQL UUID FK는 실제 import 단계에서 Firestore 문서 ID와 firebase_uid를 기준으로 별도 해석해야 한다.",
      "지원하지 않는 컬렉션은 초기 seed 범위에서 제외하고 API/알림 경계가 확정된 뒤 별도 이전한다.",
    ],
  };
}

function buildTableReport(rule, collections) {
  const missingRequiredFields = [];
  for (const collectionName of rule.sources) {
    for (const document of documentsOf(collections, collectionName)) {
      const missing = rule.required
          .filter((field) => !hasAnyValue(document.data, field.aliases))
          .map((field) => field.name);
      if (missing.length > 0) {
        missingRequiredFields.push({
          sourceCollection: collectionName,
          sourceId: document.id,
          sourcePath: document.path,
          fields: missing,
        });
      }
    }
  }

  return {
    table: rule.table,
    sourceCollections: rule.sources,
    sourceDocuments: rule.sources.reduce((total, name) => total + documentsOf(collections, name).length, 0),
    candidateRows: rule.countRows({collections}),
    missingRequiredFields,
  };
}

function buildComparisonTemplate(tableReports) {
  return tableReports.map((tableReport) => ({
    table: tableReport.table,
    expectedRows: tableReport.candidateRows,
    actualRowsQuery: `select count(*)::int as count from ${tableReport.table};`,
    fieldCheck: tableReport.missingRequiredFields.length === 0 ? "필수 필드 후보 통과" : "필수 필드 누락 검토 필요",
  }));
}

function normalizeCollections(snapshot) {
  const collections = {};
  for (const [collectionName, documents] of Object.entries(snapshot.collections || {})) {
    collections[collectionName] = Array.isArray(documents)
      ? documents.map((document) => ({
        id: typeof document.id === "string" ? document.id : "",
        path: typeof document.path === "string" ? document.path : `${collectionName}/${document.id || ""}`,
        data: document.data || fromFirestoreFields(document.fields || {}),
      }))
      : [];
  }
  return collections;
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

function documentsOf(collections, collectionName) {
  return Array.isArray(collections[collectionName]) ? collections[collectionName] : [];
}

function collectManagerDocumentFiles(data) {
  const files = [];
  const fileMap = isPlainObject(data.managerDocumentFiles) ? data.managerDocumentFiles : {};
  const pathMap = isPlainObject(data.managerDocumentFilePaths) ? data.managerDocumentFilePaths : {};
  const keys = new Set(Object.keys(fileMap).concat(Object.keys(pathMap)));
  for (const key of keys) {
    const file = isPlainObject(fileMap[key]) ? fileMap[key] : {};
    const storagePath = firstNonEmpty(file.storagePath, file.fullPath, pathMap[key]);
    if (storagePath) {
      files.push({documentKey: key, storagePath});
    }
  }
  return files;
}

function collectAppointmentFollowUpKeys(collections) {
  const keys = new Set();
  for (const collectionName of ["appointmentFollowUps", "adminSettlementRecords", "adminEmergencyIssues"]) {
    for (const document of documentsOf(collections, collectionName)) {
      keys.add(firstNonEmpty(document.data.requestId, document.data.appointmentRequestId, document.id));
    }
  }
  keys.delete("");
  return keys;
}

function hasAnyValue(data, aliases) {
  return aliases.some((alias) => {
    const value = data[alias];
    return value !== undefined && value !== null && value !== "";
  });
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function isPlainObject(value) {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function firstNonEmpty(...values) {
  for (const value of values) {
    if (value !== undefined && value !== null && value !== "") {
      return value;
    }
  }
  return "";
}

function resolveOutputPath(outputPath) {
  if (outputPath) {
    return path.resolve(process.cwd(), outputPath);
  }
  return path.resolve(process.cwd(), "reports", `postgres-seed-dry-run-${formatTimestamp(new Date())}.json`);
}

function formatTimestamp(date) {
  const year = String(date.getFullYear());
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const hour = String(date.getHours()).padStart(2, "0");
  const minute = String(date.getMinutes()).padStart(2, "0");
  const second = String(date.getSeconds()).padStart(2, "0");
  return `${year}${month}${day}-${hour}${minute}${second}`;
}

function printSummary(report, outputPath) {
  console.log("Firestore -> PostgreSQL seed dry-run 리포트");
  console.log(`- 상태: ${report.status}`);
  console.log(`- 백업 파일: ${report.source.backupPath}`);
  console.log(`- 출력 파일: ${outputPath}`);
  console.log(`- 백업 검증 오류: ${report.validation.errors.length}건`);
  console.log(`- 백업 검증 경고: ${report.validation.warnings.length}건`);
  for (const tableReport of report.tableReports) {
    console.log(`- ${tableReport.table}: 후보 ${tableReport.candidateRows}건, 필수 필드 누락 ${tableReport.missingRequiredFields.length}건`);
  }
}

main().catch((error) => {
  console.error("PostgreSQL seed dry-run 리포트 생성 중 오류가 발생했습니다.");
  console.error(error);
  process.exitCode = 1;
});
