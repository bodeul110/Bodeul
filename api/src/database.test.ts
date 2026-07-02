import assert from "node:assert/strict";
import test from "node:test";
import {type QueryResult} from "pg";

import {createPostgresClient} from "./database.js";

test("DATABASE_URL이 없으면 PostgreSQL client를 만들지 않는다", () => {
  const client = createPostgresClient({}, createFakeDependencies());

  assert.equal(client, null);
});

test("DATABASE_URL이 있으면 제한된 pool 설정으로 PostgreSQL client를 만든다", async () => {
  const calls: unknown[] = [];
  const queries: Array<{sql: string; values?: readonly unknown[]}> = [];
  const client = createPostgresClient(
      {DATABASE_URL: "postgresql://localhost:5432/bodeul"},
      createFakeDependencies({calls, queries}),
  );

  assert.notEqual(client, null);
  assert.deepEqual(calls, [
    {
      connectionString: "postgresql://localhost:5432/bodeul",
      max: 5,
      idleTimeoutMillis: 10_000,
      connectionTimeoutMillis: 5_000,
    },
  ]);

  await client?.query("select $1::text as value", ["ok"]);
  assert.deepEqual(queries, [{sql: "select $1::text as value", values: ["ok"]}]);
});

test("PostgreSQL client 종료 시 pool을 닫는다", async () => {
  let closed = false;
  const client = createPostgresClient(
      {DATABASE_URL: "postgresql://localhost:5432/bodeul"},
      createFakeDependencies({onEnd: () => {
        closed = true;
      }}),
  );

  await client?.close();

  assert.equal(closed, true);
});

test("PostgreSQL 연결 확인이 성공하면 ok를 반환한다", async () => {
  const client = createPostgresClient(
      {DATABASE_URL: "postgresql://localhost:5432/bodeul"},
      createFakeDependencies(),
  );

  assert.deepEqual(await client?.checkConnection(), {ok: true});
});

test("PostgreSQL 연결 확인 실패는 connection string 없이 요약 오류만 반환한다", async () => {
  const client = createPostgresClient(
      {DATABASE_URL: "postgresql://localhost:5432/bodeul"},
      createFakeDependencies({queryError: new Error("connection refused")}),
  );

  assert.deepEqual(await client?.checkConnection(), {
    ok: false,
    error: "db_connection_failed",
  });
});

function createFakeDependencies(options: {
  readonly calls?: unknown[];
  readonly queries?: Array<{sql: string; values?: readonly unknown[]}>;
  readonly queryError?: Error;
  readonly onEnd?: () => void;
} = {}) {
  return {
    createPool(config: unknown) {
      options.calls?.push(config);
      return {
        async query(sql: string, values?: readonly unknown[]): Promise<QueryResult> {
          if (options.queryError) {
            throw options.queryError;
          }
          options.queries?.push({sql, ...(values ? {values} : {})});
          return {
            command: "SELECT",
            fields: [],
            oid: 0,
            rowCount: 0,
            rows: [],
          } as QueryResult;
        },
        async end() {
          options.onEnd?.();
        },
      };
    },
  };
}
