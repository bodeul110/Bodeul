#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const NULLABLE_COLUMNS = Object.freeze({
  app_users: new Set([
    "manager_document_status",
    "manager_document_updated_at",
    "manager_document_reviewed_at",
  ]),
  appointment_requests: new Set([
    "patient_user_id",
    "guardian_user_id",
    "manager_user_id",
    "requester_user_id",
    "hospital_latitude",
    "hospital_longitude",
    "appointment_at",
    "payment_approved_at",
  ]),
  companion_sessions: new Set([
    "manager_user_id",
  ]),
  session_reports: new Set([
    "next_visit_at",
  ]),
  appointment_follow_ups: new Set([
    "review_saved_at",
    "settlement_follow_up_saved_at",
    "support_escalated_at",
  ]),
  support_requests: new Set([
    "requester_user_id",
    "appointment_request_id",
    "responded_by_user_id",
    "responded_at",
  ]),
  manager_document_files: new Set([
    "uploaded_at",
  ]),
  manager_document_reviews: new Set([
    "reviewed_by_user_id",
  ]),
  admin_audit_logs: new Set([
    "actor_user_id",
    "request_id",
    "inquiry_id",
  ]),
});

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }
  if (!options.filePath) {
    throw new Error("SQL로 변환할 PostgreSQL seed 입력 JSON 경로가 필요합니다. --file 옵션을 지정해 주세요.");
  }

  const seedInputPath = path.resolve(process.cwd(), options.filePath);
  const seedInput = readJsonFile(seedInputPath);
  validateSeedInput(seedInput);

  const sql = buildSeedSql(seedInput, {rollback: options.rollback});
  const outputPath = resolveOutputPath(options.outputPath);
  fs.mkdirSync(path.dirname(outputPath), {recursive: true});
  fs.writeFileSync(outputPath, sql, "utf8");

  printSummary(seedInput, outputPath);
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
  const positionalArgs = args.filter((arg, index) => !arg.startsWith("--") && !optionValueIndexes.has(index));
  return {
    filePath: fileIndex >= 0 ? args[fileIndex + 1] : positionalArgs[0] || "",
    outputPath: outputIndex >= 0 ? args[outputIndex + 1] : positionalArgs[1] || "",
    help: args.includes("--help") || args.includes("-h"),
    rollback: args.includes("--rollback"),
  };
}

function printHelp() {
  console.log("보들 PostgreSQL seed SQL 생성기");
  console.log("");
  console.log("사용법:");
  console.log("  node build-postgres-seed-sql.js --file reports/postgres-seed-input.json");
  console.log("  node build-postgres-seed-sql.js --file reports/postgres-seed-input.json --output reports/postgres-seed.sql");
  console.log("  node build-postgres-seed-sql.js --file reports/postgres-seed-input.json --output reports/postgres-seed-rollback.sql --rollback");
  console.log("  node build-postgres-seed-sql.js reports/postgres-seed-input.json reports/postgres-seed.sql");
  console.log("");
  console.log("주의:");
  console.log("- 이 스크립트는 Supabase에 접속하거나 SQL을 실행하지 않습니다.");
  console.log("- 출력 SQL은 운영 데이터와 개인정보를 포함할 수 있으므로 커밋하지 않습니다.");
}

function buildSeedSql(seedInput, {rollback}) {
  const lines = [
    "-- BoDeul PostgreSQL seed SQL",
    "-- 이 파일은 Firestore 백업 기반 seed 입력 JSON에서 생성되었습니다.",
    "-- Supabase SQL Editor 또는 psql에서 적용하기 전 row count와 FK 진단을 다시 확인하세요.",
    rollback ? "-- 검증 모드: 마지막에 rollback을 실행하므로 데이터가 저장되지 않습니다." : "-- 적용 모드: 마지막에 commit을 실행합니다.",
    "",
    "begin;",
    "",
  ];

  for (const table of seedInput.tableOrder) {
    const rows = Array.isArray(seedInput.rows?.[table]) ? seedInput.rows[table] : [];
    if (rows.length === 0) {
      lines.push(`-- ${table}: 0 rows`);
      lines.push("");
      continue;
    }
    for (const row of rows) {
      lines.push(buildInsertStatement(table, row));
    }
    lines.push("");
  }

  lines.push(rollback ? "rollback;" : "commit;");
  lines.push("");
  return lines.join("\n");
}

