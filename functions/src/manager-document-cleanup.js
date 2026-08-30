const {getApp} = require("firebase-admin/app");
const {getStorage} = require("firebase-admin/storage");
const logger = require("firebase-functions/logger");
const {onDocumentWritten} = require("firebase-functions/v2/firestore");

const {
  executeManagerDocumentDeletion,
} = require("./manager-document-deletion");

const {
  REPLACEMENT_CLEANUP_MANAGER_DOCUMENT_KEYS,
  RETENTION_MANAGER_DOCUMENT_KEYS,
  collectManagerDocumentReferencePaths,
  isManagerDocumentStoragePath,
  managerDocumentLegalHoldBlocksDeletion,
  resolveCanonicalManagerDocumentReference,
  resolveManagerDocumentReference,
} = require("./manager-document-contract");

const MANAGER_DOCUMENT_REPLACEMENT_CLEANUP_OPTIONS = {
  region: "asia-northeast3",
  document: "users/{userId}",
  retry: true,
};

class ManagerDocumentReplacementStorageGateway {
  constructor(bucket) {
    this.bucket = bucket;
  }

  async inspectManagerDocument(storagePath, managerId, documentKey) {
    this.assertManagerDocumentPath(storagePath, managerId, documentKey);
    try {
      const [metadata] = await this.bucket.file(storagePath).getMetadata();
      const objectGeneration = sanitizeText(String(metadata?.generation || ""));
      if (!/^\d+$/.test(objectGeneration)) {
        throw createManagerDocumentCleanupError("MANAGER_REPLACEMENT_GENERATION_INVALID");
      }
      return {objectGeneration};
    } catch (error) {
      if (isStorageObjectNotFound(error)) {
        return {objectMissing: true};
      }
      throw error;
    }
  }

  async deleteManagerDocument(storagePath, managerId, documentKey, objectGeneration) {
    this.assertManagerDocumentPath(storagePath, managerId, documentKey);
    const generation = sanitizeText(objectGeneration);
    if (!/^\d+$/.test(generation)) {
      throw createManagerDocumentCleanupError("MANAGER_REPLACEMENT_GENERATION_INVALID");
    }
    try {
      await this.bucket.file(storagePath).delete({
        ignoreNotFound: true,
        ifGenerationMatch: generation,
      });
    } catch (error) {
      if (!isStorageObjectNotFound(error)) {
        throw error;
      }
    }
  }

  assertManagerDocumentPath(storagePath, managerId, documentKey) {
    if (!isManagerDocumentStoragePath(
        storagePath,
        managerId,
        documentKey,
        REPLACEMENT_CLEANUP_MANAGER_DOCUMENT_KEYS,
    )) {
      throw createManagerDocumentCleanupError("MANAGER_REPLACEMENT_PATH_INVALID");
    }
  }
}

const cleanupReplacedManagerDocumentObjects = onDocumentWritten(
    MANAGER_DOCUMENT_REPLACEMENT_CLEANUP_OPTIONS,
    async (event) => {
      if (!event.data?.before?.exists || !event.data?.after?.exists) {
        return;
      }
      const managerId = event.params?.userId;
      const asOf = new Date();
      const candidates = collectReplacedManagerDocumentCandidates(
          managerId,
          event.data.before.data(),
          event.data.after.data(),
          asOf,
      );
      if (candidates.length === 0) {
        return;
      }

      const documentReference = event.data?.after?.ref || event.data.before.ref;
      const bucket = getStorage(getApp()).bucket(resolveStorageBucketName());
      const result = await deleteUnreferencedManagerDocumentCandidates({
        firestore: documentReference.firestore,
        documentReference,
        candidates,
        storage: new ManagerDocumentReplacementStorageGateway(bucket),
        asOf,
      });
      logger.info("교체된 매니저 증빙 원본 정리를 마쳤습니다.", result);
    },
);

