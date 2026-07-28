"use strict";

const fs = require("fs");
const path = require("path");

const {
  resolveProjectId,
} = require("../firebase/lib/firebase-toolkit");
const {
  assertRepositoryAccess,
  getGhLogin,
  resolveGitHubRepository,
  setRepositorySecret,
  setRepositoryVariable,
  triggerWorkflow,
} = require("./lib/github-toolkit");

const DEFAULT_WORKFLOW = "android-preflight.yml";
const DEFAULT_PROJECT_ID = "bodeul-dev";
const DEFAULT_WORKLOAD_IDENTITY_PROVIDER =
  "projects/533563500316/locations/global/workloadIdentityPools/github-actions/providers/bodeul-firebase-preflight";
const DEFAULT_SERVICE_ACCOUNT =
  "bodeul-firebase-preflight@bodeul-dev.iam.gserviceaccount.com";

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printUsage();
    return;
  }

  const repoRoot = resolveRepoRoot();
  const repository = resolveGitHubRepository(options.repo);
  const projectId = options.projectId ||
    process.env.FIREBASE_PROJECT_ID ||
    resolveProjectId() ||
    DEFAULT_PROJECT_ID;
  const workloadIdentityProvider = options.workloadIdentityProvider ||
    process.env.FIREBASE_WORKLOAD_IDENTITY_PROVIDER ||
    DEFAULT_WORKLOAD_IDENTITY_PROVIDER;
  const serviceAccount = options.serviceAccount ||
    process.env.FIREBASE_CI_SERVICE_ACCOUNT ||
    DEFAULT_SERVICE_ACCOUNT;

  if (!projectId) {
    throw new Error("Firebase 프로젝트 ID를 찾지 못했습니다. .firebaserc 또는 app/google-services.json을 확인해 주세요.");
  }
  if (!workloadIdentityProvider || !serviceAccount) {
    throw new Error("Firebase WIF provider와 CI 서비스 계정 값이 필요합니다.");
  }

  if (!options.skipAccessCheck) {
    assertRepositoryAccess(repository);
  }

  const ghLogin = getGhLogin();
  const summary = [
    `저장소: ${repository}`,
    `gh 계정: ${ghLogin || "확인 불가"}`,
    `Firebase 프로젝트: ${projectId}`,
    `WIF provider: ${workloadIdentityProvider}`,
    `CI 서비스 계정: ${serviceAccount}`,
    `워크플로: ${options.workflow}`,
    `접근 점검 생략: ${options.skipAccessCheck ? "예" : "아니오"}`,
    `워크플로 실행: ${options.dispatch ? "예" : "아니오"}`,
    `드라이런: ${options.dryRun ? "예" : "아니오"}`,
  ];
  process.stdout.write(`${summary.join("\n")}\n`);

  if (options.dryRun) {
    process.stdout.write("설정 예정 항목:\n");
    process.stdout.write("- secrets.GOOGLE_SERVICES_JSON\n");
    process.stdout.write("- secrets.FIREBASERC_JSON\n");
    process.stdout.write("- vars.FIREBASE_PROJECT_ID\n");
    process.stdout.write("- vars.FIREBASE_WORKLOAD_IDENTITY_PROVIDER\n");
    process.stdout.write("- vars.FIREBASE_CI_SERVICE_ACCOUNT\n");
    return;
  }

  const googleServicesJson = readRequiredFile(path.join(repoRoot, "app", "google-services.json"));
  const firebasercJson = readRequiredFile(path.join(repoRoot, ".firebaserc"));
  setRepositorySecret(repository, "GOOGLE_SERVICES_JSON", googleServicesJson);
  setRepositorySecret(repository, "FIREBASERC_JSON", firebasercJson);
  setRepositoryVariable(repository, "FIREBASE_PROJECT_ID", projectId);
  setRepositoryVariable(
      repository,
      "FIREBASE_WORKLOAD_IDENTITY_PROVIDER",
      workloadIdentityProvider,
  );
  setRepositoryVariable(repository, "FIREBASE_CI_SERVICE_ACCOUNT", serviceAccount);

  process.stdout.write("GitHub Actions WIF 변수와 정적 설정 반영 완료\n");

  if (!options.dispatch) {
    return;
  }

  triggerWorkflow(repository, options.workflow, {
    require_firebase_ops: "true",
    backup_file: options.backupFile,
    app_evidence_path: options.appEvidence,
  });
  process.stdout.write("GitHub Actions 워크플로 실행 요청 완료\n");
}

