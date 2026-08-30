#!/usr/bin/env node

const {createHash} = require("node:crypto");

const {
  copyStorageObject,
  createCliContext,
  deleteStorageObject,
  getStorageObject,
  listCollectionDocuments,
  runDocumentTransaction,
} = require("./lib/firebase-toolkit");

const SOURCE_KEY = "healthCertificate";
const DESTINATION_KEY = "nursingLicense";
const LEGACY_ALIAS = "managerHealthCertificateStoragePath";
const DELETION_CLAIM_FIELD = "managerDocumentDeletionClaim";
const DELETION_CLAIM_VERSION = 1;
const DELETION_CLAIM_OPERATION = "MIGRATION";
const CLAIM_STATE_CLAIMED = "CLAIMED";
const CLAIM_STATE_READY = "READY";
const MAX_DOCUMENT_SIZE = 10 * 1024 * 1024;
const ALLOWED_CONTENT_TYPES = new Set([
  "image/jpeg",
  "image/png",
  "image/webp",
]);

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }

  const context = await createCliContext();
  const managers = await loadManagers(context, options.uid);
  if (options.uid && managers.length === 0) {
    throw new Error(`지정한 MANAGER 문서를 찾지 못했습니다: ${options.uid}`);
  }
  const entries = [];
  for (const manager of managers) {
    const objectPaths = resolveMigrationObjectPaths(manager.id, manager.data);
    const [sourceObject, destinationObject] = await Promise.all([
      objectPaths.sourcePath
        ? getStorageObject(context, objectPaths.sourcePath)
        : Promise.resolve(null),
      objectPaths.destinationPath
        ? getStorageObject(context, objectPaths.destinationPath)
        : Promise.resolve(null),
    ]);
    const plan = buildMigrationPlan({
      managerId: manager.id,
      data: manager.data,
      sourceObject,
      destinationObject,
    });
    entries.push({manager, plan});
  }

  const applyBlocked = shouldBlockApply(
      entries.map((entry) => entry.plan),
      options.apply,
  );
  const results = [];
  for (const {manager, plan} of entries) {

    if (!options.apply || applyBlocked ||
        plan.action === "NOOP" || plan.action === "BLOCKED") {
      results.push(summarizePlan(manager, plan, false));
      continue;
    }

    const runManagerTransaction = async (mutationBuilder) => {
      let currentDecision = null;
      await runDocumentTransaction(
          context,
          `users/${manager.id}`,
          (document) => {
            if (!document) {
              throw new Error("Firestore 매니저 문서가 transaction 중 사라졌습니다.");
            }
            currentDecision = mutationBuilder(fromFirestoreDocument(document));
            return currentDecision?.mutation || null;
          },
      );
      return currentDecision;
    };
    await applyMigrationPlan(plan, {
      copySource: () => copyStorageObject(
          context,
          plan.sourcePath,
          plan.destinationPath,
          plan.sourceObject.generation,
      ),
      getSource: () => getStorageObject(context, plan.sourcePath),
      getDestination: () => getStorageObject(context, plan.destinationPath),
      claimDeletion: runManagerTransaction,
      prepareDeletion: runManagerTransaction,
      finalizeDeletion: runManagerTransaction,
      deleteSource: (generation) => deleteStorageObject(
          context,
          plan.sourcePath,
          generation,
      ),
    });
    results.push(summarizePlan(manager, plan, true));
  }

  const summary = {
    projectId: context.projectId,
    storageBucket: context.storageBucket,
    mode: options.apply ? "apply" : "dry-run",
    applyBlocked,
    managerCount: managers.length,
    blockedCount: results.filter((result) => result.action === "BLOCKED").length,
    results,
  };
  if (options.json) {
    console.log(JSON.stringify(summary, null, 2));
  } else {
    printSummary(summary);
  }
  if (summary.blockedCount > 0) {
    process.exitCode = 1;
  }
}

function parseOptions(args) {
  const options = {apply: false, help: false, json: false, uid: ""};
  for (let index = 0; index < args.length; index++) {
    const argument = args[index];
    if (argument === "--apply") {
      options.apply = true;
    } else if (argument === "--help" || argument === "-h") {
      options.help = true;
    } else if (argument === "--json") {
      options.json = true;
    } else if (argument === "--uid" && args[index + 1]) {
      options.uid = sanitizeText(args[index + 1]);
      index += 1;
    } else {
      throw new Error(`지원하지 않는 인자입니다: ${argument}`);
    }
  }
  return options;
}

function printHelp() {
  console.log("매니저 건강진단서 레거시 자격 증빙 이관");
  console.log("");
  console.log("사용법");
  console.log("  node migrate-manager-health-certificate.js");
  console.log("  node migrate-manager-health-certificate.js --uid <uid>");
  console.log("  node migrate-manager-health-certificate.js --apply --uid <uid>");
  console.log("");
  console.log("- 기본값은 dry-run이며 --apply를 명시해야만 변경합니다.");
  console.log("- Storage 복사, Firestore transaction, 구 객체 generation 조건부 삭제 순서로 실행합니다.");
}

