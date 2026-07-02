import {once} from "node:events";
import {type AddressInfo} from "node:net";
import assert from "node:assert/strict";
import test from "node:test";

import {getCorsConfig, getDatabaseConfig, getServerConfig} from "./config.js";
import {createApiServer} from "./server.js";

const fixedNow = () => new Date("2026-06-30T00:00:00.000Z");

test("GET /healthz는 정상 상태 JSON을 반환한다", async () => {
  const server = createApiServer(fixedNow);
  const baseUrl = await listen(server);

  try {
    const response = await fetch(`${baseUrl}/healthz`);
    const body = await response.json() as unknown;

    assert.equal(response.status, 200);
    assert.deepEqual(body, {
      status: "ok",
      service: "bodeul-api",
      timestamp: "2026-06-30T00:00:00.000Z",
    });
  } finally {
    await close(server);
  }
});

test("HEAD /healthz는 본문 없이 정상 상태를 반환한다", async () => {
  const server = createApiServer(fixedNow);
  const baseUrl = await listen(server);

  try {
    const response = await fetch(`${baseUrl}/healthz`, {method: "HEAD"});
    const body = await response.text();

    assert.equal(response.status, 200);
    assert.equal(body, "");
  } finally {
    await close(server);
  }
});

test("지원하지 않는 메서드는 405를 반환한다", async () => {
  const server = createApiServer(fixedNow);
  const baseUrl = await listen(server);

  try {
    const response = await fetch(`${baseUrl}/healthz`, {method: "POST"});
    const body = await response.json() as {error?: string};

    assert.equal(response.status, 405);
    assert.equal(response.headers.get("allow"), "GET, HEAD");
    assert.equal(body.error, "method_not_allowed");
  } finally {
    await close(server);
  }
});

test("없는 경로는 404를 반환한다", async () => {
  const server = createApiServer(fixedNow);
  const baseUrl = await listen(server);

  try {
    const response = await fetch(`${baseUrl}/missing`);
    const body = await response.json() as {error?: string};

    assert.equal(response.status, 404);
    assert.equal(body.error, "not_found");
  } finally {
    await close(server);
  }
});

test("포트 환경변수는 1부터 65535 사이의 정수만 허용한다", () => {
  assert.equal(getServerConfig({BODEUL_API_PORT: "9090"}).port, 9090);
  assert.equal(getServerConfig({BODEUL_API_PORT: ""}).port, 8080);
  assert.throws(() => getServerConfig({BODEUL_API_PORT: "0"}), /BODEUL_API_PORT/);
  assert.throws(() => getServerConfig({BODEUL_API_PORT: "abc"}), /BODEUL_API_PORT/);
});

test("CORS origin은 기본 관리자 웹 로컬 주소와 명시 설정만 허용한다", () => {
  assert.deepEqual(getCorsConfig({}).allowedOrigins, ["http://localhost:5173", "http://127.0.0.1:5173"]);
  assert.deepEqual(getCorsConfig({BODEUL_API_ALLOWED_ORIGINS: "https://admin.example.com, http://localhost:5173/"}).allowedOrigins, [
    "https://admin.example.com",
    "http://localhost:5173",
  ]);
  assert.deepEqual(getCorsConfig({BODEUL_API_ALLOWED_ORIGINS: ""}).allowedOrigins, []);
  assert.throws(() => getCorsConfig({BODEUL_API_ALLOWED_ORIGINS: "https://admin.example.com/path"}), /BODEUL_API_ALLOWED_ORIGINS/);
});

test("OPTIONS preflight는 허용된 관리자 웹 origin에 CORS 헤더를 반환한다", async () => {
  const server = createApiServer(fixedNow);
  const baseUrl = await listen(server);

  try {
    const response = await fetch(`${baseUrl}/admin/hospital-guides`, {
      method: "OPTIONS",
      headers: {
        Origin: "http://localhost:5173",
        "Access-Control-Request-Method": "GET",
        "Access-Control-Request-Headers": "Authorization",
      },
    });
    const body = await response.text();

    assert.equal(response.status, 204);
    assert.equal(body, "");
    assert.equal(response.headers.get("access-control-allow-origin"), "http://localhost:5173");
    assert.equal(response.headers.get("access-control-allow-methods"), "GET, HEAD, OPTIONS");
    assert.match(response.headers.get("access-control-allow-headers") || "", /Authorization/);
  } finally {
    await close(server);
  }
});