function collectReplacedManagerDocumentCandidates(
    managerId,
    beforeData,
    afterData,
    asOf = new Date(),
) {
  if (sanitizeText(beforeData?.role) !== "MANAGER" ||
      sanitizeText(afterData?.role) !== "MANAGER" ||
      managerDocumentLegalHoldBlocksDeletion(beforeData, asOf)) {
    return [];
  }
  if (!resolveCanonicalManagerDocumentReference(afterData, managerId)) {
    return [];
  }
  const afterReferencePaths = collectManagerDocumentReferencePaths(
      afterData,
      RETENTION_MANAGER_DOCUMENT_KEYS,
  );
  const candidates = [];
  for (const documentKey of REPLACEMENT_CLEANUP_MANAGER_DOCUMENT_KEYS) {
    const previous = resolveManagerDocumentReference(beforeData, managerId, documentKey);
    if (!previous || afterReferencePaths.has(previous.storagePath)) {
      continue;
    }
    candidates.push({
      managerId,
      documentKey,
      storagePath: previous.storagePath,
    });
  }
  return candidates;
}

async function deleteUnreferencedManagerDocumentCandidates({
  firestore,
  documentReference,
  candidates,
  storage,
  asOf = new Date(),
}) {
  const result = {
    deleted: 0,
    skippedReferenced: 0,
    skippedInvalid: 0,
    skippedDocumentState: 0,
    skippedLegalHold: 0,
    skippedClaimConflict: 0,
  };
  for (const candidate of candidates) {
    if (!isReplacementCleanupCandidate(candidate)) {
      result.skippedInvalid += 1;
      continue;
    }

    const deletion = await executeManagerDocumentDeletion({
      firestore,
      documentReference,
      candidate,
      operation: "REPLACEMENT",
      claimedAt: asOf,
      storage,
      validateCurrentState: (data) => replacementDeletionValidationIssue(
          data,
          candidate,
          asOf,
      ),
    });
    if (deletion.status === "COMPLETED") {
      result.deleted += 1;
    } else if (["CLAIM_CONFLICT", "CLAIM_BLOCKED"].includes(deletion.status)) {
      result.skippedClaimConflict += 1;
    } else if (deletion.reason === "LEGAL_HOLD") {
      result.skippedLegalHold += 1;
    } else if (deletion.reason === "REFERENCED") {
      result.skippedReferenced += 1;
    } else {
      result.skippedDocumentState += 1;
    }
  }
  return result;
}

function replacementDeletionValidationIssue(data, candidate, asOf) {
  if (sanitizeText(data?.role) !== "MANAGER" ||
      !resolveCanonicalManagerDocumentReference(data, candidate.managerId)) {
    return "DOCUMENT_STATE";
  }
  if (managerDocumentLegalHoldBlocksDeletion(data, asOf)) {
    return "LEGAL_HOLD";
  }
  if (managerDocumentDataReferencesStoragePath(data, candidate.storagePath)) {
    return "REFERENCED";
  }
  return "";
}

function managerDocumentDataReferencesStoragePath(data, storagePath) {
  return collectManagerDocumentReferencePaths(
      data,
      RETENTION_MANAGER_DOCUMENT_KEYS,
  ).has(storagePath);
}

function isReplacementCleanupCandidate(candidate) {
  return Boolean(candidate) && isManagerDocumentStoragePath(
      candidate.storagePath,
      candidate.managerId,
      candidate.documentKey,
      REPLACEMENT_CLEANUP_MANAGER_DOCUMENT_KEYS,
  );
}

function isStorageObjectNotFound(error) {
  const code = error?.code;
  return code === 404 || code === "404" || code === "storage/object-not-found" ||
    (Array.isArray(error?.errors) && error.errors.some((item) => item?.reason === "notFound"));
}

function resolveStorageBucketName() {
  const projectId = sanitizeText(process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT);
  if (!projectId) {
    throw createManagerDocumentCleanupError("FIREBASE_PROJECT_MISSING");
  }
  return `${projectId}.firebasestorage.app`;
}

function createManagerDocumentCleanupError(code) {
  return Object.assign(new Error(code), {code});
}

function sanitizeText(value) {
  return typeof value === "string" ? value.trim() : "";
}

module.exports = {
  cleanupReplacedManagerDocumentObjects,
  ManagerDocumentReplacementStorageGateway,
  collectReplacedManagerDocumentCandidates,
  deleteUnreferencedManagerDocumentCandidates,
  isReplacementCleanupCandidate,
  isStorageObjectNotFound,
  managerDocumentDataReferencesStoragePath,
  replacementDeletionValidationIssue,
};