async function loadManagers(context, requestedUid) {
  const documents = await listCollectionDocuments(context, "users");
  return documents
      .map((document) => ({
        id: document.name.split("/").pop(),
        data: fromFirestoreDocument(document),
      }))
      .filter((manager) => manager.data.role === "MANAGER")
      .filter((manager) => !requestedUid || manager.id === requestedUid);
}

function resolveMigrationObjectPaths(managerId, data) {
  const files = isPlainObject(data?.managerDocumentFiles)
      ? data.managerDocumentFiles
      : {};
  const paths = isPlainObject(data?.managerDocumentFilePaths)
      ? data.managerDocumentFilePaths
      : {};
  const sourcePath = sanitizeText(
      data?.[DELETION_CLAIM_FIELD]?.storagePath ||
      files[SOURCE_KEY]?.fullPath ||
      paths[SOURCE_KEY] ||
      data?.[LEGACY_ALIAS] ||
      data?.managerDocumentEvidenceMigration?.sourcePath,
  );
  const canonicalPath = sanitizeText(
      files[DESTINATION_KEY]?.fullPath || paths[DESTINATION_KEY],
  );
  const pathForFileName = sourcePath || canonicalPath;
  const fileName = pathForFileName.split("/").pop() || "";
  return {
    sourcePath: sourcePath || (
      canonicalPath && fileName
        ? `manager-documents/${managerId}/${SOURCE_KEY}/${fileName}`
        : ""
    ),
    destinationPath: canonicalPath || (
      sourcePath && fileName
        ? `manager-documents/${managerId}/${DESTINATION_KEY}/${fileName}`
        : ""
    ),
  };
}

