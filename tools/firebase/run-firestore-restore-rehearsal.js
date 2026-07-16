#!/usr/bin/env node

const {spawnSync} = require("child_process");
const fs = require("fs");
const path = require("path");

const {MANAGED_COLLECTIONS} = require("./lib/baseline-config");
const {validateBackupSnapshot} = require("./lib/backup-validator");
const {
  createCliContext,
  getDocument,
  patchDocumentData,
} = require("./lib/firebase-toolkit");

const REHEARSAL_PROJECT_ID = "bodeul-restore-rehearsal";
const STALE_DOCUMENT_PATH = "hospitalGuides/restore-rehearsal-stale";

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }

  const context = await createCliContext();
  assertIsolatedEmulator(context);

  const sourcePath = resolveSourceBackupPath(options.filePath);
  const roundTripPath = path.resolve(
      process.cwd(),
      options.outputPath || "backups/firestore-restore-rehearsal-roundtrip.json",
  );
  const resultPath = path.resolve(
      process.cwd(),
      options.summaryPath || "reports/firestore-restore-rehearsal-summary.json",
  );
  const workflowReportPath = path.resolve(
      process.cwd(),
      "reports/firestore-restore-rehearsal-workflow.html",
  );
  const workflowSummaryPath = path.resolve(
      process.cwd(),
      "reports/firestore-restore-rehearsal-workflow.json",
  );

  const sourceSnapshot = loadSnapshot(sourcePath);
  const sourceValidation = validateBackupSnapshot(sourceSnapshot, MANAGED_COLLECTIONS);
  if (sourceValidation.errors.length > 0) {
    throw new Error(`초기 백업 검증 실패: ${sourceValidation.errors.join(" / ")}`);
  }

  const startedAt = Date.now();
  const steps = [];

  runNodeStep("격리 Emulator 초기 데이터 준비", "restore-firestore-state.js", [
    "--apply",
    "--file",
    sourcePath,
  ], steps);
  runNodeStep("Emulator 상태 백업", "backup-firestore-state.js", [
    "--output",
    roundTripPath,
  ], steps);
  runNodeStep("백업 구조 검증", "validate-firestore-backup.js", [
    "--file",
    roundTripPath,
  ], steps);

  const roundTripSnapshot = loadSnapshot(roundTripPath);
  const mutationTargetPath = resolveMutationTargetPath(roundTripSnapshot);
  await patchDocumentData(context, STALE_DOCUMENT_PATH, {
    rehearsalOnly: true,
    note: "복원 시 삭제되어야 하는 임시 문서",
  });
  await patchDocumentData(context, mutationTargetPath, {
    restoreRehearsalMutation: "복원 시 제거되어야 하는 임시 필드",
  });

  runNodeStep("복원 dry-run", "restore-firestore-state.js", [
    "--file",
    roundTripPath,
  ], steps);
  runNodeStep("복원 apply", "restore-firestore-state.js", [
    "--apply",
    "--file",
    roundTripPath,
  ], steps);

  const staleDocument = await getDocument(context, STALE_DOCUMENT_PATH);
  if (staleDocument) {
    throw new Error("복원 후 임시 문서가 남아 있습니다.");
  }
  const restoredMutationTarget = await getDocument(context, mutationTargetPath);
  if (restoredMutationTarget?.fields?.restoreRehearsalMutation) {
    throw new Error("복원 후 임시 필드가 남아 있습니다.");
  }

  runNodeStep("백업 대비 diff", "diff-firestore-state.js", [
    "--file",
    roundTripPath,
  ], steps);
  runNodeStep("운영 workflow strict", "run-operations-workflow.js", [
    "--file",
    roundTripPath,
    "--strict",
    "--firestore-only",
    "--no-app-evidence",
    "--output",
    workflowReportPath,
    "--summary",
    workflowSummaryPath,
  ], steps);

  const workflowSummary = loadSnapshot(workflowSummaryPath);
  assertZeroDiff(workflowSummary.diffSummary);

  const result = {
    generatedAt: new Date().toISOString(),
    projectId: context.projectId,
    emulatorHost: process.env.FIRESTORE_EMULATOR_HOST,
    sourceBackup: sourcePath,
    roundTripBackup: roundTripPath,
    workflowReport: workflowReportPath,
    workflowSummary: workflowSummaryPath,
    sourceValidation: {
      errorCount: sourceValidation.errors.length,
      warningCount: sourceValidation.warnings.length,
    },
    restoredDocumentCount: countSnapshotDocuments(roundTripSnapshot),
    staleDocumentRemoved: true,
    temporaryFieldRemoved: true,
    diff: {
      added: workflowSummary.diffSummary.totalAdded,
      removed: workflowSummary.diffSummary.totalRemoved,
      changed: workflowSummary.diffSummary.totalChanged,
    },
    readiness: workflowSummary.readiness,
    elapsedMs: Date.now() - startedAt,
    steps,
  };

  fs.mkdirSync(path.dirname(resultPath), {recursive: true});
  fs.writeFileSync(resultPath, `${JSON.stringify(result, null, 2)}\n`, "utf8");

  console.log("");
  console.log("Firestore Emulator 백업/복원 리허설을 완료했습니다.");
  console.log(`- 프로젝트: ${result.projectId}`);
  console.log(`- 복원 문서: ${result.restoredDocumentCount}건`);
  console.log("- diff: 추가 0 / 삭제 0 / 변경 0");
  console.log(`- 역할 준비도: ${result.readiness.readyRoleCount}/${result.readiness.totalRoles}`);
  console.log(`- 샘플 시나리오: ${result.readiness.passedScenarioCount}/${result.readiness.totalScenarios}`);
  console.log(`- 결과: ${resultPath}`);
}

