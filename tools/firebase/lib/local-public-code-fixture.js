const crypto = require("crypto");

const PUBLIC_CODE_PREFIX = "BD-";
const PUBLIC_CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
const PUBLIC_CODE_BODY_LENGTH = 6;
const PUBLIC_CODE_PATTERN = /^BD-[A-Z0-9]{6}$/;
const DEFAULT_MAX_ATTEMPTS_PER_CODE = 20;
const MAX_FIXTURE_COUNT = 1000;

function createLocalPublicCodeCandidate(randomIndex = defaultRandomIndex) {
  let body = "";
  for (let index = 0; index < PUBLIC_CODE_BODY_LENGTH; index += 1) {
    body += PUBLIC_CODE_ALPHABET[randomIndex(PUBLIC_CODE_ALPHABET.length)];
  }
  return `${PUBLIC_CODE_PREFIX}${body}`;
}

function generateLocalPublicCodeFixtures({
  count = 1,
  reservedCodes = [],
  candidateFactory = createLocalPublicCodeCandidate,
  maxAttemptsPerCode = DEFAULT_MAX_ATTEMPTS_PER_CODE,
} = {}) {
  assertIntegerInRange(count, "count", 1, MAX_FIXTURE_COUNT);
  assertIntegerInRange(maxAttemptsPerCode, "maxAttemptsPerCode", 1, 1000);
  if (typeof candidateFactory !== "function") {
    throw new TypeError("candidateFactory는 함수여야 합니다.");
  }

  const usedCodes = new Set(normalizeReservedCodes(reservedCodes));
  const fixtures = [];

  for (let fixtureIndex = 0; fixtureIndex < count; fixtureIndex += 1) {
    const fixture = generateUniqueFixture({
      usedCodes,
      candidateFactory,
      maxAttemptsPerCode,
    });
    usedCodes.add(fixture.code);
    fixtures.push(fixture);
  }

  return fixtures;
}

function generateUniqueFixture({usedCodes, candidateFactory, maxAttemptsPerCode}) {
  for (let attempt = 1; attempt <= maxAttemptsPerCode; attempt += 1) {
    const candidate = String(candidateFactory()).trim().toUpperCase();
    if (!PUBLIC_CODE_PATTERN.test(candidate)) {
      throw new Error("로컬 fixture 공고번호는 BD-와 영문·숫자 6자리 형식이어야 합니다.");
    }
    if (!usedCodes.has(candidate)) {
      return {code: candidate, attempts: attempt};
    }
  }

  throw new Error(`로컬 fixture 공고번호 충돌을 ${maxAttemptsPerCode}회 안에 해소하지 못했습니다.`);
}

function normalizeReservedCodes(reservedCodes) {
  if (!Array.isArray(reservedCodes)) {
    throw new TypeError("reservedCodes는 배열이어야 합니다.");
  }
  return reservedCodes
      .map((code) => String(code).trim().toUpperCase())
      .filter((code) => PUBLIC_CODE_PATTERN.test(code));
}

function defaultRandomIndex(upperBound) {
  return crypto.randomInt(upperBound);
}

function assertIntegerInRange(value, name, minimum, maximum) {
  if (!Number.isInteger(value) || value < minimum || value > maximum) {
    throw new RangeError(`${name}는 ${minimum} 이상 ${maximum} 이하의 정수여야 합니다.`);
  }
}

module.exports = {
  PUBLIC_CODE_PATTERN,
  createLocalPublicCodeCandidate,
  generateLocalPublicCodeFixtures,
};