function buildMigrationPlan({managerId, data, sourceObject, destinationObject}) {
  const files = isPlainObject(data?.managerDocumentFiles)
      ? data.managerDocumentFiles
      : {};
  const paths = isPlainObject(data?.managerDocumentFilePaths)
      ? data.managerDocumentFilePaths
      : {};
  const hasHealthFile = hasOwn(files, SOURCE_KEY);
  const hasHealthPath = hasOwn(paths, SOURCE_KEY);
  const hasHealthAlias = hasOwn(data || {}, LEGACY_ALIAS);
  const hasLegacy = hasHealthFile || hasHealthPath || hasHealthAlias;
  const hasLicense = hasOwn(files, "license") ||
    hasOwn(paths, "license") ||
    hasOwn(data || {}, "managerLicenseStoragePath");
  const hasNursing = hasOwn(files, DESTINATION_KEY) ||
    hasOwn(paths, DESTINATION_KEY);
  const objectPaths = resolveMigrationObjectPaths(managerId, data);

  if (!isExactText(managerId)) {
    return blockedPlan(managerId, "매니저 UID가 비어 있거나 공백을 포함합니다.");
  }
  if (data?.role !== "MANAGER") {
    return blockedPlan(managerId, "현재 사용자 역할이 MANAGER가 아닙니다.");
  }
  const legalHoldIssue = validateManagerDocumentLegalHold(data);
  if (legalHoldIssue) {
    return blockedPlan(managerId, `legal hold 검증 실패: ${legalHoldIssue}`);
  }
  const claimValidation = validateDeletionClaim(
      data?.[DELETION_CLAIM_FIELD],
      managerId,
      objectPaths.sourcePath,
  );
  if (claimValidation.issue) {
    return blockedPlan(managerId, `삭제 claim 검증 실패: ${claimValidation.issue}`);
  }
  if (hasLicense && hasNursing) {
    return blockedPlan(managerId, "canonical 자격 증빙이 둘 이상입니다.");
  }

  if (claimValidation.claim) {
    if (hasLegacy || hasLicense || !hasNursing) {
      return blockedPlan(
          managerId,
          "삭제 claim 중에는 canonical nursingLicense만 참조해야 합니다.",
      );
    }
    const canonicalIssue = validateCanonicalMigrationState(
        data,
        managerId,
        objectPaths.sourcePath,
        objectPaths.destinationPath,
    );
    if (canonicalIssue) {
      return blockedPlan(managerId, `claim canonical 상태 검증 실패: ${canonicalIssue}`);
    }
    const destinationIssue = validateStorageObject(
        destinationObject,
        objectPaths.destinationPath,
    );
    if (destinationIssue) {
      return blockedPlan(managerId, `canonical Storage 검증 실패: ${destinationIssue}`);
    }
    if (sourceObject) {
      const sourceIssue = validateStorageObject(sourceObject, objectPaths.sourcePath);
      const matchIssue = sourceIssue || compareStorageObjects(
          sourceObject,
          destinationObject,
          objectPaths.destinationPath,
      );
      if (matchIssue) {
        return blockedPlan(managerId, `claim 원본 검증 실패: ${matchIssue}`);
      }
      if (claimValidation.claim.state === CLAIM_STATE_READY &&
          (claimValidation.claim.objectMissing ||
           claimValidation.claim.objectGeneration !==
             sanitizeText(sourceObject.generation))) {
        return blockedPlan(managerId, "READY claim의 원본 generation 상태가 다릅니다.");
      }
    }
    return {
      action: "RESUME_CLAIM",
      managerId,
      sourcePath: objectPaths.sourcePath,
      destinationPath: objectPaths.destinationPath,
      sourceObject,
      destinationObject,
      claim: claimValidation.claim,
      data,
      reason: "기존 삭제 claim을 이어서 원본 삭제 또는 finalize를 수행합니다.",
    };
  }

  if (hasLegacy) {
    if (hasLicense || hasNursing) {
      return blockedPlan(managerId, "레거시와 canonical 메타데이터가 동시에 존재합니다.");
    }
    if (!hasHealthFile || !hasHealthPath || !isPlainObject(files[SOURCE_KEY])) {
      return blockedPlan(managerId, "healthCertificate 메타데이터 또는 path map이 부분 상태입니다.");
    }
    const metadataPath = files[SOURCE_KEY].fullPath;
    const pathMap = paths[SOURCE_KEY];
    const alias = data[LEGACY_ALIAS];
    if (!isExpectedPath(metadataPath, managerId, SOURCE_KEY) ||
        metadataPath !== pathMap ||
        (hasHealthAlias && metadataPath !== alias)) {
      return blockedPlan(managerId, "healthCertificate 경로 귀속 또는 alias가 일치하지 않습니다.");
    }
    if (!objectPaths.destinationPath ||
        !isExpectedPath(objectPaths.destinationPath, managerId, DESTINATION_KEY)) {
      return blockedPlan(managerId, "nursingLicense 대상 경로를 안전하게 계산할 수 없습니다.");
    }
    const sourceIssue = validateStorageObject(sourceObject, metadataPath);
    if (sourceIssue) {
      return blockedPlan(managerId, `원본 Storage 검증 실패: ${sourceIssue}`);
    }
    const base = {
      action: destinationObject ? "UPDATE_METADATA" : "COPY_AND_UPDATE",
      managerId,
      sourcePath: metadataPath,
      destinationPath: objectPaths.destinationPath,
      sourceObject,
      destinationObject,
      data,
      reason: destinationObject
        ? "검증된 이전 복사본을 이어서 Firestore 메타데이터를 교체합니다."
        : "원본을 canonical 경로로 복사한 뒤 Firestore 메타데이터를 교체합니다.",
    };
    if (destinationObject) {
      const matchIssue = compareStorageObjects(
          sourceObject,
          destinationObject,
          objectPaths.destinationPath,
      );
      if (matchIssue) {
        return blockedPlan(managerId, `기존 대상 객체 충돌: ${matchIssue}`);
      }
    }
    return base;
  }

  if (hasLicense) {
    const licenseMetadata = files.license;
    const licensePath = paths.license;
    const licenseAlias = data.managerLicenseStoragePath;
    if (!isPlainObject(licenseMetadata) ||
        !isExpectedPath(licenseMetadata.fullPath, managerId, "license") ||
        licenseMetadata.fullPath !== licensePath ||
        licenseMetadata.fullPath !== licenseAlias) {
      return blockedPlan(managerId, "license canonical 메타데이터가 부분 상태입니다.");
    }
    return {
      action: "NOOP",
      managerId,
      reason: "license canonical 증빙은 healthCertificate 이관 대상이 아닙니다.",
    };
  }
  if (!hasNursing) {
    return {
      action: "NOOP",
      managerId,
      reason: "healthCertificate 레거시 참조가 없습니다.",
    };
  }

  const nursingMetadata = files[DESTINATION_KEY];
  const nursingPath = paths[DESTINATION_KEY];
  if (!isPlainObject(nursingMetadata) ||
      !isExpectedPath(nursingMetadata.fullPath, managerId, DESTINATION_KEY) ||
      nursingMetadata.fullPath !== nursingPath) {
    return blockedPlan(managerId, "nursingLicense canonical 메타데이터가 부분 상태입니다.");
  }
  const destinationIssue = validateStorageObject(
      destinationObject,
      nursingMetadata.fullPath,
  );
  if (destinationIssue) {
    return blockedPlan(managerId, `canonical Storage 검증 실패: ${destinationIssue}`);
  }
  if (!sourceObject) {
    return {
      action: "NOOP",
      managerId,
      destinationPath: nursingMetadata.fullPath,
      destinationObject,
      reason: "canonical 이관과 구 객체 삭제가 이미 완료됐습니다.",
    };
  }
  const canonicalIssue = validateCanonicalMigrationState(
      data,
      managerId,
      objectPaths.sourcePath,
      nursingMetadata.fullPath,
  );
  if (canonicalIssue) {
    return blockedPlan(managerId, `삭제 대기 canonical 상태 검증 실패: ${canonicalIssue}`);
  }
  const sourceIssue = validateStorageObject(sourceObject, objectPaths.sourcePath);
  const matchIssue = sourceIssue || compareStorageObjects(
      sourceObject,
      destinationObject,
      nursingMetadata.fullPath,
  );
  if (matchIssue) {
    return blockedPlan(managerId, `삭제 대기 원본 검증 실패: ${matchIssue}`);
  }
  return {
    action: "CLEANUP_SOURCE",
    managerId,
    sourcePath: objectPaths.sourcePath,
    destinationPath: nursingMetadata.fullPath,
    sourceObject,
    destinationObject,
    data,
    reason: "Firestore 교체는 완료됐고 generation이 일치하는 구 객체만 삭제합니다.",
  };
}

