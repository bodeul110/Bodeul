export interface VerifiedFirebaseToken {
  readonly uid: string;
  readonly email?: string;
  readonly claims: Readonly<Record<string, unknown>>;
}

export interface FirebaseIdTokenVerifier {
  verifyIdToken(idToken: string): Promise<VerifiedFirebaseToken>;
}

export interface AuthContext {
  readonly uid: string;
  readonly token: string;
  readonly email?: string;
  readonly claims: Readonly<Record<string, unknown>>;
}

export interface AuthFailure {
  readonly statusCode: 401 | 503;
  readonly error: "missing_authorization" | "invalid_authorization" | "auth_not_configured" | "invalid_firebase_token";
  readonly message: string;
}

export type AuthResult =
  | {readonly ok: true; readonly context: AuthContext}
  | {readonly ok: false; readonly failure: AuthFailure};

export type BearerTokenResult =
  | {readonly ok: true; readonly token: string}
  | {readonly ok: false; readonly failure: AuthFailure};

export interface AuthorizationHeaders {
  readonly authorization?: string | readonly string[] | undefined;
}

export async function authenticateFirebaseRequest(
    headers: AuthorizationHeaders,
    verifier: FirebaseIdTokenVerifier | null | undefined,
): Promise<AuthResult> {
  if (!verifier) {
    return fail(503, "auth_not_configured", "Firebase ID token 검증기가 아직 설정되지 않았습니다.");
  }

  const tokenResult = extractBearerToken(headers);
  if (!tokenResult.ok) {
    return tokenResult;
  }

  try {
    const verified = await verifier.verifyIdToken(tokenResult.token);
    if (!verified.uid.trim()) {
      return fail(401, "invalid_firebase_token", "Firebase ID token에 uid가 없습니다.");
    }

    const context: AuthContext = {
      uid: verified.uid,
      token: tokenResult.token,
      claims: verified.claims,
      ...(verified.email ? {email: verified.email} : {}),
    };

    return {
      ok: true,
      context,
    };
  } catch {
    return fail(401, "invalid_firebase_token", "Firebase ID token 검증에 실패했습니다.");
  }
}

export function extractBearerToken(headers: AuthorizationHeaders): BearerTokenResult {
  const rawHeader = headers.authorization;
  const header = Array.isArray(rawHeader) ? rawHeader[0] : rawHeader;

  if (header === undefined || header.trim() === "") {
    return fail(401, "missing_authorization", "Authorization 헤더가 필요합니다.");
  }

  const match = header.match(/^Bearer\s+(.+)$/i);
  if (!match) {
    return fail(401, "invalid_authorization", "Authorization 헤더는 Bearer 토큰 형식이어야 합니다.");
  }

  const token = match[1]?.trim() || "";
  if (!token) {
    return fail(401, "invalid_authorization", "Bearer 토큰 값이 비어 있습니다.");
  }

  return {ok: true, token};
}

function fail(statusCode: AuthFailure["statusCode"], error: AuthFailure["error"], message: string): {readonly ok: false; readonly failure: AuthFailure} {
  return {
    ok: false,
    failure: {statusCode, error, message},
  };
}
