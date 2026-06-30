#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const {loadAppNavigationEvidence} = require("./lib/app-navigation-evidence");
const {MANAGED_COLLECTIONS} = require("./lib/baseline-config");
const {validateBackupSnapshot} = require("./lib/backup-validator");
const {createCliContext} = require("./lib/firebase-toolkit");
const {
  buildDiffSummary,
  buildRoleReadiness,
  loadBackupSnapshot,
  loadOperationsSnapshot,
  renderOperationsReportHtml,
  resolveAppNavigationEvidencePath,
  resolveReportOutputPath,
  writeReportFile,
} = require("./lib/operations-report");

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }

  const context = await createCliContext();
  const snapshot = await loadOperationsSnapshot(context);
  const readiness = buildRoleReadiness(snapshot);
  const reportsRoot = path.resolve(process.cwd(), "reports");

  let backupSnapshot = null;
  let backupValidation = null;
  let diffSummary = null;
  let backupPath = "";
  let appEvidence = null;

  if (options.filePath) {
    backupPath = path.resolve(process.cwd(), options.filePath);
    backupSnapshot = loadBackupSnapshot(backupPath);
    backupValidation = validateBackupSnapshot(backupSnapshot, MANAGED_COLLECTIONS);
    if (backupValidation.errors.length === 0) {
      diffSummary = buildDiffSummary(backupSnapshot, snapshot);
    }
  }

  const appEvidencePath = resolveAppNavigationEvidencePath(reportsRoot, options.appEvidencePath);
  if (appEvidencePath) {
    appEvidence = loadAppNavigationEvidence(appEvidencePath);
  }

  const reportPath = resolveReportOutputPath(options.outputPath);
  const summaryPath = resolveSummaryOutputPath(options.summaryPath, reportPath);
  const reportHtml = renderOperationsReportHtml(snapshot, readiness, diffSummary, {
    appEvidence,
    reportPath,
  });
  writeReportFile(reportPath, reportHtml);

  const summary = buildWorkflowSummary({
    appEvidence,
    snapshot,
    readiness,
    backupPath,
    backupValidation,
    diffSummary,
    reportPath,
    summaryPath,
  });
  writeSummaryFile(summaryPath, summary);

  if (options.json) {
    console.log(JSON.stringify(summary, null, 2));
  } else {
    printSummary(summary);
  }

  if (options.strict && summary.overallStatus !== "ready") {
    process.exitCode = 1;
  }
}

function parseOptions(args) {
  const appEvidenceIndex = args.indexOf("--app-evidence");
  const fileIndex = args.indexOf("--file");
  const outputIndex = args.indexOf("--output");
  const summaryIndex = args.indexOf("--summary");
  return {
    appEvidencePath: appEvidenceIndex >= 0 ?
      args[appEvidenceIndex + 1] :
      sanitizeEnvOption(process.env.BODEUL_WORKFLOW_APP_EVIDENCE_PATH),
    filePath: fileIndex >= 0 ?
      args[fileIndex + 1] :
      sanitizeEnvOption(process.env.BODEUL_WORKFLOW_FILE_PATH),
    help: args.includes("--help") || args.includes("-h"),
    json: args.includes("--json"),
    outputPath: outputIndex >= 0 ? args[outputIndex + 1] : "",
    strict: args.includes("--strict"),
    summaryPath: summaryIndex >= 0 ? args[summaryIndex + 1] : "",
  };
}

function sanitizeEnvOption(value) {
  if (value === null || value === undefined) {
    return "";
  }
  return String(value).trim();
}

function printHelp() {
  console.log("보들 Firebase 운영 워크플로 스크립트");
  console.log("");
  console.log("사용법");
  console.log("  node run-operations-workflow.js");
  console.log("  node run-operations-workflow.js --file backups/firestore-backup.json");
  console.log("  node run-operations-workflow.js --file backups/firestore-backup.json --strict");
  console.log("  node run-operations-workflow.js --app-evidence reports/app-navigation-evidence-latest.json");
  console.log("  node run-operations-workflow.js --output reports/custom-report.html --summary reports/custom-summary.json");
  console.log("");
  console.log("기본 동작");
  console.log("- 현재 Firebase 상태를 읽어 역할별 화면 진입 점검을 수행합니다.");
  console.log("- HTML 운영 리포트와 JSON 요약 파일을 함께 생성합니다.");
  console.log("- --file을 주면 백업 검증과 현재 상태 diff를 함께 수행합니다.");
  console.log("- --app-evidence를 주거나 기본 증적 파일이 있으면 앱 화면 증적도 함께 반영합니다.");
}

