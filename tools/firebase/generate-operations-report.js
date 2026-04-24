#!/usr/bin/env node

const path = require("path");

const {loadAppNavigationEvidence} = require("./lib/app-navigation-evidence");
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
  const diffSummary = options.filePath ?
    buildDiffSummary(loadBackupSnapshot(path.resolve(process.cwd(), options.filePath)), snapshot) :
    null;
  const reportsRoot = path.resolve(process.cwd(), "reports");
  const appEvidencePath = resolveAppNavigationEvidencePath(reportsRoot, options.appEvidencePath);
  const appEvidence = appEvidencePath ? loadAppNavigationEvidence(appEvidencePath) : null;

  const outputPath = resolveReportOutputPath(options.outputPath);
  const html = renderOperationsReportHtml(snapshot, readiness, diffSummary, {
    appEvidence,
    reportPath: outputPath,
  });

  writeReportFile(outputPath, html);

  console.log("보들 운영 리포트를 생성했습니다.");
  console.log(`- 파일: ${outputPath}`);
  console.log(`- 역할 준비도: ${readiness.roles.filter((role) => role.ready).length}/${readiness.roles.length}`);
  console.log(`- 샘플 시나리오: ${readiness.scenarios.filter((scenario) => scenario.pass).length}/${readiness.scenarios.length}`);
  if (diffSummary) {
    console.log(`- 백업 diff: 추가 ${diffSummary.totalAdded} / 삭제 ${diffSummary.totalRemoved} / 변경 ${diffSummary.totalChanged}`);
  }
  if (appEvidence) {
    console.log(
        `- 앱 화면 증적: 통과 ${appEvidence.summary.passedCount} / 경고 ${appEvidence.summary.warningCount} / 실패 ${appEvidence.summary.failedCount}`,
    );
  }
}

function parseOptions(args) {
  const appEvidenceIndex = args.indexOf("--app-evidence");
  const fileIndex = args.indexOf("--file");
  const outputIndex = args.indexOf("--output");
  return {
    appEvidencePath: appEvidenceIndex >= 0 ? args[appEvidenceIndex + 1] : "",
    filePath: fileIndex >= 0 ? args[fileIndex + 1] : "",
    help: args.includes("--help") || args.includes("-h"),
    outputPath: outputIndex >= 0 ? args[outputIndex + 1] : "",
  };
}

function printHelp() {
  console.log("보들 Firebase 운영 리포트 생성 스크립트");
  console.log("");
  console.log("사용법");
  console.log("  node generate-operations-report.js");
  console.log("  node generate-operations-report.js --file backups/firestore-backup.json");
  console.log("  node generate-operations-report.js --app-evidence reports/app-navigation-evidence-latest.json");
  console.log("  node generate-operations-report.js --output reports/custom-report.html");
}

main().catch((error) => {
  console.error("운영 리포트 생성 스크립트 실행 중 오류가 발생했습니다.");
  console.error(error);
  process.exitCode = 1;
});
