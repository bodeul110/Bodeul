"use strict";

const {spawnSync} = require("child_process");
const path = require("path");

function resolveRepoRoot() {
  return path.resolve(__dirname, "..", "..", "..");
}

function runCommand(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: options.cwd || resolveRepoRoot(),
    encoding: "utf8",
    input: options.input,
    stdio: ["pipe", "pipe", "pipe"],
  });

  if (result.error) {
    throw result.error;
  }

  if (options.allowFailure || result.status === 0) {
    return {
      status: result.status || 0,
      stdout: result.stdout || "",
      stderr: result.stderr || "",
    };
  }

  throw new Error([
    `${command} ${args.join(" ")} 실행 실패`,
    result.stderr || result.stdout || `종료 코드 ${result.status}`,
  ].filter(Boolean).join(": "));
}

function resolveGitHubRepository(explicitRepository) {
  const repo = sanitizeText(explicitRepository);
  if (repo) {
    return repo;
  }

  const remote = runCommand("git", ["remote", "get-url", "origin"]).stdout.trim();
  const parsedRepository = parseGitHubRepository(remote);
  if (!parsedRepository) {
    throw new Error(`origin 원격 URL에서 GitHub 저장소를 해석하지 못했습니다: ${remote}`);
  }
  return parsedRepository;
}

function parseGitHubRepository(remoteUrl) {
  const remote = sanitizeText(remoteUrl);
  const match = remote.match(/github\.com[:/](.+?)(?:\.git)?$/i);
  if (!match) {
    return "";
  }
  return sanitizeText(match[1]);
}

function getGhLogin() {
  const result = runCommand("gh", ["api", "user"], {allowFailure: true});
  if (result.status !== 0) {
    return "";
  }

  try {
    return sanitizeText(JSON.parse(result.stdout).login);
  } catch (error) {
    return "";
  }
}

function assertRepositoryAccess(repository) {
  const result = runCommand("gh", ["api", `repos/${repository}`], {allowFailure: true});
  if (result.status === 0) {
    return;
  }

  const ghLogin = getGhLogin();
  const loginLabel = ghLogin ? `현재 gh 계정은 ${ghLogin}` : "현재 gh 계정을 확인하지 못했습니다";
  throw new Error(`${loginLabel}. ${repository} 저장소 API 접근이 되지 않습니다. 올바른 계정으로 \`gh auth login\` 또는 \`gh auth switch\` 후 다시 실행해 주세요.`);
}

function setRepositorySecret(repository, name, value) {
  runCommand("gh", ["secret", "set", name, "--repo", repository], {
    input: `${value}`,
  });
}

function setRepositoryVariable(repository, name, value) {
  runCommand("gh", ["variable", "set", name, "--repo", repository, "--body", `${value}`]);
}

function triggerWorkflow(repository, workflowFile, fields = {}) {
  const args = ["workflow", "run", workflowFile, "--repo", repository];

  Object.entries(fields).forEach(([key, value]) => {
    const text = sanitizeText(value);
    if (!text) {
      return;
    }
    args.push("--field", `${key}=${text}`);
  });

  runCommand("gh", args);
}

function sanitizeText(value) {
  if (value === null || value === undefined) {
    return "";
  }
  return String(value).trim();
}

module.exports = {
  assertRepositoryAccess,
  getGhLogin,
  resolveGitHubRepository,
  triggerWorkflow,
  setRepositorySecret,
  setRepositoryVariable,
};
