const test = require("node:test");
const assert = require("node:assert/strict");

const {
  buildSupabaseClaims,
  needsSupabaseRole,
  parseCustomClaims,
  serializeClaims,
} = require("../lib/supabase-auth-claims");

test("기존 claim을 보존하면서 authenticated 역할을 설정한다", () => {
  assert.deepEqual(
      buildSupabaseClaims('{"admin":true,"accessLevel":2}'),
      {admin: true, accessLevel: 2, role: "authenticated"},
  );
  assert.equal(needsSupabaseRole('{"role":"authenticated"}'), false);
  assert.equal(needsSupabaseRole('{"role":"ADMIN"}'), true);
});

test("잘못된 claim 형식은 거부한다", () => {
  assert.throws(() => parseCustomClaims("[]"), /JSON 객체/);
  assert.throws(() => parseCustomClaims("{"), /JSON/);
});

test("Firebase 1,000바이트 제한을 적용한다", () => {
  assert.equal(
      serializeClaims('{"admin":true}'),
      '{"admin":true,"role":"authenticated"}',
  );
  assert.throws(
      () => serializeClaims({large: "x".repeat(1000)}),
      /1,000바이트/,
  );
});
