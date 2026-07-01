import {type AuthContext} from "./auth.js";
import {type PostgresClient} from "./database.js";

export type AppUserRole = "PATIENT" | "GUARDIAN" | "MANAGER" | "ADMIN";

export interface AdminRoleAuthorizer {
  getRoleByFirebaseUid(firebaseUid: string): Promise<AppUserRole | null>;
}

export interface AuthorizationFailure {
  readonly statusCode: 403 | 503;
  readonly error: "admin_role_required" | "authorization_not_configured" | "role_lookup_failed";
  readonly message: string;
}

export type AuthorizationResult =
  | {readonly ok: true}
  | {readonly ok: false; readonly failure: AuthorizationFailure};

export function createPostgresAdminRoleAuthorizer(
    postgresClient: PostgresClient | null | undefined,
): AdminRoleAuthorizer | null {
  if (!postgresClient) {
    return null;
  }

  return {
    async getRoleByFirebaseUid(firebaseUid: string): Promise<AppUserRole | null> {
      const result = await postgresClient.query(
          "select role from app_users where firebase_uid = $1 limit 1",
          [firebaseUid],
      );
      const role = result.rows[0]?.role;
      return isAppUserRole(role) ? role : null;
    },
  };
}

export async function authorizeAdminRequest(
    authContext: AuthContext,
    authorizer: AdminRoleAuthorizer | null | undefined,
): Promise<AuthorizationResult> {
  if (!authorizer) {
    return fail(503, "authorization_not_configured", "관리자 권한 확인기가 아직 설정되지 않았습니다.");
  }

  try {
    const role = await authorizer.getRoleByFirebaseUid(authContext.uid);
    if (role !== "ADMIN") {
      return fail(403, "admin_role_required", "관리자 권한이 필요합니다.");
    }

    return {ok: true};
  } catch {
    return fail(503, "role_lookup_failed", "관리자 권한 확인에 실패했습니다.");
  }
}

function isAppUserRole(value: unknown): value is AppUserRole {
  return value === "PATIENT" || value === "GUARDIAN" || value === "MANAGER" || value === "ADMIN";
}

function fail(
    statusCode: AuthorizationFailure["statusCode"],
    error: AuthorizationFailure["error"],
    message: string,
): {readonly ok: false; readonly failure: AuthorizationFailure} {
  return {
    ok: false,
    failure: {statusCode, error, message},
  };
}
