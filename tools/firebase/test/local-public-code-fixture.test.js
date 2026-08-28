const assert = require("node:assert/strict");
const test = require("node:test");

const {
  PUBLIC_CODE_PATTERN,
  createLocalPublicCodeCandidate,
  generateLocalPublicCodeFixtures,
} = require("../lib/local-public-code-fixture");

test("로컬 fixture 공고번호는 BD-와 영문·숫자 6자리 형식을 따른다", () => {
  const indexes = [0, 1, 26, 27, 2, 3];
  let cursor = 0;

  const candidate = createLocalPublicCodeCandidate(() => indexes[cursor++]);

  assert.equal(candidate, "BD-AB01CD");
  assert.match(candidate, PUBLIC_CODE_PATTERN);
});

test("예약된 공고번호와 충돌하면 새 후보로 재시도한다", () => {
  const candidates = ["BD-ABC123", "BD-ABC123", "BD-XYZ789"];
  let cursor = 0;

  const [fixture] = generateLocalPublicCodeFixtures({
    count: 1,
    reservedCodes: ["bd-abc123"],
    candidateFactory: () => candidates[cursor++],
    maxAttemptsPerCode: 3,
  });

  assert.deepEqual(fixture, {code: "BD-XYZ789", attempts: 3});
});

test("한 번에 만든 fixture끼리도 중복되지 않는다", () => {
  const candidates = ["BD-AAAAAA", "BD-AAAAAA", "BD-BBBBBB"];
  let cursor = 0;

  const fixtures = generateLocalPublicCodeFixtures({
    count: 2,
    candidateFactory: () => candidates[cursor++],
    maxAttemptsPerCode: 2,
  });

  assert.deepEqual(fixtures, [
    {code: "BD-AAAAAA", attempts: 1},
    {code: "BD-BBBBBB", attempts: 2},
  ]);
});

test("충돌 재시도 한도를 모두 쓰면 실패한다", () => {
  assert.throws(
      () => generateLocalPublicCodeFixtures({
        count: 1,
        reservedCodes: ["BD-ABC123"],
        candidateFactory: () => "BD-ABC123",
        maxAttemptsPerCode: 2,
      }),
      /충돌을 2회 안에 해소하지 못했습니다/,
  );
});

test("운영 계약으로 오인할 수 있는 형식은 거부한다", () => {
  assert.throws(
      () => generateLocalPublicCodeFixtures({
        candidateFactory: () => "RESERVATION-1",
      }),
      /BD-와 영문·숫자 6자리/,
  );
});