test("OPTIONS preflight는 허용되지 않은 origin을 거부한다", async () => {
  const server = createApiServer(fixedNow);
  const baseUrl = await listen(server);

  try {
    const response = await fetch(`${baseUrl}/admin/hospital-guides`, {
      method: "OPTIONS",
      headers: {
        Origin: "https://unknown.example.com",
        "Access-Control-Request-Method": "GET",
      },
    });
    const body = await response.json() as {error?: string};

    assert.equal(response.status, 403);
    assert.equal(response.headers.get("access-control-allow-origin"), null);
    assert.equal(body.error, "cors_origin_not_allowed");
  } finally {
    await close(server);
  }
});

test("DATABASE_URL이 없으면 DB 설정 누락 상태로 처리한다", () => {
  assert.deepEqual(getDatabaseConfig({}), {status: "missing"});
  assert.deepEqual(getDatabaseConfig({DATABASE_URL: ""}), {status: "missing"});
});

test("DATABASE_URL은 PostgreSQL connection string만 허용한다", () => {
  assert.deepEqual(getDatabaseConfig({DATABASE_URL: "postgresql://localhost:5432/bodeul"}), {
    status: "configured",
    connectionString: "postgresql://localhost:5432/bodeul",
  });
  assert.throws(() => getDatabaseConfig({DATABASE_URL: "https://localhost/bodeul"}), /DATABASE_URL/);
  assert.throws(() => getDatabaseConfig({DATABASE_URL: "postgresql://localhost"}), /DATABASE_URL/);
  assert.throws(() => getDatabaseConfig({DATABASE_URL: "not-a-url"}), /DATABASE_URL/);
});

test("GET /admin/api-contract는 Firebase 토큰 검증 후 초기 계약을 반환한다", async () => {
  const server = createApiServer({
    now: fixedNow,
    env: {DATABASE_URL: "postgresql://localhost:5432/bodeul"},
    firebaseVerifier: {
      async verifyIdToken(idToken) {
        assert.equal(idToken, "firebase-token");
        return {uid: "admin-1", claims: {role: "ADMIN"}};
      },
    },
    adminRoleAuthorizer: {
      async getRoleByFirebaseUid(uid) {
        assert.equal(uid, "admin-1");
        return "ADMIN";
      },
    },
  });
  const baseUrl = await listen(server);

  try {
    const response = await fetch(`${baseUrl}/admin/api-contract`, {
      headers: {Authorization: "Bearer firebase-token"},
    });
    const body = await response.json() as {
      status?: string;
      resource?: string;
      database?: {status?: string};
      endpoints?: Array<{path?: string}>;
    };

    assert.equal(response.status, 200);
    assert.equal(body.status, "ok");
    assert.equal(body.resource, "admin-api-contract");
    assert.equal(body.database?.status, "configured");
    assert.equal(body.endpoints?.some((endpoint) => endpoint.path === "/admin/api-contract"), true);
    assert.equal(body.endpoints?.some((endpoint) => endpoint.path === "/admin/hospital-guides"), true);
  } finally {
    await close(server);
  }
});

test("GET /admin/api-contract는 Firebase verifier가 없으면 503을 반환한다", async () => {
  const server = createApiServer({now: fixedNow, env: {}});
  const baseUrl = await listen(server);

  try {
    const response = await fetch(`${baseUrl}/admin/api-contract`, {
      headers: {Authorization: "Bearer firebase-token"},
    });
    const body = await response.json() as {error?: string};

    assert.equal(response.status, 503);
    assert.equal(body.error, "auth_not_configured");
  } finally {
    await close(server);
  }
});

test("GET /admin/api-contract는 관리자 권한 확인기가 없으면 503을 반환한다", async () => {
  const server = createApiServer({
    now: fixedNow,
    env: {},
    firebaseVerifier: {
      async verifyIdToken() {
        return {uid: "admin-1", claims: {}};
      },
    },
  });
  const baseUrl = await listen(server);

  try {
    const response = await fetch(`${baseUrl}/admin/api-contract`, {
      headers: {Authorization: "Bearer firebase-token"},
    });
    const body = await response.json() as {error?: string};

    assert.equal(response.status, 503);
    assert.equal(body.error, "authorization_not_configured");
  } finally {
    await close(server);
  }
});