async function applyMigrationPlan(plan, dependencies) {
  if (plan.action === "BLOCKED") {
    throw new Error(plan.reason);
  }
  if (plan.action === "NOOP") {
    return {action: "NOOP"};
  }

  let destinationObject = plan.destinationObject;
  if (plan.action === "COPY_AND_UPDATE") {
    await dependencies.copySource();
    destinationObject = await dependencies.getDestination();
    const matchIssue = compareStorageObjects(
        plan.sourceObject,
        destinationObject,
        plan.destinationPath,
    );
    if (matchIssue) {
      throw new Error(`복사 결과 검증 실패: ${matchIssue}`);
    }
  }

  const latestDestination = await dependencies.getDestination();
  const latestDestinationIssue = plan.sourceObject
    ? compareStorageObjects(
        plan.sourceObject,
        latestDestination,
        plan.destinationPath,
    )
    : validateStorageObject(latestDestination, plan.destinationPath);
  if (latestDestinationIssue) {
    throw new Error(`claim 직전 대상 객체 검증 실패: ${latestDestinationIssue}`);
  }

  const sourceBeforeClaim = await dependencies.getSource();
  if (sourceBeforeClaim && plan.sourceObject) {
    const sourceRaceIssue = compareStorageObjects(
        plan.sourceObject,
        sourceBeforeClaim,
        plan.sourcePath,
    );
    if (sourceRaceIssue ||
        sanitizeText(sourceBeforeClaim.generation) !==
          sanitizeText(plan.sourceObject.generation)) {
      throw new Error(`claim 직전 원본 객체 변경 감지: ${sourceRaceIssue || "generation이 다릅니다."}`);
    }
  }
  const sourceForClaim = sourceBeforeClaim || plan.sourceObject;

  const claimedAt = resolveClaimedAt(dependencies.claimedAt);
  const claimDecision = await dependencies.claimDeletion((currentData) =>
    buildClaimDecision({
      managerId: plan.managerId,
      data: currentData,
      sourceObject: sourceForClaim,
      destinationObject: latestDestination,
      sourcePath: plan.sourcePath,
      destinationPath: plan.destinationPath,
      claimedAt,
    }));
  if (claimDecision?.state === "FINALIZED") {
    return {action: plan.action};
  }
  const claimed = claimDecision?.claim;
  const claimedValidation = validateDeletionClaim(
      claimed,
      plan.managerId,
      plan.sourcePath,
  );
  if (claimedValidation.issue) {
    throw new Error(`claim transaction 결과 검증 실패: ${claimedValidation.issue}`);
  }

  const latestSource = await dependencies.getSource();
  const readyDecision = await dependencies.prepareDeletion((currentData) =>
    buildReadyDecision({
      managerId: plan.managerId,
      data: currentData,
      sourceObject: latestSource,
      destinationObject: latestDestination,
      sourcePath: plan.sourcePath,
      destinationPath: plan.destinationPath,
      expectedClaim: claimedValidation.claim,
    }));
  const readyClaim = readyDecision?.claim;
  const readyValidation = validateDeletionClaim(
      readyClaim,
      plan.managerId,
      plan.sourcePath,
  );
  if (readyValidation.issue || readyValidation.claim?.state !== CLAIM_STATE_READY) {
    throw new Error(
        `READY claim 결과 검증 실패: ${readyValidation.issue || "state가 READY가 아닙니다."}`,
    );
  }

  const destinationBeforeDelete = await dependencies.getDestination();
  const destinationRaceIssue = compareStorageObjects(
      latestDestination,
      destinationBeforeDelete,
      plan.destinationPath,
  );
  if (destinationRaceIssue ||
      sanitizeText(destinationBeforeDelete?.generation) !==
        sanitizeText(latestDestination.generation)) {
    throw new Error(
        `원본 삭제 직전 canonical 객체 변경 감지: ${destinationRaceIssue || "generation이 다릅니다."}`,
    );
  }

  if (latestSource && !readyClaim.objectMissing) {
    await dependencies.deleteSource(readyClaim.objectGeneration);
  }
  const remainingSource = await dependencies.getSource();
  if (remainingSource) {
    throw new Error("generation 조건부 삭제 뒤에도 원본 Storage 객체가 남아 있습니다.");
  }

  await dependencies.finalizeDeletion((currentData) =>
    buildFinalizeDecision({
      managerId: plan.managerId,
      data: currentData,
      destinationObject: latestDestination,
      sourcePath: plan.sourcePath,
      destinationPath: plan.destinationPath,
      expectedClaim: readyClaim,
    }));
  return {action: plan.action};
}

