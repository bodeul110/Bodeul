const assert = require("node:assert/strict");
const test = require("node:test");

const {
  createCliContext,
  lookupAuthUserByEmail,
  resolveFirestoreConnection,
} = require("../lib/firebase-toolkit");

test("Emulator 환경변수가 없으면 운영 Firestore endpoint를 사용한다", () => {
  assert.deepEqual(resolveFirestoreConnection({}), {
    apiOrigin: "https://firestore.googleapis.com",
    useEmulator: false,
  });
});

test("Firestore Emulator context에서 production Auth 호출을 요청 전에 차단한다", async () => {
  await assert.rejects(
      () => lookupAuthUserByEmail({
        projectId: "bodeul-restore-rehearsal",
        accessToken: "owner",
        useFirestoreEmulator: true,
      }, "patient@bodeul.app"),
      /production Firebase Auth endpoint/,
  );
});

test("localhost Firestore Emulator endpoint를 허용한다", () => {
  assert.deepEqual(resolveFirestoreConnection({
    FIRESTORE_EMULATOR_HOST: "127.0.0.1:8080",
  }), {
    apiOrigin: "http://127.0.0.1:8080",
    useEmulator: true,
  });
});

test("원격 또는 protocol 포함 Emulator endpoint를 거부한다", () => {
  assert.throws(
      () => resolveFirestoreConnection({FIRESTORE_EMULATOR_HOST: "example.com:8080"}),
      /localhost/,
  );
  assert.throws(
      () => resolveFirestoreConnection({FIRESTORE_EMULATOR_HOST: "http://127.0.0.1:8080"}),
      /protocol/,
  );
  assert.throws(
      () => resolveFirestoreConnection({FIRESTORE_EMULATOR_HOST: "127.0.0.1:8080/path"}),
      /localhost/,
  );
});

test("Emulator context는 Firebase 로그인 token 없이 격리 자격 증명을 사용한다", async () => {
  const originalProjectId = process.env.FIREBASE_PROJECT_ID;
  const originalEmulatorHost = process.env.FIRESTORE_EMULATOR_HOST;
  process.env.FIREBASE_PROJECT_ID = "bodeul-restore-rehearsal";
  process.env.FIRESTORE_EMULATOR_HOST = "localhost:8080";

  try {
    const context = await createCliContext();
    assert.equal(context.projectId, "bodeul-restore-rehearsal");
    assert.equal(context.firestoreApiOrigin, "http://localhost:8080");
    assert.equal(context.useFirestoreEmulator, true);
    assert.equal(context.accessToken, "owner");
  } finally {
    restoreEnv("FIREBASE_PROJECT_ID", originalProjectId);
    restoreEnv("FIRESTORE_EMULATOR_HOST", originalEmulatorHost);
  }
});

function restoreEnv(name, value) {
  if (value === undefined) {
    delete process.env[name];
    return;
  }
  process.env[name] = value;
}
