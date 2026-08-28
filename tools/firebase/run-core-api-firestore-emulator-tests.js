const path = require("node:path");
const {spawnSync} = require("node:child_process");

const repositoryRoot = path.resolve(__dirname, "..", "..");
const firebaseCli = require.resolve("firebase-tools/lib/bin/firebase");
const gradleCommand = process.platform === "win32"
  ? "core-api\\gradlew.bat -p core-api firestoreEmulatorTest --console=plain"
  : "./core-api/gradlew -p core-api firestoreEmulatorTest --console=plain";

const result = spawnSync(
    process.execPath,
    [
      firebaseCli,
      "emulators:exec",
      "--only",
      "firestore",
      "--project",
      "demo-bodeul-account-deletion",
      gradleCommand,
    ],
    {
      cwd: repositoryRoot,
      env: {
        ...process.env,
        GCLOUD_PROJECT: "demo-bodeul-account-deletion",
      },
      stdio: "inherit",
    },
);

if (result.error) {
  throw result.error;
}
process.exitCode = result.status ?? 1;