function buildClaimDecision({
  managerId,
  data,
  sourceObject,
  destinationObject,
  sourcePath,
  destinationPath,
  claimedAt,
}) {
  const currentPlan = buildMigrationPlan({
    managerId,
    data,
    sourceObject,
    destinationObject,
  });
  if (currentPlan.action === "NOOP") {
    return {state: "FINALIZED", mutation: null};
  }
  if (currentPlan.action === "BLOCKED") {
    throw new Error(`claim transaction 재검증 차단: ${currentPlan.reason}`);
  }
  assertMigrationPaths(currentPlan, sourcePath, destinationPath, "claim transaction");
  if (currentPlan.action === "RESUME_CLAIM") {
    return {
      state: currentPlan.claim.state,
      claim: currentPlan.claim,
      mutation: null,
    };
  }
  if (currentPlan.action !== "UPDATE_METADATA" &&
      currentPlan.action !== "CLEANUP_SOURCE") {
    throw new Error(`claim transaction에서 처리할 수 없는 상태입니다: ${currentPlan.action}`);
  }

  const claim = createDeletionClaim(managerId, sourcePath, claimedAt);
  const mutation = currentPlan.action === "UPDATE_METADATA"
    ? buildFirestoreMutation(data, destinationPath, claim)
    : {data: {[DELETION_CLAIM_FIELD]: claim}};
  return {state: CLAIM_STATE_CLAIMED, claim, mutation};
}

function buildReadyDecision({
  managerId,
  data,
  sourceObject,
  destinationObject,
  sourcePath,
  destinationPath,
  expectedClaim,
}) {
  const currentPlan = buildMigrationPlan({
    managerId,
    data,
    sourceObject,
    destinationObject,
  });
  if (currentPlan.action !== "RESUME_CLAIM") {
    throw new Error(
        `READY transaction 재검증 차단: ${currentPlan.reason || currentPlan.action}`,
    );
  }
  assertMigrationPaths(currentPlan, sourcePath, destinationPath, "READY transaction");
  if (!deletionClaimsMatch(currentPlan.claim, expectedClaim)) {
    throw new Error("READY transaction의 claim이 획득한 claim과 다릅니다.");
  }
  if (currentPlan.claim.state === CLAIM_STATE_READY) {
    return {state: CLAIM_STATE_READY, claim: currentPlan.claim, mutation: null};
  }

  const readyClaim = {
    ...currentPlan.claim,
    state: CLAIM_STATE_READY,
  };
  if (sourceObject) {
    readyClaim.objectGeneration = sanitizeText(sourceObject.generation);
  } else {
    readyClaim.objectMissing = true;
  }
  const readyValidation = validateDeletionClaim(
      readyClaim,
      managerId,
      sourcePath,
  );
  if (readyValidation.issue) {
    throw new Error(`READY claim 생성 차단: ${readyValidation.issue}`);
  }
  return {
    state: CLAIM_STATE_READY,
    claim: readyClaim,
    mutation: {data: {[DELETION_CLAIM_FIELD]: readyClaim}},
  };
}

function buildFinalizeDecision({
  managerId,
  data,
  destinationObject,
  sourcePath,
  destinationPath,
  expectedClaim,
}) {
  const currentPlan = buildMigrationPlan({
    managerId,
    data,
    sourceObject: null,
    destinationObject,
  });
  if (currentPlan.action === "NOOP") {
    return {state: "FINALIZED", mutation: null};
  }
  if (currentPlan.action !== "RESUME_CLAIM") {
    throw new Error(
        `finalize transaction 재검증 차단: ${currentPlan.reason || currentPlan.action}`,
    );
  }
  assertMigrationPaths(currentPlan, sourcePath, destinationPath, "finalize transaction");
  if (currentPlan.claim.state !== CLAIM_STATE_READY ||
      !deletionClaimsMatch(currentPlan.claim, expectedClaim)) {
    throw new Error("finalize transaction의 READY claim이 삭제에 사용한 claim과 다릅니다.");
  }
  return {
    state: "FINALIZED",
    mutation: {deleteFields: [DELETION_CLAIM_FIELD]},
  };
}

function validateSourceDeletionState({
  managerId,
  data,
  destinationObject,
  sourcePath,
  destinationPath,
  expectedClaim,
}) {
  try {
    buildFinalizeDecision({
      managerId,
      data,
      destinationObject,
      sourcePath,
      destinationPath,
      expectedClaim,
    });
    return "";
  } catch (error) {
    return error.message;
  }
}

