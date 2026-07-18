const SUPABASE_AUTHENTICATED_ROLE = "authenticated";

function normalizeClaims(claims) {
  if (!claims || typeof claims !== "object" || Array.isArray(claims)) {
    return {};
  }
  return {...claims};
}

function mergeSupabaseAuthenticatedRole(claims) {
  return {
    ...normalizeClaims(claims),
    role: SUPABASE_AUTHENTICATED_ROLE,
  };
}

module.exports = {
  SUPABASE_AUTHENTICATED_ROLE,
  mergeSupabaseAuthenticatedRole,
};
