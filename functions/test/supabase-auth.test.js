const test = require("node:test");
const assert = require("node:assert/strict");

const {
  mergeSupabaseAuthenticatedRole,
} = require("../src/lib/supabase-auth-claims");

test("기존 custom claim을 보존하고 Supabase 역할을 추가한다", () => {
  assert.deepEqual(
      mergeSupabaseAuthenticatedRole({admin: true, accessLevel: 3}),
      {admin: true, accessLevel: 3, role: "authenticated"},
  );
});

test("claim이 없거나 잘못된 형식이면 역할만 만든다", () => {
  assert.deepEqual(
      mergeSupabaseAuthenticatedRole(null),
      {role: "authenticated"},
  );
  assert.deepEqual(
      mergeSupabaseAuthenticatedRole([]),
      {role: "authenticated"},
  );
});
