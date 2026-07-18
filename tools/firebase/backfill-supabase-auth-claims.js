const {execFileSync} = require("node:child_process");

const {resolveProjectId} = require("./lib/firebase-toolkit");
const {
  needsSupabaseRole,
  serializeClaims,
} = require("./lib/supabase-auth-claims");

const PROJECT_ID_PATTERN = /^[a-z][a-z0-9-]{4,28}[a-z0-9]$/;

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const projectId = validateProjectId(options.projectId || resolveProjectId());
  const accessToken = loadAccessToken();
  const users = await listUsers(projectId, accessToken);
  const targets = users.filter((user) => needsSupabaseRole(user.customAttributes));

  printPlan(projectId, users.length, targets.length, options.apply);
  if (!options.apply || targets.length === 0) {
    return;
  }

  let updatedCount = 0;
  for (const user of targets) {
    await updateClaims(projectId, accessToken, user);
    updatedCount += 1;
  }

  const remainingUsers = await listUsers(projectId, accessToken);
  const remainingCount = remainingUsers.filter(
      (user) => needsSupabaseRole(user.customAttributes),
  ).length;

  console.log("");
  console.log(`적용 완료: ${updatedCount}명`);
  console.log(`미적용 사용자: ${remainingCount}명`);
  if (remainingCount !== 0) {
    throw new Error("일부 사용자에게 Supabase 인증 역할이 적용되지 않았습니다.");
  }
}

async function listUsers(projectId, accessToken) {
  const users = [];
  let nextPageToken = "";

  do {
    const url = new URL(
        `https://identitytoolkit.googleapis.com/v1/projects/${encodeURIComponent(projectId)}/accounts:batchGet`,
    );
    url.searchParams.set("maxResults", "1000");
    if (nextPageToken) {
      url.searchParams.set("nextPageToken", nextPageToken);
    }

    const response = await fetch(url, {
      headers: buildGoogleHeaders(projectId, accessToken),
    });
    const body = await readJson(response);
    if (!response.ok) {
      throw new Error(formatApiError("Firebase 사용자 조회", response.status, body));
    }

    users.push(...(Array.isArray(body.users) ? body.users : []));
    nextPageToken = `${body.nextPageToken ?? ""}`.trim();
  } while (nextPageToken);

  return users;
}

async function updateClaims(projectId, accessToken, user) {
  const localId = `${user.localId ?? ""}`.trim();
  if (!localId) {
    throw new Error("Firebase 사용자 UID가 비어 있습니다.");
  }

  const response = await fetch(
      "https://identitytoolkit.googleapis.com/v1/accounts:update",
      {
        method: "POST",
        headers: {
          ...buildGoogleHeaders(projectId, accessToken),
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          localId,
          targetProjectId: projectId,
          customAttributes: serializeClaims(user.customAttributes),
        }),
      },
  );
  const body = await readJson(response);
  if (!response.ok) {
    throw new Error(formatApiError("Firebase custom claim 갱신", response.status, body));
  }
}

function buildGoogleHeaders(projectId, accessToken) {
  return {
    Authorization: `Bearer ${accessToken}`,
    "x-goog-user-project": projectId,
  };
}

function loadAccessToken() {
  const envToken = `${process.env.GCLOUD_ACCESS_TOKEN ?? ""}`.trim();
  if (envToken) {
    return envToken;
  }

  try {
    const executable = process.platform === "win32" ?
      (process.env.ComSpec || "cmd.exe") :
      "gcloud";
    const args = process.platform === "win32" ?
      ["/d", "/s", "/c", "gcloud.cmd auth print-access-token"] :
      ["auth", "print-access-token"];
    return execFileSync(executable, args, {
      encoding: "utf8",
      windowsHide: true,
      stdio: ["ignore", "pipe", "pipe"],
    }).trim();
  } catch (error) {
    throw new Error(
        "Google Cloud 로그인이 필요합니다. gcloud auth login 후 다시 실행하세요.",
    );
  }
}

function parseArgs(args) {
  const options = {apply: false, projectId: ""};
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--apply") {
      options.apply = true;
    } else if (arg === "--project") {
      options.projectId = requireValue(args, ++index, arg);
    } else {
      throw new Error(`알 수 없는 옵션입니다: ${arg}`);
    }
  }
  return options;
}

function requireValue(args, index, option) {
  const value = `${args[index] ?? ""}`.trim();
  if (!value) {
    throw new Error(`${option} 값이 필요합니다.`);
  }
  return value;
}

function validateProjectId(value) {
  const projectId = `${value ?? ""}`.trim();
  if (!PROJECT_ID_PATTERN.test(projectId)) {
    throw new Error("Firebase 프로젝트 ID 형식이 올바르지 않습니다.");
  }
  return projectId;
}

async function readJson(response) {
  const text = await response.text();
  if (!text) {
    return {};
  }
  try {
    return JSON.parse(text);
  } catch (error) {
    return {error: {message: "JSON이 아닌 응답을 받았습니다."}};
  }
}

function formatApiError(action, status, body) {
  const message = `${body?.error?.message ?? "알 수 없는 오류"}`.trim();
  return `${action} 실패 (${status}): ${message}`;
}

function printPlan(projectId, totalCount, targetCount, apply) {
  console.log("Firebase Supabase 인증 역할 백필");
  console.log(`프로젝트: ${projectId}`);
  console.log(`전체 사용자: ${totalCount}명`);
  console.log(`변경 대상: ${targetCount}명`);
  console.log(`실행 모드: ${apply ? "apply" : "dry-run"}`);
  if (!apply && targetCount > 0) {
    console.log("적용하려면 --apply 옵션을 명시하세요.");
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