function buildFirestoreMutation(data, destinationPath, deletionClaim = null) {
  const files = {...data.managerDocumentFiles};
  const paths = {...data.managerDocumentFilePaths};
  const legacyMetadata = {...files[SOURCE_KEY]};
  delete files[SOURCE_KEY];
  delete paths[SOURCE_KEY];
  files[DESTINATION_KEY] = {
    ...legacyMetadata,
    fullPath: destinationPath,
  };
  paths[DESTINATION_KEY] = destinationPath;
  const mutation = {
    data: {
      managerDocumentFiles: files,
      managerDocumentFilePaths: paths,
      managerDocumentEvidenceMigration: {
        migrationId: "health-certificate-to-nursing-license-v1",
        sourceKey: SOURCE_KEY,
        destinationKey: DESTINATION_KEY,
        sourcePath: legacyMetadata.fullPath,
        destinationPath,
      },
    },
    deleteFields: [LEGACY_ALIAS],
  };
  if (deletionClaim) {
    mutation.data[DELETION_CLAIM_FIELD] = deletionClaim;
  }
  return mutation;
}

function createDeletionClaim(managerId, storagePath, claimedAt) {
  return {
    version: DELETION_CLAIM_VERSION,
    claimId: buildDeletionClaimId(managerId, storagePath),
    operation: DELETION_CLAIM_OPERATION,
    documentKey: SOURCE_KEY,
    storagePath,
    state: CLAIM_STATE_CLAIMED,
    claimedAt: resolveClaimedAt(claimedAt),
  };
}

function buildDeletionClaimId(managerId, storagePath) {
  return createHash("sha256")
      .update([
        String(DELETION_CLAIM_VERSION),
        DELETION_CLAIM_OPERATION,
        managerId,
        SOURCE_KEY,
        storagePath,
      ].join("\0"), "utf8")
      .digest("hex");
}

function validateDeletionClaim(value, managerId, expectedStoragePath) {
  if (value === undefined) {
    return {claim: null, issue: ""};
  }
  if (!isPlainObject(value)) {
    return {claim: null, issue: "claim이 map이 아닙니다."};
  }
  if (value.state !== CLAIM_STATE_CLAIMED && value.state !== CLAIM_STATE_READY) {
    return {claim: null, issue: "claim state가 CLAIMED 또는 READY가 아닙니다."};
  }
  const requiredKeys = [
    "version",
    "claimId",
    "operation",
    "documentKey",
    "storagePath",
    "state",
    "claimedAt",
  ];
  if (value.state === CLAIM_STATE_READY) {
    const hasGeneration = hasOwn(value, "objectGeneration");
    const hasMissing = hasOwn(value, "objectMissing");
    if (hasGeneration === hasMissing) {
      return {
        claim: null,
        issue: "READY claim은 objectGeneration 또는 objectMissing 중 하나만 가져야 합니다.",
      };
    }
    requiredKeys.push(hasGeneration ? "objectGeneration" : "objectMissing");
  }
  const actualKeys = Object.keys(value).sort();
  if (actualKeys.length !== requiredKeys.length ||
      requiredKeys.some((key) => !hasOwn(value, key))) {
    return {claim: null, issue: "claim 필드가 불완전하거나 허용되지 않은 필드가 있습니다."};
  }
  if (value.version !== DELETION_CLAIM_VERSION ||
      value.operation !== DELETION_CLAIM_OPERATION ||
      value.documentKey !== SOURCE_KEY) {
    return {claim: null, issue: "claim 버전·작업·문서 키가 현재 이관과 다릅니다."};
  }
  if (!isExpectedPath(value.storagePath, managerId, SOURCE_KEY) ||
      value.storagePath !== expectedStoragePath) {
    return {claim: null, issue: "claim Storage 경로가 현재 매니저 원본과 다릅니다."};
  }
  if (value.claimId !== buildDeletionClaimId(managerId, value.storagePath)) {
    return {claim: null, issue: "claimId가 결정적 식별자와 다릅니다."};
  }
  if (!isExactIsoTimestamp(value.claimedAt)) {
    return {claim: null, issue: "claimedAt이 정규화된 ISO 시각이 아닙니다."};
  }
  if (value.state === CLAIM_STATE_READY) {
    if (hasOwn(value, "objectGeneration") &&
        !/^\d+$/.test(value.objectGeneration)) {
      return {claim: null, issue: "READY claim의 objectGeneration이 올바르지 않습니다."};
    }
    if (hasOwn(value, "objectMissing") && value.objectMissing !== true) {
      return {claim: null, issue: "READY claim의 objectMissing은 true여야 합니다."};
    }
  }
  return {claim: value, issue: ""};
}

