import assert from "node:assert/strict";
import test from "node:test";

import {createFirebaseAdminVerifier} from "./firebase-admin.js";

test("Firebase 설정이 없으면 verifier를 만들지 않는다", () => {
  const verifier = createFirebaseAdminVerifier({}, createFakeDependencies());

  assert.equal(verifier, null);
});

test("FIREBASE_PROJECT_ID만 있으면 기본 인증 정보로 Admin SDK를 초기화한다", async () => {
  const calls: Array<{projectId?: string; hasCredential: boolean}> = [];
  const verifier = createFirebaseAdminVerifier(
      {FIREBASE_PROJECT_ID: "bodeul-dev"},
      createFakeDependencies({calls}),
  );

  assert.notEqual(verifier, null);
  assert.deepEqual(calls, [{projectId: "bodeul-dev", hasCredential: false}]);
  assert.deepEqual(await verifier?.verifyIdToken("firebase-token"), {
    uid: "admin-1",
    email: "admin@bodeul.app",
    claims: {
      uid: "admin-1",
      email: "admin@bodeul.app",
      role: "ADMIN",
    },
  });
});

test("서비스 계정 JSON이 있으면 cert 기반으로 Admin SDK를 초기화한다", () => {
  const calls: Array<{projectId?: string; hasCredential: boolean}> = [];
  const certs: unknown[] = [];
  const verifier = createFirebaseAdminVerifier(
      {
        FIREBASE_SERVICE_ACCOUNT_JSON: JSON.stringify({
          project_id: "bodeul-dev",
          client_email: "firebase-adminsdk@bodeul-dev.iam.gserviceaccount.com",
          private_key: "local-test-key\\nline-2",
        }),
      },
      createFakeDependencies({calls, certs}),
  );

  assert.notEqual(verifier, null);
  assert.deepEqual(calls, [{projectId: "bodeul-dev", hasCredential: true}]);
  assert.deepEqual(certs, [
    {
      projectId: "bodeul-dev",
      clientEmail: "firebase-adminsdk@bodeul-dev.iam.gserviceaccount.com",
      privateKey: "local-test-key\nline-2",
    },
  ]);
});

test("기존 Firebase app이 있으면 새 app을 초기화하지 않는다", () => {
  const calls: Array<{projectId?: string; hasCredential: boolean}> = [];
  const verifier = createFirebaseAdminVerifier(
      {FIREBASE_PROJECT_ID: "bodeul-dev"},
      createFakeDependencies({apps: [{}], calls}),
  );

  assert.notEqual(verifier, null);
  assert.deepEqual(calls, []);
});

test("서비스 계정 JSON 형식이 잘못되면 시작 단계에서 실패한다", () => {
  assert.throws(
      () => createFirebaseAdminVerifier({FIREBASE_SERVICE_ACCOUNT_JSON: "{not-json"}, createFakeDependencies()),
      /FIREBASE_SERVICE_ACCOUNT_JSON/,
  );

  assert.throws(
      () => createFirebaseAdminVerifier({FIREBASE_SERVICE_ACCOUNT_JSON: JSON.stringify({project_id: "bodeul-dev"})}, createFakeDependencies()),
      /client_email/,
  );
});

function createFakeDependencies(options: {
  readonly apps?: object[];
  readonly calls?: Array<{projectId?: string; hasCredential: boolean}>;
  readonly certs?: unknown[];
} = {}) {
  const app = {};

  return {
    cert(serviceAccount: unknown) {
      options.certs?.push(serviceAccount);
      return {credential: "cert"};
    },
    getApps() {
      return options.apps ?? [];
    },
    getAuth() {
      return {
        async verifyIdToken(idToken: string) {
          assert.equal(idToken, "firebase-token");
          return {
            uid: "admin-1",
            email: "admin@bodeul.app",
            role: "ADMIN",
          };
        },
      };
    },
    initializeApp(initOptions: {readonly projectId?: string; readonly credential?: unknown}) {
      options.calls?.push({
        ...(initOptions.projectId ? {projectId: initOptions.projectId} : {}),
        hasCredential: initOptions.credential !== undefined,
      });
      return app;
    },
  };
}
