"use strict";

const fs = require("fs");
const path = require("path");

const {
  resolveFirebaseCiToken,
  resolveFirebaseOAuthClientSecret,
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

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printUsage();
    return;
  }

  const repoRoot = resolveRepoRoot();
  const repository = resolveGitHubRepository(options.repo);
  const projectId = options.projectId || resolveProjectId();
  const firebaseToken = options.firebaseToken || resolveFirebaseCiToken();
  const firebaseOauthClientSecret =
    options.firebaseOauthClientSecret || resolveFirebaseOAuthClientSecret();
  const googleServicesJson = readRequiredFile(path.join(repoRoot, "app", "google-services.json"));
  const firebasercJson = readRequiredFile(path.join(repoRoot, ".firebaserc"));

  if (!projectId) {
    throw new Error("Firebase 프로젝트 ID를 찾지 못했습니다. .firebaserc 또는 app/google-services.json을 확인해 주세요.");
  }
  if (!firebaseToken) {
    throw new Error("GitHub Actions에 넣을 FIREBASE_TOKEN을 찾지 못했습니다. `firebase login:ci` 토큰 또는 로컬 firebase 로그인 상태를 확인해 주세요.");
  }
  if (requiresOAuthClientSecret(firebaseToken) && !firebaseOauthClientSecret) {
    throw new Error(
        "refresh token 기반 FIREBASE_TOKEN을 쓰려면 FIREBASE_OAUTH_CLIENT_SECRET이 필요합니다. " +
        "환경 변수 또는 local.properties의 firebaseOauthClientSecret 값을 확인해 주세요.",
    );
  }

  if (!options.skipAccessCheck) {
    assertRepositoryAccess(repository);
  }

  const ghLogin = getGhLogin();
  const summary = [
    `저장소: ${repository}`,
    `gh 계정: ${ghLogin || "확인 불가"}`,
    `Firebase 프로젝트: ${projectId}`,
    `워크플로: ${options.workflow}`,
    `접근 점검 생략: ${options.skipAccessCheck ? "예" : "아니오"}`,
    `워크플로 실행: ${options.dispatch ? "예" : "아니오"}`,
    `드라이런: ${options.dryRun ? "예" : "아니오"}`,
  ];
  process.stdout.write(`${summary.join("\n")}\n`);

  if (options.dryRun) {
    process.stdout.write("설정 예정 항목:\n");
    process.stdout.write("- secrets.FIREBASE_TOKEN\n");
    if (requiresOAuthClientSecret(firebaseToken)) {
      process.stdout.write("- secrets.FIREBASE_OAUTH_CLIENT_SECRET\n");
    }
    process.stdout.write("- secrets.GOOGLE_SERVICES_JSON\n");
    process.stdout.write("- secrets.FIREBASERC_JSON\n");
    process.stdout.write("- vars.FIREBASE_PROJECT_ID\n");
    return;
  }

  setRepositorySecret(repository, "FIREBASE_TOKEN", firebaseToken);
  if (requiresOAuthClientSecret(firebaseToken)) {
    setRepositorySecret(
        repository,
        "FIREBASE_OAUTH_CLIENT_SECRET",
        firebaseOauthClientSecret,
    );
  }
  setRepositorySecret(repository, "GOOGLE_SERVICES_JSON", googleServicesJson);
  setRepositorySecret(repository, "FIREBASERC_JSON", firebasercJson);
  setRepositoryVariable(repository, "FIREBASE_PROJECT_ID", projectId);

  process.stdout.write("GitHub Actions 시크릿/변수 반영 완료\n");

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
    firebaseToken: "",
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
      case "--firebase-token":
        options.firebaseToken = requireValue(args, ++index, arg);
        break;
      case "--firebase-oauth-client-secret":
        options.firebaseOauthClientSecret = requireValue(args, ++index, arg);
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
    "  --firebase-token <token>     GitHub secret으로 넣을 Firebase CI 토큰",
    "  --firebase-oauth-client-secret <value>  refresh token 교환용 OAuth client secret",
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

function requiresOAuthClientSecret(token) {
  const trimmed = `${token || ""}`.trim();
  return Boolean(trimmed) && !trimmed.startsWith("ya29.");
}

main().catch((error) => {
  process.stderr.write(`${error.message}\n`);
  process.exit(1);
});
