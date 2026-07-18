const SUPABASE_AUTHENTICATED_ROLE = "authenticated";

function parseCustomClaims(value) {
  if (!value) {
    return {};
  }

  if (typeof value === "object" && !Array.isArray(value)) {
    return {...value};
  }

  if (typeof value !== "string") {
    throw new Error("custom claim 형식을 해석할 수 없습니다.");
  }

  const parsed = JSON.parse(value);
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("custom claim은 JSON 객체여야 합니다.");
  }
  return parsed;
}

function buildSupabaseClaims(value) {
  return {
    ...parseCustomClaims(value),
    role: SUPABASE_AUTHENTICATED_ROLE,
  };
}

function needsSupabaseRole(value) {
  return parseCustomClaims(value).role !== SUPABASE_AUTHENTICATED_ROLE;
}

function serializeClaims(value) {
  const serialized = JSON.stringify(buildSupabaseClaims(value));
  if (Buffer.byteLength(serialized, "utf8") > 1000) {
    throw new Error("Firebase custom claim 크기 제한 1,000바이트를 초과합니다.");
  }
  return serialized;
}

module.exports = {
  buildSupabaseClaims,
  needsSupabaseRole,
  parseCustomClaims,
  serializeClaims,
};