function validateCanonicalMigrationState(
    data,
    managerId,
    sourcePath,
    destinationPath,
) {
  const files = isPlainObject(data?.managerDocumentFiles)
    ? data.managerDocumentFiles
    : {};
  const paths = isPlainObject(data?.managerDocumentFilePaths)
    ? data.managerDocumentFilePaths
    : {};
  const metadata = files[DESTINATION_KEY];
  if (data?.role !== "MANAGER") {
    return "현재 사용자 역할이 MANAGER가 아닙니다.";
  }
  if (hasOwn(files, SOURCE_KEY) || hasOwn(paths, SOURCE_KEY) ||
      hasOwn(data || {}, LEGACY_ALIAS)) {
    return "healthCertificate 원본 참조가 남아 있습니다.";
  }
  if (hasOwn(files, "license") || hasOwn(paths, "license") ||
      hasOwn(data || {}, "managerLicenseStoragePath")) {
    return "다른 canonical 자격 증빙이 함께 존재합니다.";
  }
  if (!isPlainObject(metadata) ||
      metadata.fullPath !== destinationPath ||
      paths[DESTINATION_KEY] !== destinationPath ||
      !isExpectedPath(destinationPath, managerId, DESTINATION_KEY)) {
    return "nursingLicense metadata와 path map이 일치하지 않습니다.";
  }
  const marker = data?.managerDocumentEvidenceMigration;
  if (!isPlainObject(marker) ||
      marker.migrationId !== "health-certificate-to-nursing-license-v1" ||
      marker.sourceKey !== SOURCE_KEY ||
      marker.destinationKey !== DESTINATION_KEY ||
      marker.sourcePath !== sourcePath ||
      marker.destinationPath !== destinationPath) {
    return "이관 marker가 원본과 canonical 경로를 증명하지 못합니다.";
  }
  return "";
}

function assertMigrationPaths(plan, sourcePath, destinationPath, stage) {
  if (plan.sourcePath !== sourcePath || plan.destinationPath !== destinationPath) {
    throw new Error(`${stage}에서 처음 검증한 이관 경로가 바뀌었습니다.`);
  }
}

function deletionClaimsMatch(left, right) {
  if (!isPlainObject(left) || !isPlainObject(right)) {
    return false;
  }
  const keys = new Set([...Object.keys(left), ...Object.keys(right)]);
  return Array.from(keys).every((key) => left[key] === right[key]);
}

function resolveClaimedAt(value) {
  const candidate = value || new Date().toISOString();
  if (!isExactIsoTimestamp(candidate)) {
    throw new Error("claimedAt은 정규화된 ISO 시각이어야 합니다.");
  }
  return candidate;
}

function isExactIsoTimestamp(value) {
  if (!isExactText(value)) {
    return false;
  }
  const millis = Date.parse(value);
  return Number.isFinite(millis) && new Date(millis).toISOString() === value;
}

function validateStorageObject(storageObject, expectedPath) {
  if (!storageObject) {
    return "객체가 없습니다.";
  }
  if (sanitizeText(storageObject.name) !== expectedPath) {
    return "객체 이름이 예상 경로와 다릅니다.";
  }
  if (!sanitizeText(storageObject.generation)) {
    return "generation이 없습니다.";
  }
  if (!ALLOWED_CONTENT_TYPES.has(sanitizeText(storageObject.contentType))) {
    return "허용되지 않은 content type입니다.";
  }
  const size = Number(storageObject.size);
  if (!Number.isSafeInteger(size) || size <= 0 || size > MAX_DOCUMENT_SIZE) {
    return "크기가 1 byte 이상 10 MiB 이하여야 합니다.";
  }
  if (!sanitizeText(storageObject.md5Hash) && !sanitizeText(storageObject.crc32c)) {
    return "무결성 checksum이 없습니다.";
  }
  return "";
}

function compareStorageObjects(sourceObject, destinationObject, destinationPath) {
  const destinationIssue = validateStorageObject(destinationObject, destinationPath);
  if (destinationIssue) {
    return destinationIssue;
  }
  if (sanitizeText(sourceObject.contentType) !==
      sanitizeText(destinationObject.contentType)) {
    return "content type이 원본과 다릅니다.";
  }
  if (Number(sourceObject.size) !== Number(destinationObject.size)) {
    return "크기가 원본과 다릅니다.";
  }
  const sourceMd5 = sanitizeText(sourceObject.md5Hash);
  const destinationMd5 = sanitizeText(destinationObject.md5Hash);
  const sourceCrc = sanitizeText(sourceObject.crc32c);
  const destinationCrc = sanitizeText(destinationObject.crc32c);
  if (sourceMd5 && destinationMd5 && sourceMd5 !== destinationMd5) {
    return "MD5 checksum이 원본과 다릅니다.";
  }
  if (sourceCrc && destinationCrc && sourceCrc !== destinationCrc) {
    return "CRC32C checksum이 원본과 다릅니다.";
  }
  if (!(sourceMd5 && destinationMd5) && !(sourceCrc && destinationCrc)) {
    return "원본과 대상을 비교할 공통 checksum이 없습니다.";
  }
  return "";
}

