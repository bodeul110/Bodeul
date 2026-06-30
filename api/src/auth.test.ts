import assert from "node:assert/strict";
import test from "node:test";

import {
  authenticateFirebaseRequest,
  extractBearerToken,
  type FirebaseIdTokenVerifier,
} from "./auth.js";

test("Bearer 토큰을 추출한다", () => {
  const result = extractBearerToken({authorization: "Bearer firebase-token"});

  assert.equal(result.ok, true);
  if (result.ok) {
    assert.equal(result.token, "firebase-token");
  }
});

test("Bearer 스킴은 대소문자를 구분하지 않는다", () => {
  const result = extractBearerToken({authorization: "bearer firebase-token"});

  assert.equal(result.ok, true);
  if (result.ok) {
    assert.equal(result.token, "firebase-token");
  }
});

test("Authorization 헤더가 없으면 401을 반환한다", () => {
  const result = extractBearerToken({});

  assert.equal(result.ok, false);
  if (!result.ok) {
    assert.equal(result.failure.statusCode, 401);
    assert.equal(result.failure.error, "missing_authorization");
  }
});

test("Bearer 형식이 아니면 401을 반환한다", () => {
  const result = extractBearerToken({authorization: "Basic abc"});

  assert.equal(result.ok, false);
  if (!result.ok) {
    assert.equal(result.failure.statusCode, 401);
    assert.equal(result.failure.error, "invalid_authorization");
  }
});

test("Firebase verifier가 없으면 503을 반환한다", async () => {
  const result = await authenticateFirebaseRequest({authorization: "Bearer firebase-token"}, null);

  assert.equal(result.ok, false);
  if (!result.ok) {
    assert.equal(result.failure.statusCode, 503);
    assert.equal(result.failure.error, "auth_not_configured");
  }
});

test("Firebase verifier가 uid를 반환하면 인증 컨텍스트를 만든다", async () => {
  const verifier: FirebaseIdTokenVerifier = {
    async verifyIdToken(idToken) {
      assert.equal(idToken, "firebase-token");
      return {
        uid: "user-1",
        email: "admin@bodeul.app",
        claims: {role: "ADMIN"},
      };
    },
  };

  const result = await authenticateFirebaseRequest({authorization: "Bearer firebase-token"}, verifier);

  assert.equal(result.ok, true);
  if (result.ok) {
    assert.equal(result.context.uid, "user-1");
    assert.equal(result.context.email, "admin@bodeul.app");
    assert.equal(result.context.token, "firebase-token");
    assert.deepEqual(result.context.claims, {role: "ADMIN"});
  }
});

test("Firebase verifier 실패는 401로 숨긴다", async () => {
  const verifier: FirebaseIdTokenVerifier = {
    async verifyIdToken() {
      throw new Error("token expired");
    },
  };

  const result = await authenticateFirebaseRequest({authorization: "Bearer expired-token"}, verifier);

  assert.equal(result.ok, false);
  if (!result.ok) {
    assert.equal(result.failure.statusCode, 401);
    assert.equal(result.failure.error, "invalid_firebase_token");
  }
});

test("uid가 비어 있으면 401을 반환한다", async () => {
  const verifier: FirebaseIdTokenVerifier = {
    async verifyIdToken() {
      return {uid: " ", claims: {}};
    },
  };

  const result = await authenticateFirebaseRequest({authorization: "Bearer firebase-token"}, verifier);

  assert.equal(result.ok, false);
  if (!result.ok) {
    assert.equal(result.failure.statusCode, 401);
    assert.equal(result.failure.error, "invalid_firebase_token");
  }
});