function parseOptions(args) {
  return {
    filePath: readOption(args, "--file"),
    outputPath: readOption(args, "--output"),
    summaryPath: readOption(args, "--summary"),
    help: args.includes("--help") || args.includes("-h"),
  };
}

function readOption(args, name) {
  const index = args.indexOf(name);
  return index >= 0 ? args[index + 1] || "" : "";
}

function resolveSourceBackupPath(filePath) {
  const explicitPath = filePath || process.env.BODEUL_RESTORE_REHEARSAL_FILE;
  if (explicitPath) {
    return assertFile(path.resolve(process.cwd(), explicitPath));
  }

  const backupsRoot = path.resolve(process.cwd(), "backups");
  const candidates = fs.readdirSync(backupsRoot, {withFileTypes: true})
      .filter((entry) => entry.isFile())
      .filter((entry) => /^firestore-backup-.*\.json$/i.test(entry.name))
      .filter((entry) => !entry.name.includes("restore-rehearsal-roundtrip"))
      .map((entry) => {
        const candidatePath = path.join(backupsRoot, entry.name);
        return {candidatePath, modifiedAt: fs.statSync(candidatePath).mtimeMs};
      })
      .sort((left, right) => right.modifiedAt - left.modifiedAt);

  if (candidates.length === 0) {
    throw new Error("리허설 입력 백업이 없습니다. --file로 백업 파일을 지정해 주세요.");
  }
  return candidates[0].candidatePath;
}

function assertFile(filePath) {
  if (!fs.existsSync(filePath) || !fs.statSync(filePath).isFile()) {
    throw new Error(`백업 파일을 찾을 수 없습니다: ${filePath}`);
  }
  return filePath;
}

function loadSnapshot(filePath) {
  return JSON.parse(fs.readFileSync(assertFile(filePath), "utf8"));
}

function assertIsolatedEmulator(context) {
  if (!context.useFirestoreEmulator) {
    throw new Error("이 명령은 FIRESTORE_EMULATOR_HOST가 설정된 격리 환경에서만 실행할 수 있습니다.");
  }
  if (context.projectId !== REHEARSAL_PROJECT_ID) {
    throw new Error(`리허설 project id는 ${REHEARSAL_PROJECT_ID}만 허용합니다.`);
  }
}

function resolveMutationTargetPath(snapshot) {
  for (const collectionName of MANAGED_COLLECTIONS) {
    const document = snapshot.collections?.[collectionName]?.[0];
    if (document?.path) {
      return document.path;
    }
  }
  throw new Error("임시 필드 복원을 검증할 문서가 백업에 없습니다.");
}

function runNodeStep(label, scriptName, args, steps) {
  const startedAt = Date.now();
  console.log("");
  console.log(`[${label}]`);
  const result = spawnSync(process.execPath, [path.join(__dirname, scriptName), ...args], {
    cwd: __dirname,
    env: process.env,
    encoding: "utf8",
  });
  if (result.stdout) {
    process.stdout.write(result.stdout);
  }
  if (result.stderr) {
    process.stderr.write(result.stderr);
  }
  const elapsedMs = Date.now() - startedAt;
  steps.push({label, elapsedMs, exitCode: result.status});
  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    throw new Error(`${label} 실패: exit code ${result.status}`);
  }
}

function assertZeroDiff(diffSummary) {
  if (!diffSummary ||
      diffSummary.totalAdded !== 0 ||
      diffSummary.totalRemoved !== 0 ||
      diffSummary.totalChanged !== 0) {
    throw new Error("복원 후 백업 대비 diff가 0이 아닙니다.");
  }
}

function countSnapshotDocuments(snapshot) {
  return MANAGED_COLLECTIONS.reduce(
      (total, collectionName) => total + (snapshot.collections?.[collectionName]?.length || 0),
      0,
  );
}

function printHelp() {
  console.log("Firestore Emulator 백업/복원 리허설");
  console.log("");
  console.log("사용법:");
  console.log("  node run-firestore-restore-rehearsal.js --file backups/firestore-backup.json");
  console.log("");
  console.log("필수 환경:");
  console.log(`- FIREBASE_PROJECT_ID=${REHEARSAL_PROJECT_ID}`);
  console.log("- FIRESTORE_EMULATOR_HOST=localhost:<port>");
}

main().catch((error) => {
  console.error("Firestore 백업/복원 리허설 중 오류가 발생했습니다.");
  console.error(error);
  process.exitCode = 1;
});