function buildInsertStatement(table, row) {
  const entries = Object.entries(row)
      .filter(([column, value]) => value !== undefined && (value !== null || isNullableColumn(table, column)));
  const columns = entries.map(([column]) => quoteIdentifier(column));
  const values = entries.map(([, value]) => toSqlLiteral(value));
  const conflictColumns = table === "appointment_follow_ups" ? ["appointment_request_id"] : ["id"];
  const updateColumns = entries
      .map(([column]) => column)
      .filter((column) => !conflictColumns.includes(column));
  const conflictTarget = conflictColumns.map(quoteIdentifier).join(", ");
  const updateClause = updateColumns.length > 0
    ? `do update set ${updateColumns.map((column) => `${quoteIdentifier(column)} = excluded.${quoteIdentifier(column)}`).join(", ")}`
    : "do nothing";

  return [
    `insert into ${quoteIdentifier(table)} (${columns.join(", ")})`,
    `values (${values.join(", ")})`,
    `on conflict (${conflictTarget}) ${updateClause};`,
  ].join("\n");
}

function toSqlLiteral(value) {
  if (value === null || value === undefined) {
    return "null";
  }
  if (typeof value === "number") {
    return Number.isFinite(value) ? String(value) : "null";
  }
  if (typeof value === "boolean") {
    return value ? "true" : "false";
  }
  if (Array.isArray(value) || (value && typeof value === "object")) {
    return `${quoteString(JSON.stringify(value))}::jsonb`;
  }
  return quoteString(String(value));
}

function isNullableColumn(table, column) {
  return Boolean(NULLABLE_COLUMNS[table]?.has(column));
}

function quoteString(value) {
  return `'${String(value).replaceAll("'", "''")}'`;
}

function quoteIdentifier(value) {
  return `"${String(value).replaceAll("\"", "\"\"")}"`;
}

function validateSeedInput(seedInput) {
  if (!seedInput || typeof seedInput !== "object") {
    throw new Error("seed 입력 JSON 루트가 객체가 아닙니다.");
  }
  if (seedInput.mode !== "seed-input") {
    throw new Error("seed 입력 JSON의 mode가 seed-input이 아닙니다.");
  }
  if (!Array.isArray(seedInput.tableOrder)) {
    throw new Error("seed 입력 JSON에 tableOrder 배열이 없습니다.");
  }
  if (!seedInput.rows || typeof seedInput.rows !== "object") {
    throw new Error("seed 입력 JSON에 rows 객체가 없습니다.");
  }
  const blockingDiagnostics = Array.isArray(seedInput.diagnostics)
    ? seedInput.diagnostics.filter((item) => item.level === "error")
    : [];
  if (blockingDiagnostics.length > 0) {
    throw new Error(`seed 입력 JSON에 error 진단이 ${blockingDiagnostics.length}건 있어 SQL을 생성하지 않습니다.`);
  }
}

function readJsonFile(filePath) {
  return JSON.parse(fs.readFileSync(filePath, "utf8").replace(/^\uFEFF/, ""));
}

function resolveOutputPath(outputPath) {
  if (outputPath) {
    return path.resolve(process.cwd(), outputPath);
  }
  return path.resolve(process.cwd(), "reports", `postgres-seed-${formatTimestamp(new Date())}.sql`);
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

function printSummary(seedInput, outputPath) {
  console.log("PostgreSQL seed SQL 생성 결과");
  console.log(`- 출력 파일: ${outputPath}`);
  for (const table of seedInput.tableOrder) {
    const count = Array.isArray(seedInput.rows?.[table]) ? seedInput.rows[table].length : 0;
    console.log(`- ${table}: ${count}건`);
  }
}

main().catch((error) => {
  console.error("PostgreSQL seed SQL 생성 중 오류가 발생했습니다.");
  console.error(error);
  process.exitCode = 1;
});
