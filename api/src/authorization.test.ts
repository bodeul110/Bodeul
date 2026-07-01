import assert from "node:assert/strict";
import test from "node:test";
import {type QueryResult} from "pg";

import {authorizeAdminRequest, createPostgresAdminRoleAuthorizer} from "./authorization.js";
import {type AuthContext} from "./auth.js";
import {type PostgresClient} from "./database.js";

const adminAuthContext: AuthContext = {
  uid: "firebase-admin-uid",
  token: "firebase-token",
  claims: {},
};

test("PostgreSQL role 조회는 firebase_uid 기준으로 ADMIN 역할을 반환한다", async () => {
  const queries: Array<{sql: string; values?: readonly unknown[]}> = [];
  const authorizer = createPostgresAdminRoleAuthorizer(createFakePostgresClient({
    queries,
    rows: [{role: "ADMIN"}],
  }));

  assert.equal(await authorizer?.getRoleByFirebaseUid("firebase-admin-uid"), "ADMIN");
  assert.deepEqual(queries, [
    {
      sql: "select role from app_users where firebase_uid = $1 limit 1",
      values: ["firebase-admin-uid"],
    },
  ]);
});

test("PostgreSQL role 조회 결과가 없으면 null을 반환한다", async () => {
  const authorizer = createPostgresAdminRoleAuthorizer(createFakePostgresClient({rows: []}));

  assert.equal(await authorizer?.getRoleByFirebaseUid("missing-uid"), null);
});

test("관리자 권한 확인기가 없으면 503을 반환한다", async () => {
  const result = await authorizeAdminRequest(adminAuthContext, null);

  assert.equal(result.ok, false);
  if (!result.ok) {
    assert.equal(result.failure.statusCode, 503);
    assert.equal(result.failure.error, "authorization_not_configured");
  }
});

test("ADMIN role이면 관리자 인가를 통과한다", async () => {
  const result = await authorizeAdminRequest(adminAuthContext, {
    async getRoleByFirebaseUid(uid) {
      assert.equal(uid, "firebase-admin-uid");
      return "ADMIN";
    },
  });

  assert.deepEqual(result, {ok: true});
});

test("ADMIN role이 아니면 403을 반환한다", async () => {
  const result = await authorizeAdminRequest(adminAuthContext, {
    async getRoleByFirebaseUid() {
      return "MANAGER";
    },
  });

  assert.equal(result.ok, false);
  if (!result.ok) {
    assert.equal(result.failure.statusCode, 403);
    assert.equal(result.failure.error, "admin_role_required");
  }
});

test("role 조회 실패는 503으로 반환한다", async () => {
  const result = await authorizeAdminRequest(adminAuthContext, {
    async getRoleByFirebaseUid() {
      throw new Error("db down");
    },
  });

  assert.equal(result.ok, false);
  if (!result.ok) {
    assert.equal(result.failure.statusCode, 503);
    assert.equal(result.failure.error, "role_lookup_failed");
  }
});

function createFakePostgresClient(options: {
  readonly rows: Array<{role: unknown}>;
  readonly queries?: Array<{sql: string; values?: readonly unknown[]}>;
}): PostgresClient {
  return {
    status: "configured",
    async query(sql, values): Promise<QueryResult> {
      options.queries?.push({sql, ...(values ? {values} : {})});
      return {
        command: "SELECT",
        fields: [],
        oid: 0,
        rowCount: options.rows.length,
        rows: options.rows,
      } as QueryResult;
    },
    async close() {
      return undefined;
    },
    async checkConnection() {
      return {ok: true};
    },
  };
}