function parseArgs(args) {
  const options = {
    repo: "",
    projectId: "",
    workloadIdentityProvider: "",
    serviceAccount: "",
    workflow: DEFAULT_WORKFLOW,
    backupFile: "",
    appEvidence: "tools/firebase/templates/app-navigation-evidence.sample.json",
    dryRun: false,
    dispatch: false,
    skipAccessCheck: false,
    help: false,
  };

  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    switch (arg) {
      case "--repo":
        options.repo = requireValue(args, ++index, arg);
        break;
      case "--project-id":
        options.projectId = requireValue(args, ++index, arg);
        break;
      case "--workload-identity-provider":
        options.workloadIdentityProvider = requireValue(args, ++index, arg);
        break;
      case "--service-account":
        options.serviceAccount = requireValue(args, ++index, arg);
        break;
      case "--workflow":
        options.workflow = requireValue(args, ++index, arg);
        break;
      case "--backup-file":
        options.backupFile = requireValue(args, ++index, arg);
        break;
      case "--app-evidence":
        options.appEvidence = requireValue(args, ++index, arg);
        break;
      case "--dispatch":
        options.dispatch = true;
        break;
      case "--dry-run":
        options.dryRun = true;
        break;
      case "--skip-access-check":
        options.skipAccessCheck = true;
        break;
      case "--help":
      case "-h":
        options.help = true;
        break;
      default:
        throw new Error(`알 수 없는 옵션입니다: ${arg}`);
    }
  }

  return options;
}

function requireValue(args, index, optionName) {
  if (index >= args.length) {
    throw new Error(`${optionName} 값이 필요합니다.`);
  }
  return args[index];
}

function readRequiredFile(filePath) {
  if (!fs.existsSync(filePath)) {
    throw new Error(`필수 파일이 없습니다: ${filePath}`);
  }
  return fs.readFileSync(filePath, "utf8");
}

function resolveRepoRoot() {
  return path.resolve(__dirname, "..", "..");
}

function printUsage() {
  process.stdout.write([
    "사용법:",
    "  node tools/github/configure-actions-firebase.js [옵션]",
    "",
    "옵션:",
    "  --repo <owner/repo>          GitHub 저장소. 기본값은 origin 원격",
    "  --project-id <id>            Firebase 프로젝트 ID",
    "  --workload-identity-provider <name>  Firebase Preflight WIF provider resource name",
    "  --service-account <email>    Firebase Preflight 서비스 계정",
    "  --workflow <file>            실행할 workflow 파일명",
    "  --backup-file <path>         workflow_dispatch 입력 backup_file 값",
    "  --app-evidence <path>        workflow_dispatch 입력 app_evidence_path 값",
    "  --dispatch                   시크릿 반영 후 workflow_dispatch 실행",
    "  --dry-run                    반영 예정 값만 출력",
    "  --skip-access-check          저장소 API 접근 점검 생략",
    "  --help                       도움말 출력",
    "",
    "예시:",
    "  node tools/github/configure-actions-firebase.js --repo bodeul110/Bodeul --dry-run",
    "  node tools/github/configure-actions-firebase.js --repo bodeul110/Bodeul --dispatch",
  ].join("\n"));
}

main().catch((error) => {
  process.stderr.write(`${error.message}\n`);
  process.exit(1);
});
