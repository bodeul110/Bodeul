import assert from "node:assert/strict";
import test from "node:test";
import {type QueryResult} from "pg";

import {
  createPostgresHospitalGuideReader,
  parseHospitalGuideLimit,
} from "./hospital-guides.js";
import {type PostgresClient} from "./database.js";

test("PostgreSQL hospital_guides 조회 결과를 관리자 API DTO로 변환한다", async () => {
  const queries: Array<{sql: string; values?: readonly unknown[]}> = [];
  const reader = createPostgresHospitalGuideReader(createFakePostgresClient({
    queries,
    rows: [
      {
        id: "bad67ae3-b0ef-5a63-806d-7274ac4ce3d3",
        hospital_name: "서울내과병원",
        department_name: "내과",
        steps: [{title: "접수", description: "원무과에서 접수합니다."}],
        created_at: new Date("2026-04-23T16:48:39.766Z"),
        updated_at: new Date("2026-04-23T16:48:39.766Z"),
      },
    ],
  }));

  const payload = await reader?.listHospitalGuides(50);

  assert.deepEqual(payload, {
    items: [
      {
        id: "bad67ae3-b0ef-5a63-806d-7274ac4ce3d3",
        hospitalName: "서울내과병원",
        departmentName: "내과",
        steps: [{title: "접수", description: "원무과에서 접수합니다."}],
        createdAt: "2026-04-23T16:48:39.766Z",
        updatedAt: "2026-04-23T16:48:39.766Z",
      },
    ],
    limit: 50,
  });
  assert.deepEqual(queries, [
    {
      sql: [
        "select id, hospital_name, department_name, steps, created_at, updated_at",
        "from hospital_guides",
        "order by updated_at desc, hospital_name asc, department_name asc",
        "limit $1",
      ].join(" "),
      values: [50],
    },
  ]);
});

test("PostgreSQL client가 없으면 hospital guide reader를 만들지 않는다", () => {
  assert.equal(createPostgresHospitalGuideReader(null), null);
});

test("hospital guide limit 기본값과 범위를 검증한다", () => {
  assert.deepEqual(parseHospitalGuideLimit(null), {ok: true, limit: 50});
  assert.deepEqual(parseHospitalGuideLimit(""), {ok: true, limit: 50});
  assert.deepEqual(parseHospitalGuideLimit("100"), {ok: true, limit: 100});

  for (const invalidLimit of ["0", "101", "1.5", "abc"]) {
    const result = parseHospitalGuideLimit(invalidLimit);
    assert.equal(result.ok, false);
    if (!result.ok) {
      assert.equal(result.failure.statusCode, 400);
      assert.equal(result.failure.error, "invalid_limit");
    }
  }
});

function createFakePostgresClient(options: {
  readonly rows: Array<Record<string, unknown>>;
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
