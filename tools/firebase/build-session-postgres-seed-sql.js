#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const {
  buildSessionSeedPlan,
  buildSessionSeedSql,
} = require("./lib/session-postgres-seed");

function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }
  if (!options.filePath) {
    throw new Error("--file 옵션으로 Firestore 백업 JSON 경로를 지정해야 합니다.");
  }

  const backupPath = path.resolve(process.cwd(), options.filePath);
  const snapshot = JSON.parse(fs.readFileSync(backupPath, "utf8").replace(/^\uFEFF/, ""));
  const plan = buildSessionSeedPlan(snapshot);
  printValidationSummary(plan);

  if (plan.status !== "passed") {
    process.exitCode = 1;
    return;
  }
  if (options.check) {
    return;
  }

  const sql = buildSessionSeedSql(plan, {rollback: options.rollback});
  const outputPath = resolveOutputPath(options.outputPath, options.rollback);
  fs.mkdirSync(path.dirname(outputPath), {recursive: true});
  fs.writeFileSync(outputPath, sql, "utf8");
  console.log(`- SQL 파일: ${outputPath}`);
  console.log("- 주의: 생성된 SQL에는 개인정보가 포함되므로 커밋하거나 공유하지 않습니다.");
}

function parseOptions(args) {
  const fileIndex = args.indexOf("--file");
  const outputIndex = args.indexOf("--output");
  return {
    filePath: fileIndex >= 0 ? args[fileIndex + 1] : "",
    outputPath: outputIndex >= 0 ? args[outputIndex + 1] : "",
    rollback: args.includes("--rollback"),
    check: args.includes("--check"),
    help: args.includes("--help") || args.includes("-h"),
  };
}

function resolveOutputPath(outputPath, rollback) {
  if (outputPath) {
    return path.resolve(process.cwd(), outputPath);
  }
  const suffix = rollback ? "rollback" : "apply";
  return path.resolve(
      process.cwd(),
      "reports",
      `companion-session-postgres-${suffix}-${formatTimestamp(new Date())}.sql`,
  );
}

function printValidationSummary(plan) {
  console.log("동행 세션 PostgreSQL seed 검증 결과");
  console.log(`- 상태: ${plan.status}`);
  console.log(`- 동행 세션: ${plan.rowCounts.companion_sessions || 0}건`);
  console.log(`- 세션 리포트: ${plan.rowCounts.session_reports || 0}건`);
  console.log(`- 후속 처리: ${plan.rowCounts.appointment_follow_ups || 0}건`);
  console.log(`- 오류: ${plan.errors.length}건`);
  for (const error of plan.errors) {
    console.log(`  - ${error.path} ${error.field}: ${error.message}`);
  }
}

function printHelp() {
  console.log("Firestore 동행 세션 PostgreSQL seed SQL 생성기");
  console.log("");
  console.log("사용법:");
  console.log("  node build-session-postgres-seed-sql.js --file backups/firestore-backup.json --check");
  console.log("  node build-session-postgres-seed-sql.js --file backups/firestore-backup.json");
  console.log("  node build-session-postgres-seed-sql.js --file backups/firestore-backup.json --rollback");
}

function formatTimestamp(date) {
  const parts = [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0"),
    String(date.getHours()).padStart(2, "0"),
    String(date.getMinutes()).padStart(2, "0"),
    String(date.getSeconds()).padStart(2, "0"),
  ];
  return `${parts[0]}${parts[1]}${parts[2]}-${parts[3]}${parts[4]}${parts[5]}`;
}

try {
  main();
} catch (error) {
  console.error("동행 세션 PostgreSQL seed 처리 중 오류가 발생했습니다.");
  console.error(error instanceof Error ? error.message : error);
  process.exitCode = 1;
}
