#!/usr/bin/env node

const {createCliContext} = require("./lib/firebase-toolkit");
const {
  buildRoleReadiness,
  loadOperationsSnapshot,
} = require("./lib/operations-report");

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }

  const context = await createCliContext();
  const snapshot = await loadOperationsSnapshot(context);
  const readiness = buildRoleReadiness(snapshot);

  if (options.json) {
    console.log(JSON.stringify({
      projectId: snapshot.projectId,
      generatedAt: snapshot.generatedAt,
      roles: readiness.roles,
      scenarios: readiness.scenarios,
    }, null, 2));
    return;
  }

  console.log("보들 역할별 화면 진입 점검");
  console.log(`프로젝트: ${snapshot.projectId}`);
  console.log(`생성 시각: ${snapshot.generatedAt}`);

  console.log("");
  console.log("샘플 시나리오:");
  for (const scenario of readiness.scenarios) {
    console.log(
        `- ${scenario.label}: ${scenario.pass ? "통과" : "미달"} | ${scenario.detail}`,
    );
  }

  console.log("");
  console.log("역할별 점검:");
  for (const role of readiness.roles) {
    console.log(
        `- ${role.label}: ${role.ready ? "준비됨" : "확인 필요"} (${role.checks.filter((check) => check.pass).length}/${role.checks.length})`,
    );
    for (const check of role.checks) {
      console.log(`  * ${check.label}: ${check.pass ? "통과" : "미달"} | ${check.detail}`);
    }
  }
}

function parseOptions(args) {
  return {
    help: args.includes("--help") || args.includes("-h"),
    json: args.includes("--json"),
  };
}

function printHelp() {
  console.log("보들 역할별 화면 진입 점검 스크립트");
  console.log("");
  console.log("사용법:");
  console.log("  node check-role-screen-readiness.js");
  console.log("  node check-role-screen-readiness.js --json");
}

main().catch((error) => {
  console.error("화면 진입 점검 스크립트 실행 중 오류가 발생했습니다.");
  console.error(error);
  process.exitCode = 1;
});
