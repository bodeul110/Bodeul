#!/usr/bin/env node

const {
  BASELINE_GUIDES,
  BASELINE_USERS,
  DAY_IN_MILLIS,
  MANAGED_COLLECTIONS,
} = require("./lib/baseline-config");
const {
  createCliContext,
  deleteCollectionDocuments,
  getCollectionCounts,
  patchDocumentData,
  resolveBaselineUsers,
} = require("./lib/firebase-toolkit");

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }

  const context = await createCliContext();
  const baselineUsers = await resolveBaselineUsers(context, BASELINE_USERS, options.apply);
  const collectionCounts = await getCollectionCounts(context, MANAGED_COLLECTIONS);

  printPlan(options, context.projectId, collectionCounts, baselineUsers);

  if (!options.apply) {
    console.log("");
    console.log("dry-run 완료: 실제 삭제와 기준선 재생성은 수행하지 않았습니다.");
    return;
  }

  console.log("");
  console.log("Firestore 기준선 초기화를 시작합니다.");

  const deletedCounts = {};
  for (const collectionName of MANAGED_COLLECTIONS) {
    deletedCounts[collectionName] = await deleteCollectionDocuments(context, collectionName);
  }

  await seedUsers(context, baselineUsers.foundUsers);
  await seedHospitalGuides(context);

  console.log("");
  console.log("초기화가 끝났습니다.");
  console.log("삭제된 문서 수:");
  for (const collectionName of MANAGED_COLLECTIONS) {
    console.log(`- ${collectionName}: ${deletedCounts[collectionName]}건 삭제`);
  }
  console.log("");
  console.log(`재생성된 users 문서: ${baselineUsers.foundUsers.length}건`);
  console.log(`재생성된 hospitalGuides 문서: ${BASELINE_GUIDES.length}건`);
}

function parseOptions(args) {
  return {
    apply: args.includes("--apply"),
    help: args.includes("--help") || args.includes("-h"),
  };
}

function printHelp() {
  console.log("보들 Firebase 개발용 기준선 초기화 스크립트");
  console.log("");
  console.log("사용법:");
  console.log("  node reset-firestore-baseline.js --dry-run");
  console.log("  node reset-firestore-baseline.js --apply");
  console.log("");
  console.log("옵션:");
  console.log("  --apply  Firestore 초기화와 기준선 재생성을 실제로 수행합니다.");
  console.log("  --help   도움말을 출력합니다.");
  console.log("");
  console.log("기본 동작:");
  console.log("- firebase CLI 로그인 토큰을 사용합니다.");
  console.log("- 기준선 Auth 계정이 없으면 apply 시 자동으로 생성합니다.");
  console.log("- users, hospitalGuides를 포함한 Firestore 컬렉션을 기준선으로 다시 맞춥니다.");
}

function printPlan(options, projectId, collectionCounts, baselineUsers) {
  console.log("보들 Firebase 기준선 초기화 계획");
  console.log(`프로젝트: ${projectId}`);
  console.log(`모드: ${options.apply ? "apply" : "dry-run"}`);
  console.log("");
  console.log("삭제 대상 컬렉션:");
  for (const collectionName of MANAGED_COLLECTIONS) {
    console.log(`- ${collectionName}: 현재 ${collectionCounts[collectionName]}건`);
  }
  console.log("");
  console.log("기준선 계정:");
  for (const user of baselineUsers.foundUsers) {
    console.log(`- ${user.role} ${user.email} (${user.uid})`);
  }
  if (baselineUsers.missingUsers.length > 0) {
    console.log("");
    console.log(options.apply ? "생성될 기준선 Auth 계정:" : "누락된 기준선 Auth 계정:");
    for (const user of baselineUsers.missingUsers) {
      console.log(`- ${user.role} ${user.email}`);
    }
  }
  console.log("");
  console.log("기준선 병원 가이드:");
  for (const guide of BASELINE_GUIDES) {
    console.log(`- ${guide.hospitalName} / ${guide.departmentName}`);
  }
}

async function seedUsers(context, baselineUsers) {
  const now = Date.now();
  const submittedAt = now - (3 * DAY_IN_MILLIS);
  const reviewedAt = now - (2 * DAY_IN_MILLIS);

  for (const baselineUser of baselineUsers) {
    const document = {
      name: baselineUser.name,
      email: baselineUser.email,
      phone: baselineUser.phone,
      role: baselineUser.role,
      provider: "EMAIL",
      providerUserId: baselineUser.uid,
      createdAt: now,
      updatedAt: now,
    };

    if (baselineUser.managerProfile) {
      document.managerDocumentSummary = baselineUser.managerProfile.managerDocumentSummary;
      document.managerAvailabilitySummary = baselineUser.managerProfile.managerAvailabilitySummary;
      document.managerDocumentStatus = baselineUser.managerProfile.managerDocumentStatus;
      document.managerDocumentReviewNote = baselineUser.managerProfile.managerDocumentReviewNote;
      document.managerDocumentReviewedByName = baselineUser.managerProfile.managerDocumentReviewedByName;
      document.managerDocumentUpdatedAt = submittedAt;
      document.managerDocumentReviewedAt = reviewedAt;
      document.managerDocumentHistory = [
        {
          eventType: "APPROVED",
          happenedAt: reviewedAt,
          actorName: "관리자",
          summary: baselineUser.managerProfile.managerDocumentSummary,
          reviewNote: baselineUser.managerProfile.managerDocumentReviewNote,
        },
        {
          eventType: "SUBMITTED",
          happenedAt: submittedAt,
          actorName: baselineUser.name,
          summary: baselineUser.managerProfile.managerDocumentSummary,
          reviewNote: "",
        },
      ];
    }

    await patchDocumentData(context, `users/${baselineUser.uid}`, document);
  }
}

async function seedHospitalGuides(context) {
  const now = Date.now();
  for (const guide of BASELINE_GUIDES) {
    await patchDocumentData(context, `hospitalGuides/${guide.id}`, {
      hospitalName: guide.hospitalName,
      departmentName: guide.departmentName,
      steps: guide.steps,
      createdAt: now,
      updatedAt: now,
    });
  }
}

main().catch((error) => {
  console.error("기준선 초기화 스크립트 실행 중 오류가 발생했습니다.");
  console.error(error);
  process.exitCode = 1;
});
