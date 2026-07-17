#!/usr/bin/env node

const {
  createCliContext,
  extractRelativeDocumentPath,
  getDocument,
  listCollectionDocuments,
  patchDocumentData,
} = require("./lib/firebase-toolkit");
const {
  buildSessionParticipantPatch,
  fromFirestoreDocument,
} = require("./lib/session-participant-migration");

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }

  const context = await createCliContext();
  const sessions = await listCollectionDocuments(context, "companionSessions");
  const appointmentCache = new Map();
  const changes = [];
  const blockers = [];

  for (const sessionDocument of sessions) {
    const sessionPath = extractRelativeDocumentPath(sessionDocument.name, context.projectId);
    const sessionData = fromFirestoreDocument(sessionDocument);
    const appointmentRequestId = normalizeText(sessionData.appointmentRequestId);
    if (!appointmentRequestId) {
      blockers.push({path: sessionPath, reason: "appointmentRequestId가 없습니다."});
      continue;
    }

    let appointmentDocument = appointmentCache.get(appointmentRequestId);
    if (appointmentDocument === undefined) {
      appointmentDocument = await getDocument(
          context,
          `appointmentRequests/${appointmentRequestId}`,
      );
      appointmentCache.set(appointmentRequestId, appointmentDocument);
    }
    if (!appointmentDocument) {
      blockers.push({path: sessionPath, reason: "연결된 예약 문서를 찾지 못했습니다."});
      continue;
    }

    try {
      const patch = buildSessionParticipantPatch(
          sessionData,
          fromFirestoreDocument(appointmentDocument),
      );
      if (Object.keys(patch).length > 0) {
        changes.push({path: sessionPath, patch});
      }
    } catch (error) {
      blockers.push({path: sessionPath, reason: error.message});
    }
  }

  printPlan(options, context.projectId, sessions.length, changes, blockers);
  if (blockers.length > 0) {
    throw new Error("참가자 정보를 안전하게 확정할 수 없는 세션이 있습니다.");
  }
  if (!options.apply) {
    console.log("");
    console.log("dry-run 완료: Firestore 문서는 변경하지 않았습니다.");
    return;
  }

  for (const change of changes) {
    await patchDocumentData(context, change.path, change.patch);
  }
  console.log("");
  console.log(`참가자 정보 보완 완료: ${changes.length}건`);
}

function parseOptions(args) {
  return {
    apply: args.includes("--apply"),
    help: args.includes("--help") || args.includes("-h"),
  };
}

function printHelp() {
  console.log("동행 세션 참가자 정보 보완 도구");
  console.log("");
  console.log("사용법:");
  console.log("  node backfill-companion-session-participants.js");
  console.log("  node backfill-companion-session-participants.js --apply");
  console.log("");
  console.log("기본 동작은 dry-run이며 --apply일 때만 Firestore를 수정합니다.");
}

function printPlan(options, projectId, sessionCount, changes, blockers) {
  console.log("동행 세션 참가자 정보 보완 계획");
  console.log(`프로젝트: ${projectId}`);
  console.log(`모드: ${options.apply ? "apply" : "dry-run"}`);
  console.log(`전체 세션: ${sessionCount}건`);
  console.log(`변경 대상: ${changes.length}건`);
  for (const change of changes) {
    console.log(`- ${change.path}`);
  }
  console.log(`차단 항목: ${blockers.length}건`);
  for (const blocker of blockers) {
    console.log(`- ${blocker.path}: ${blocker.reason}`);
  }
}

function normalizeText(value) {
  return value === null || value === undefined ? "" : String(value).trim();
}

main().catch((error) => {
  console.error("동행 세션 참가자 정보 보완 중 오류가 발생했습니다.");
  console.error(error.message);
  process.exitCode = 1;
});
