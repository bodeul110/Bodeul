import {createServer, type IncomingMessage, type Server, type ServerResponse} from "node:http";

import {createAdminApiContractPayload} from "./admin.js";
import {authenticateFirebaseRequest, type FirebaseIdTokenVerifier} from "./auth.js";
import {authorizeAdminRequest, type AdminRoleAuthorizer} from "./authorization.js";
import {getDatabaseConfig, type DatabaseConfig} from "./config.js";
import {createHealthPayload} from "./health.js";

interface ErrorPayload {
  readonly error: string;
  readonly message: string;
}

export interface ApiServerOptions {
  readonly now?: () => Date;
  readonly env?: NodeJS.ProcessEnv;
  readonly firebaseVerifier?: FirebaseIdTokenVerifier | null;
  readonly adminRoleAuthorizer?: AdminRoleAuthorizer | null;
}

interface RequestContext {
  readonly now: () => Date;
  readonly database: DatabaseConfig;
  readonly firebaseVerifier: FirebaseIdTokenVerifier | null | undefined;
  readonly adminRoleAuthorizer: AdminRoleAuthorizer | null | undefined;
}

export function createApiServer(optionsOrNow: ApiServerOptions | (() => Date) = {}): Server {
  const options = typeof optionsOrNow === "function" ? {now: optionsOrNow} : optionsOrNow;
  const context: RequestContext = {
    now: options.now ?? (() => new Date()),
    database: getDatabaseConfig(options.env ?? process.env),
    firebaseVerifier: options.firebaseVerifier,
    adminRoleAuthorizer: options.adminRoleAuthorizer,
  };

  return createServer((request, response) => {
    void handleRequest(request, response, context).catch(() => {
      sendJson(response, 500, {
        error: "internal_error",
        message: "API 요청 처리 중 오류가 발생했습니다.",
      });
    });
  });
}

async function handleRequest(request: IncomingMessage, response: ServerResponse, context: RequestContext): Promise<void> {
  const url = new URL(request.url || "/", "http://localhost");

  if (url.pathname === "/healthz") {
    handleHealthz(request, response, context.now);
    return;
  }

  if (url.pathname === "/admin/api-contract") {
    await handleAdminApiContract(request, response, context);
    return;
  }

  sendJson(response, 404, {
    error: "not_found",
    message: "요청한 API 경로를 찾을 수 없습니다.",
  });
}

function handleHealthz(request: IncomingMessage, response: ServerResponse, now: () => Date): void {
  if (request.method !== "GET" && request.method !== "HEAD") {
    response.setHeader("Allow", "GET, HEAD");
    sendJson(response, 405, {
      error: "method_not_allowed",
      message: "헬스체크는 GET 또는 HEAD 요청만 허용합니다.",
    });
    return;
  }

  sendJson(response, 200, createHealthPayload(now), request.method === "HEAD");
}

async function handleAdminApiContract(request: IncomingMessage, response: ServerResponse, context: RequestContext): Promise<void> {
  if (request.method !== "GET" && request.method !== "HEAD") {
    response.setHeader("Allow", "GET, HEAD");
    sendJson(response, 405, {
      error: "method_not_allowed",
      message: "관리자 API 계약 조회는 GET 또는 HEAD 요청만 허용합니다.",
    });
    return;
  }

  const authResult = await authenticateFirebaseRequest(request.headers, context.firebaseVerifier);
  if (!authResult.ok) {
    sendJson(response, authResult.failure.statusCode, authResult.failure);
    return;
  }

  const authorizationResult = await authorizeAdminRequest(authResult.context, context.adminRoleAuthorizer);
  if (!authorizationResult.ok) {
    sendJson(response, authorizationResult.failure.statusCode, authorizationResult.failure);
    return;
  }

  sendJson(response, 200, createAdminApiContractPayload(context.database, context.now), request.method === "HEAD");
}

function sendJson(response: ServerResponse, statusCode: number, payload: ErrorPayload | object, omitBody = false): void {
  const body = JSON.stringify(payload);
  response.statusCode = statusCode;
  response.setHeader("Content-Type", "application/json; charset=utf-8");
  response.setHeader("Cache-Control", "no-store");
  response.setHeader("Content-Length", Buffer.byteLength(body));

  if (omitBody) {
    response.end();
    return;
  }

  response.end(body);
}