function resolveSummaryOutputPath(summaryPath, reportPath) {
  if (summaryPath) {
    return path.resolve(process.cwd(), summaryPath);
  }

  if (reportPath.endsWith(".html")) {
    return reportPath.replace(/\.html$/i, ".json").replace("operations-report", "operations-summary");
  }

  const directory = path.dirname(reportPath);
  const fileName = path.basename(reportPath).replace("operations-report", "operations-summary");
  return path.join(directory, `${fileName}.json`);
}

function buildWorkflowSummary({
  appEvidence,
  snapshot,
  readiness,
  backupPath,
  backupValidation,
  diffSummary,
  reportPath,
  summaryPath,
}) {
  const readyRoleCount = readiness.roles.filter((role) => role.ready).length;
  const passedScenarioCount = readiness.scenarios.filter((scenario) => scenario.pass).length;
  const allReady = readyRoleCount === readiness.roles.length;
  const allScenariosPass = passedScenarioCount === readiness.scenarios.length;
  const hasBackupErrors = Boolean(backupValidation && backupValidation.errors.length > 0);

  return {
    generatedAt: new Date().toISOString(),
    projectId: snapshot.projectId,
    overallStatus: allReady && allScenariosPass && !hasBackupErrors ? "ready" : "needs_attention",
    reportPath,
    summaryPath,
    backupPath: backupPath || "",
    collectionCounts: snapshot.collectionCounts,
    readiness: {
      readyRoleCount,
      totalRoles: readiness.roles.length,
      passedScenarioCount,
      totalScenarios: readiness.scenarios.length,
      roles: readiness.roles,
      scenarios: readiness.scenarios,
    },
    backupValidation: backupValidation ? {
      errorCount: backupValidation.errors.length,
      warningCount: backupValidation.warnings.length,
      errors: backupValidation.errors,
      warnings: backupValidation.warnings,
    } : null,
    diffSummary: diffSummary ? {
      totalAdded: diffSummary.totalAdded,
      totalRemoved: diffSummary.totalRemoved,
      totalChanged: diffSummary.totalChanged,
      collections: diffSummary.collections,
    } : null,
    appEvidence: appEvidence ? {
      filePath: appEvidence.filePath,
      source: appEvidence.source,
      buildVariant: appEvidence.buildVariant,
      device: appEvidence.device,
      summary: appEvidence.summary,
    } : null,
  };
}

function writeSummaryFile(summaryPath, summary) {
  fs.mkdirSync(path.dirname(summaryPath), {recursive: true});
  fs.writeFileSync(summaryPath, `${JSON.stringify(summary, null, 2)}\n`, "utf8");
}

function printSummary(summary) {
  console.log("보들 Firebase 운영 워크플로");
  console.log(`- 프로젝트: ${summary.projectId}`);
  console.log(`- 상태: ${summary.overallStatus === "ready" ? "준비됨" : "확인 필요"}`);
  console.log(`- 역할 준비도: ${summary.readiness.readyRoleCount}/${summary.readiness.totalRoles}`);
  console.log(
      `- 샘플 시나리오: ${summary.readiness.passedScenarioCount}/${summary.readiness.totalScenarios}`,
  );
  if (summary.backupValidation) {
    console.log(
        `- 백업 검증: 오류 ${summary.backupValidation.errorCount} / 경고 ${summary.backupValidation.warningCount}`,
    );
  }
  if (summary.diffSummary) {
    console.log(
        `- 백업 diff: 추가 ${summary.diffSummary.totalAdded} / 삭제 ${summary.diffSummary.totalRemoved} / 변경 ${summary.diffSummary.totalChanged}`,
    );
  }
  if (summary.appEvidence) {
    console.log(
        `- 앱 화면 증적: 통과 ${summary.appEvidence.summary.passedCount} / 경고 ${summary.appEvidence.summary.warningCount} / 실패 ${summary.appEvidence.summary.failedCount}`,
    );
  }
  console.log(`- HTML 리포트: ${summary.reportPath}`);
  console.log(`- JSON 요약: ${summary.summaryPath}`);
}

main().catch((error) => {
  console.error("운영 워크플로 스크립트 실행 중 오류가 발생했습니다.");
  console.error(error);
  process.exitCode = 1;
});
