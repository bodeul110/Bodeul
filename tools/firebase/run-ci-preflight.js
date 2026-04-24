#!/usr/bin/env node

const path = require("path");
const {spawn} = require("child_process");

const {createCliContext} = require("./lib/firebase-toolkit");

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }

  const repoRoot = path.resolve(__dirname, "..", "..");
  const toolsRoot = path.resolve(__dirname);
  const preflightScriptPath = path.join(toolsRoot, "run-local-preflight.js");
  const preflightArgs = [];

  if (options.filePath) {
    preflightArgs.push("--file", options.filePath);
  }
  if (options.appEvidencePath) {
    preflightArgs.push("--app-evidence", options.appEvidencePath);
  }
  if (options.summaryPath) {
    preflightArgs.push("--summary", options.summaryPath);
  }
  if (options.json) {
    preflightArgs.push("--json");
  }
  if (options.skipBuild) {
    preflightArgs.push("--skip-build");
  }
  if (options.skipTests) {
    preflightArgs.push("--skip-tests");
  }

  let skipWorkflow = options.skipWorkflow;
  if (!skipWorkflow) {
    try {
      await createCliContext();
      console.log("CI 프리플라이트: Firebase 운영 워크플로를 포함합니다.");
    } catch (error) {
      if (options.requireFirebase) {
        throw error;
      }
      skipWorkflow = true;
      console.log("CI 프리플라이트: Firebase 점검 입력이 없어 운영 워크플로를 건너뜁니다.");
      console.log(`- 사유: ${error.message || error}`);
    }
  }

  if (skipWorkflow) {
    preflightArgs.push("--skip-workflow");
  }

  const exitCode = await runNodeScript(preflightScriptPath, preflightArgs, repoRoot);
  process.exitCode = exitCode;
}

function parseOptions(args) {
  const appEvidenceIndex = args.indexOf("--app-evidence");
  const fileIndex = args.indexOf("--file");
  const summaryIndex = args.indexOf("--summary");
  return {
    appEvidencePath: appEvidenceIndex >= 0 ? args[appEvidenceIndex + 1] : "",
    filePath: fileIndex >= 0 ? args[fileIndex + 1] : "",
    help: args.includes("--help") || args.includes("-h"),
    json: args.includes("--json"),
    requireFirebase: args.includes("--require-firebase"),
    skipBuild: args.includes("--skip-build"),
    skipTests: args.includes("--skip-tests"),
    skipWorkflow: args.includes("--skip-workflow"),
    summaryPath: summaryIndex >= 0 ? args[summaryIndex + 1] : "",
  };
}

function printHelp() {
  console.log("보들 CI 프리플라이트 스크립트");
  console.log("");
  console.log("사용법");
  console.log("  node run-ci-preflight.js");
  console.log("  node run-ci-preflight.js --file backups/firestore-backup.json");
  console.log("  node run-ci-preflight.js --require-firebase");
  console.log("  node run-ci-preflight.js --app-evidence templates/app-navigation-evidence.sample.json");
  console.log("");
  console.log("동작");
  console.log("- Firebase 입력이 준비되면 로컬 프리플라이트를 전체 모드로 실행합니다.");
  console.log("- Firebase 입력이 없으면 기본적으로 운영 워크플로를 건너뛰고 빌드/테스트만 실행합니다.");
  console.log("- `--require-firebase`를 주면 Firebase 입력이 없을 때 실패로 종료합니다.");
}

async function runNodeScript(scriptPath, args, cwd) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [scriptPath].concat(args), {
      cwd,
      stdio: "inherit",
      shell: false,
    });

    child.on("error", reject);
    child.on("exit", (exitCode) => {
      resolve(typeof exitCode === "number" ? exitCode : 1);
    });
  });
}

main().catch((error) => {
  console.error("CI 프리플라이트 스크립트 실행 중 오류가 발생했습니다.");
  console.error(error.message || error);
  process.exitCode = 1;
});
