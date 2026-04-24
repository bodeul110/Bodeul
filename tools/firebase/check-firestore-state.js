#!/usr/bin/env node

const {BASELINE_USERS, MANAGED_COLLECTIONS} = require("./lib/baseline-config");
const {
  createCliContext,
  getCollectionCounts,
  getDocument,
  lookupAuthUserByEmail,
} = require("./lib/firebase-toolkit");

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }

  const context = await createCliContext();
  const collectionCounts = await getCollectionCounts(context, MANAGED_COLLECTIONS);
  const baselineStatuses = await loadBaselineStatuses(context);

  if (options.json) {
    console.log(JSON.stringify({
      projectId: context.projectId,
      collectionCounts,
      baselineStatuses,
    }, null, 2));
    return;
  }

  console.log("보들 Firebase 상태 점검");
  console.log(`프로젝트: ${context.projectId}`);
  console.log("");
  console.log("기준선 계정:");
  for (const status of baselineStatuses) {
    console.log(
        `- ${status.role} ${status.email} | Auth=${status.authStatus} | users 문서=${status.userDocumentStatus}${
          status.uid ? ` | uid=${status.uid}` : ""
        }`,
    );
  }

  console.log("");
  console.log("컬렉션 문서 수:");
  for (const collectionName of MANAGED_COLLECTIONS) {
    console.log(`- ${collectionName}: ${collectionCounts[collectionName]}건`);
  }
}

function parseOptions(args) {
  return {
    help: args.includes("--help") || args.includes("-h"),
    json: args.includes("--json"),
  };
}

function printHelp() {
  console.log("보들 Firebase 상태 점검 스크립트");
  console.log("");
  console.log("사용법:");
  console.log("  node check-firestore-state.js");
  console.log("  node check-firestore-state.js --json");
}

async function loadBaselineStatuses(context) {
  const statuses = [];
  for (const baselineUser of BASELINE_USERS) {
    const authUser = await lookupAuthUserByEmail(context, baselineUser.email);
    if (!authUser) {
      statuses.push({
        role: baselineUser.role,
        email: baselineUser.email,
        uid: "",
        authStatus: "missing",
        userDocumentStatus: "missing",
      });
      continue;
    }

    const userDocument = await getDocument(context, `users/${authUser.localId}`);
    statuses.push({
      role: baselineUser.role,
      email: baselineUser.email,
      uid: authUser.localId,
      authStatus: "present",
      userDocumentStatus: userDocument ? "present" : "missing",
    });
  }
  return statuses;
}

main().catch((error) => {
  console.error("상태 점검 스크립트 실행 중 오류가 발생했습니다.");
  console.error(error);
  process.exitCode = 1;
});
