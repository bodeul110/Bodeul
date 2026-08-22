const fs = require("fs");
const path = require("path");
const {spawnSync} = require("child_process");

const repoRoot = path.resolve(__dirname, "..", "..");
const toolsRoot = path.join(repoRoot, "tools", "firebase");
const firebaseCli = path.join(
    toolsRoot,
    "node_modules",
    "firebase-tools",
    "lib",
    "bin",
    "firebase.js",
);
const testFile = path.join(
    repoRoot,
    "functions",
    "test",
    "retention-emulator.test.js",
);
const projectId = "bodeul-retention-emulator";

if (!fs.existsSync(firebaseCli)) {
  console.error("firebase-tools를 찾지 못했습니다. tools/firebase에서 npm ci를 먼저 실행해 주세요.");
  process.exit(1);
}

const command = `"${process.execPath}" --test "${testFile}"`;
const result = spawnSync(
    process.execPath,
    [
      firebaseCli,
      "emulators:exec",
      "--only",
      "firestore,storage",
      "--project",
      projectId,
      command,
    ],
    {
      cwd: repoRoot,
      env: {
        ...process.env,
        RETENTION_EMULATOR_TEST_REQUIRED: "true",
      },
      stdio: "inherit",
    },
);

if (result.error) {
  console.error(result.error);
}
process.exit(result.status ?? 1);