test("GET /admin/api-contract는 Authorization 헤더가 없으면 401을 반환한다", async () => {
  const server = createApiServer({
    now: fixedNow,
    env: {},
    firebaseVerifier: {
      async verifyIdToken() {
        return {uid: "admin-1", claims: {}};
      },
    },
  });
  const baseUrl = await listen(server);

  try {
    const response = await fetch(`${baseUrl}/admin/api-contract`);
    const body = await response.json() as {error?: string};

    assert.equal(response.status, 401);
    assert.equal(body.error, "missing_authorization");
  } finally {
    await close(server);
  }
});

test("GET /admin/api-contract는 비관리자 role이면 403을 반환한다", async () => {
  const server = createApiServer({
    now: fixedNow,
    env: {},
    firebaseVerifier: {
      async verifyIdToken() {
        return {uid: "manager-1", claims: {}};
      },
    },
    adminRoleAuthorizer: {
      async getRoleByFirebaseUid(uid) {
        assert.equal(uid, "manager-1");
        return "MANAGER";
      },
    },
  });
  const baseUrl = await listen(server);

  try {
    const response = await fetch(`${baseUrl}/admin/api-contract`, {
      headers: {Authorization: "Bearer firebase-token"},
    });
    const body = await response.json() as {error?: string};

    assert.equal(response.status, 403);
    assert.equal(body.error, "admin_role_required");
  } finally {
    await close(server);
  }
});

test("GET /admin/api-contract는 role 조회 실패 시 503을 반환한다", async () => {
  const server = createApiServer({
    now: fixedNow,
    env: {},
    firebaseVerifier: {
      async verifyIdToken() {
        return {uid: "admin-1", claims: {}};
      },
    },
    adminRoleAuthorizer: {
      async getRoleByFirebaseUid() {
        throw new Error("db down");
      },
    },
  });
  const baseUrl = await listen(server);

  try {
    const response = await fetch(`${baseUrl}/admin/api-contract`, {
      headers: {Authorization: "Bearer firebase-token"},
    });
    const body = await response.json() as {error?: string};

    assert.equal(response.status, 503);
    assert.equal(body.error, "role_lookup_failed");
  } finally {
    await close(server);
  }
});

test("GET /admin/hospital-guides는 관리자 권한으로 병원 가이드 목록을 반환한다", async () => {
  const server = createApiServer({
    now: fixedNow,
    env: {DATABASE_URL: "postgresql://localhost:5432/bodeul"},
    firebaseVerifier: {
      async verifyIdToken(idToken) {
        assert.equal(idToken, "firebase-token");
        return {uid: "admin-1", claims: {}};
      },
    },
    adminRoleAuthorizer: {
      async getRoleByFirebaseUid(uid) {
        assert.equal(uid, "admin-1");
        return "ADMIN";
      },
    },
    hospitalGuideReader: {
      async listHospitalGuides(limit) {
        assert.equal(limit, 50);
        return {
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
          limit,
        };
      },
    },
  });
  const baseUrl = await listen(server);

  try {
    const response = await fetch(`${baseUrl}/admin/hospital-guides`, {
      headers: {Authorization: "Bearer firebase-token"},
    });
    const body = await response.json() as {
      limit?: number;
      items?: Array<{hospitalName?: string; departmentName?: string; steps?: unknown[]}>;
    };

    assert.equal(response.status, 200);
    assert.equal(body.limit, 50);
    assert.equal(body.items?.[0]?.hospitalName, "서울내과병원");
    assert.equal(body.items?.[0]?.departmentName, "내과");
    assert.equal(Array.isArray(body.items?.[0]?.steps), true);
  } finally {
    await close(server);
  }
});

test("GET /admin/hospital-guides는 limit query를 조회기에 전달한다", async () => {
  const server = createApiServer({
    now: fixedNow,
    env: {DATABASE_URL: "postgresql://localhost:5432/bodeul"},
    firebaseVerifier: {
      async verifyIdToken() {
        return {uid: "admin-1", claims: {}};
      },
    },
    adminRoleAuthorizer: {
      async getRoleByFirebaseUid() {
        return "ADMIN";
      },
    },
    hospitalGuideReader: {
      async listHospitalGuides(limit) {
        assert.equal(limit, 2);
        return {items: [], limit};
      },
    },
  });
  const baseUrl = await listen(server);

  try {
    const response = await fetch(`${baseUrl}/admin/hospital-guides?limit=2`, {
      headers: {Authorization: "Bearer firebase-token"},
    });
    const body = await response.json() as {limit?: number};

    assert.equal(response.status, 200);
    assert.equal(body.limit, 2);
  } finally {
    await close(server);
  }
});

