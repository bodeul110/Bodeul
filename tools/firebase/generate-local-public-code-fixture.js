#!/usr/bin/env node

const {
  generateLocalPublicCodeFixtures,
} = require("./lib/local-public-code-fixture");

function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }

  const fixtures = generateLocalPublicCodeFixtures({count: options.count});
  console.log(JSON.stringify({
    scope: "local_synthetic_fixture",
    productionReady: false,
    fixtures,
  }, null, 2));
}

function parseOptions(args) {
  const countIndex = args.indexOf("--count");
  const rawCount = countIndex >= 0 ? args[countIndex + 1] : "1";
  if (!/^\d+$/.test(rawCount || "")) {
    throw new Error("--count에는 1 이상 1000 이하의 정수를 입력해야 합니다.");
  }
  return {
    count: Number(rawCount),
    help: args.includes("--help") || args.includes("-h"),
  };
}

function printHelp() {
  console.log("로컬 합성 fixture용 예약 공고번호 생성기");
  console.log("");
  console.log("사용법:");
  console.log("  node generate-local-public-code-fixture.js --count 10");
  console.log("");
  console.log("생성 결과는 DB·API·운영 발급 계약과 연결되지 않습니다.");
}

try {
  main();
} catch (error) {
  console.error("로컬 fixture 공고번호 생성에 실패했습니다.");
  console.error(error instanceof Error ? error.message : error);
  process.exitCode = 1;
}
