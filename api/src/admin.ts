import {type DatabaseConfig} from "./config.js";

export interface AdminApiContractEndpoint {
  readonly method: "GET";
  readonly path: string;
  readonly auth: "none" | "firebase_id_token";
  readonly response: string;
  readonly description: string;
}

export interface AdminApiContractPayload {
  readonly status: "ok";
  readonly service: "bodeul-api";
  readonly resource: "admin-api-contract";
  readonly version: "2026-06-30";
  readonly timestamp: string;
  readonly database: {
    readonly status: DatabaseConfig["status"];
  };
  readonly authentication: {
    readonly type: "firebase_id_token";
    readonly status: "draft";
  };
  readonly endpoints: readonly AdminApiContractEndpoint[];
}

export function createAdminApiContractPayload(database: DatabaseConfig, now: () => Date = () => new Date()): AdminApiContractPayload {
  return {
    status: "ok",
    service: "bodeul-api",
    resource: "admin-api-contract",
    version: "2026-06-30",
    timestamp: now().toISOString(),
    database: {
      status: database.status,
    },
    authentication: {
      type: "firebase_id_token",
      status: "draft",
    },
    endpoints: [
      {
        method: "GET",
        path: "/healthz",
        auth: "none",
        response: "HealthPayload",
        description: "배포와 모니터링을 위한 최소 헬스체크입니다.",
      },
      {
        method: "GET",
        path: "/admin/api-contract",
        auth: "firebase_id_token",
        response: "AdminApiContractPayload",
        description: "관리자 웹 초기 API 응답 계약과 서버 설정 상태를 확인합니다.",
      },
    ],
  };
}