test("GET /admin/hospital-guides는 잘못된 limit이면 400을 반환한다", async () => {
  const server = createApiServer({
    now: fixedNow,
    env: {DATABASE_URL: "postgresql://localhost:5432/bodeul"},
    firebaseVerifier: {
      async verifyIdToken() {
        return {uid: "admin-1", claims: {}};
      },
    },
    adminRoleAuthorizer: {
      async getRoleByFirebaseUid() {
        return "ADMIN";
      },
    },
    hospitalGuideReader: {
      async listHospitalGuides() {
        assert.fail("잘못된 limit에서는 병원 가이드 조회기를 호출하지 않아야 한다.");
      },
    },
  });
  const baseUrl = await listen(server);

  try {
    const response = await fetch(`${baseUrl}/admin/hospital-guides?limit=101`, {
      headers: {Authorization: "Bearer firebase-token"},
    });
    const body = await response.json() as {error?: string};

    assert.equal(response.status, 400);
    assert.equal(body.error, "invalid_limit");
  } finally {
    await close(server);
  }
});

test("GET /admin/hospital-guides는 조회기가 없으면 503을 반환한다", async () => {
  const server = createApiServer({
    now: fixedNow,
    env: {},
    firebaseVerifier: {
      async verifyIdToken() {
        return {uid: "admin-1", claims: {}};
      },
    },
    adminRoleAuthorizer: {
      async getRoleByFirebaseUid() {
        return "ADMIN";
      },
    },
  });
  const baseUrl = await listen(server);

  try {
    const response = await fetch(`${baseUrl}/admin/hospital-guides`, {
      headers: {Authorization: "Bearer firebase-token"},
    });
    const body = await response.json() as {error?: string};

    assert.equal(response.status, 503);
    assert.equal(body.error, "hospital_guides_not_configured");
  } finally {
    await close(server);
  }
});

test("GET /admin/hospital-guides는 조회 실패 시 503을 반환한다", async () => {
  const server = createApiServer({
    now: fixedNow,
    env: {DATABASE_URL: "postgresql://localhost:5432/bodeul"},
    firebaseVerifier: {
      async verifyIdToken() {
        return {uid: "admin-1", claims: {}};
      },
    },
    adminRoleAuthorizer: {
      async getRoleByFirebaseUid() {
        return "ADMIN";
      },
    },
    hospitalGuideReader: {
      async listHospitalGuides() {
        throw new Error("db down");
      },
    },
  });
  const baseUrl = await listen(server);

  try {
    const response = await fetch(`${baseUrl}/admin/hospital-guides`, {
      headers: {Authorization: "Bearer firebase-token"},
    });
    const body = await response.json() as {error?: string};

    assert.equal(response.status, 503);
    assert.equal(body.error, "hospital_guides_lookup_failed");
  } finally {
    await close(server);
  }
});

test("지원하지 않는 병원 가이드 API 메서드는 405를 반환한다", async () => {
  const server = createApiServer({now: fixedNow, env: {}});
  const baseUrl = await listen(server);

  try {
    const response = await fetch(`${baseUrl}/admin/hospital-guides`, {method: "POST"});
    const body = await response.json() as {error?: string};

    assert.equal(response.status, 405);
    assert.equal(response.headers.get("allow"), "GET");
    assert.equal(body.error, "method_not_allowed");
  } finally {
    await close(server);
  }
});

test("지원하지 않는 관리자 API 메서드는 405를 반환한다", async () => {
  const server = createApiServer({now: fixedNow, env: {}});
  const baseUrl = await listen(server);

  try {
    const response = await fetch(`${baseUrl}/admin/api-contract`, {method: "POST"});
    const body = await response.json() as {error?: string};

    assert.equal(response.status, 405);
    assert.equal(response.headers.get("allow"), "GET, HEAD");
    assert.equal(body.error, "method_not_allowed");
  } finally {
    await close(server);
  }
});

async function listen(server: ReturnType<typeof createApiServer>): Promise<string> {
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  const address = server.address() as AddressInfo;
  return `http://127.0.0.1:${address.port}`;
}

async function close(server: ReturnType<typeof createApiServer>): Promise<void> {
  server.close();
  await once(server, "close");
}