function validateManagerDocumentLegalHold(data, asOf = new Date()) {
  const fields = [
    "managerDocumentLegalHoldUntil",
    "managerDocumentLegalHoldReason",
    "managerDocumentLegalHoldByAdminUserId",
  ];
  const present = fields.filter((field) => hasOwn(data, field));
  if (present.length === 0) {
    return "";
  }
  if (present.length !== fields.length) {
    return "보존 필드가 불완전합니다.";
  }
  if (!isExactText(data.managerDocumentLegalHoldReason) ||
      !isExactText(data.managerDocumentLegalHoldByAdminUserId)) {
    return "보존 사유 또는 설정 관리자 ID가 비어 있거나 앞뒤 공백을 포함합니다.";
  }
  if (!isFirestoreTimestampLike(data.managerDocumentLegalHoldUntil)) {
    return "보존 만료 시각 형식이 Firestore timestamp가 아닙니다.";
  }
  const holdUntilMillis = timestampMillis(data.managerDocumentLegalHoldUntil);
  const asOfMillis = timestampMillis(asOf);
  if (holdUntilMillis === null || asOfMillis === null) {
    return "보존 만료 시각을 해석할 수 없습니다.";
  }
  if (holdUntilMillis > asOfMillis) {
    return "활성 legal hold가 있습니다.";
  }
  return "";
}

function isFirestoreTimestampLike(value) {
  return value instanceof Date ||
    (typeof value === "string" && /^\d{4}-\d{2}-\d{2}T/.test(value)) ||
    Boolean(value && typeof value.toMillis === "function");
}

function timestampMillis(value) {
  if (value instanceof Date) {
    const millis = value.getTime();
    return Number.isFinite(millis) ? millis : null;
  }
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : null;
  }
  if (typeof value === "string") {
    const millis = Date.parse(value);
    return Number.isFinite(millis) ? millis : null;
  }
  if (value && typeof value.toMillis === "function") {
    try {
      const millis = value.toMillis();
      return typeof millis === "number" && Number.isFinite(millis) ? millis : null;
    } catch (_error) {
      return null;
    }
  }
  return null;
}

function isExpectedPath(value, managerId, documentKey) {
  if (!isExactText(value) || !isExactText(managerId)) {
    return false;
  }
  const segments = value.split("/");
  return segments.length === 4 &&
    segments[0] === "manager-documents" &&
    segments[1] === managerId &&
    segments[2] === documentKey &&
    Boolean(segments[3]);
}

function blockedPlan(managerId, reason) {
  return {action: "BLOCKED", managerId, reason};
}

function shouldBlockApply(plans, applyRequested) {
  return Boolean(applyRequested) &&
    plans.some((plan) => plan.action === "BLOCKED");
}

function summarizePlan(manager, plan, applied) {
  return {
    managerId: manager.id,
    action: plan.action,
    reason: plan.reason,
    sourcePath: plan.sourcePath || "",
    destinationPath: plan.destinationPath || "",
    applied,
  };
}

function printSummary(summary) {
  console.log("매니저 healthCertificate 이관 점검");
  console.log(`- 프로젝트: ${summary.projectId}`);
  console.log(`- 버킷: ${summary.storageBucket}`);
  console.log(`- 모드: ${summary.mode}`);
  if (summary.applyBlocked) {
    console.log("- 차단된 매니저가 있어 이번 apply에서는 어떤 변경도 수행하지 않았습니다.");
  }
  console.log(`- 매니저 수: ${summary.managerCount}`);
  console.log(`- 차단 수: ${summary.blockedCount}`);
  for (const result of summary.results) {
    console.log(`- ${result.managerId}: ${result.action} | ${result.reason}`);
  }
}

function fromFirestoreDocument(document) {
  return fromFirestoreMap(document?.fields || {});
}

function fromFirestoreMap(fields) {
  const result = {};
  for (const [key, value] of Object.entries(fields || {})) {
    result[key] = fromFirestoreValue(value);
  }
  return result;
}

function fromFirestoreValue(value) {
  if (!value || typeof value !== "object") return null;
  if ("stringValue" in value) return value.stringValue;
  if ("integerValue" in value) return Number(value.integerValue);
  if ("doubleValue" in value) return Number(value.doubleValue);
  if ("booleanValue" in value) return Boolean(value.booleanValue);
  if ("timestampValue" in value) return value.timestampValue;
  if ("nullValue" in value) return null;
  if ("mapValue" in value) return fromFirestoreMap(value.mapValue?.fields || {});
  if ("arrayValue" in value) {
    return (value.arrayValue?.values || []).map(fromFirestoreValue);
  }
  return null;
}

function isPlainObject(value) {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function hasOwn(value, key) {
  return Object.prototype.hasOwnProperty.call(value || {}, key);
}

function isExactText(value) {
  return typeof value === "string" && value.length > 0 && value.trim() === value;
}

function sanitizeText(value) {
  return value === null || value === undefined ? "" : String(value).trim();
}

if (require.main === module) {
  main().catch((error) => {
    console.error("매니저 healthCertificate 이관 중 오류가 발생했습니다.");
    console.error(error);
    process.exitCode = 1;
  });
}

module.exports = {
  applyMigrationPlan,
  buildClaimDecision,
  buildDeletionClaimId,
  buildFinalizeDecision,
  buildFirestoreMutation,
  buildMigrationPlan,
  buildReadyDecision,
  compareStorageObjects,
  createDeletionClaim,
  parseOptions,
  resolveMigrationObjectPaths,
  shouldBlockApply,
  validateDeletionClaim,
  validateManagerDocumentLegalHold,
  validateSourceDeletionState,
  validateStorageObject,
};
