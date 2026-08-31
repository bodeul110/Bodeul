const {createHash} = require("node:crypto");
const {FieldValue} = require("firebase-admin/firestore");

const MANAGER_DOCUMENT_DELETION_CLAIM_FIELD = "managerDocumentDeletionClaim";
const MANAGER_DOCUMENT_DELETION_CLAIM_VERSION = 1;
const MANAGER_DOCUMENT_DELETION_OPERATIONS = Object.freeze([
  "REPLACEMENT",
  "RETENTION",
  "MIGRATION",
]);
const MANAGER_DOCUMENT_DELETION_STATES = Object.freeze([
  "CLAIMED",
  "READY",
]);

async function executeManagerDocumentDeletion({
  firestore,
  documentReference,
  candidate,
  operation,
  claimedAt = new Date(),
  storage,
  validateCurrentState,
  buildFinalizeUpdates = () => ({}),
}) {
  const database = firestore || documentReference?.firestore;
  if (!database || typeof database.runTransaction !== "function") {
    throw createManagerDocumentDeletionError("MANAGER_DOCUMENT_FIRESTORE_MISSING");
  }
  if (!documentReference || typeof validateCurrentState !== "function" ||
      !storage || typeof storage.inspectManagerDocument !== "function" ||
      typeof storage.deleteManagerDocument !== "function") {
    throw createManagerDocumentDeletionError("MANAGER_DOCUMENT_DELETION_CONFIG_INVALID");
  }

  const requestedClaim = buildManagerDocumentDeletionClaim({
    candidate,
    operation,
    claimedAt,
  });
  const acquired = await acquireManagerDocumentDeletionClaim({
    firestore: database,
    documentReference,
    requestedClaim,
    validateCurrentState,
  });
  if (acquired.status !== "ACQUIRED") {
    return acquired;
  }

  let readyClaim = acquired.claim;
  if (readyClaim.state === "CLAIMED") {
    const object = await storage.inspectManagerDocument(
        candidate.storagePath,
        candidate.managerId,
        candidate.documentKey,
    );
    readyClaim = await bindManagerDocumentDeletionObject({
      firestore: database,
      documentReference,
      claim: readyClaim,
      object,
      validateCurrentState,
    });
  }

  if (!readyClaim.objectMissing) {
    await storage.deleteManagerDocument(
        candidate.storagePath,
        candidate.managerId,
        candidate.documentKey,
        readyClaim.objectGeneration,
    );
  }

  await finalizeManagerDocumentDeletion({
    firestore: database,
    documentReference,
    claim: readyClaim,
    validateCurrentState,
    buildFinalizeUpdates,
  });
  return {
    status: "COMPLETED",
    claim: readyClaim,
    objectMissing: readyClaim.objectMissing === true,
  };
}

async function acquireManagerDocumentDeletionClaim({
  firestore,
  documentReference,
  requestedClaim,
  validateCurrentState,
}) {
  return firestore.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(documentReference);
    if (!snapshot.exists) {
      return {status: "SKIPPED", reason: "DOCUMENT_MISSING"};
    }
    const data = snapshot.data();
    const validationIssue = normalizeValidationIssue(
        validateCurrentState(data, snapshot),
    );
    const hasCurrentClaim = hasOwn(data, MANAGER_DOCUMENT_DELETION_CLAIM_FIELD);
    if (hasCurrentClaim) {
      const currentClaim = parseManagerDocumentDeletionClaim(
          data[MANAGER_DOCUMENT_DELETION_CLAIM_FIELD],
      );
      if (!currentClaim || !sameManagerDocumentDeletionIdentity(
          currentClaim,
          requestedClaim,
      )) {
        return {status: "CLAIM_CONFLICT", reason: "CURRENT_CLAIM_CONFLICT"};
      }
      if (validationIssue) {
        if (currentClaim.state === "CLAIMED") {
          transaction.update(documentReference, {
            [MANAGER_DOCUMENT_DELETION_CLAIM_FIELD]: FieldValue.delete(),
          });
          return {
            status: "CLAIM_RELEASED",
            reason: validationIssue,
            claim: currentClaim,
          };
        }
        return {status: "CLAIM_BLOCKED", reason: validationIssue, claim: currentClaim};
      }
      return {status: "ACQUIRED", claim: currentClaim, resumed: true};
    }
    if (validationIssue) {
      return {status: "SKIPPED", reason: validationIssue};
    }

    transaction.update(documentReference, {
      [MANAGER_DOCUMENT_DELETION_CLAIM_FIELD]: requestedClaim,
    });
    return {status: "ACQUIRED", claim: requestedClaim, resumed: false};
  });
}

async function bindManagerDocumentDeletionObject({
  firestore,
  documentReference,
  claim,
  object,
  validateCurrentState,
}) {
  const readyClaim = buildReadyManagerDocumentDeletionClaim(claim, object);
  return firestore.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(documentReference);
    if (!snapshot.exists) {
      throw createManagerDocumentDeletionError("MANAGER_DOCUMENT_CLAIM_DOCUMENT_MISSING");
    }
    const data = snapshot.data();
    const currentClaim = parseManagerDocumentDeletionClaim(
        data?.[MANAGER_DOCUMENT_DELETION_CLAIM_FIELD],
    );
    if (!currentClaim || !sameManagerDocumentDeletionClaim(currentClaim, claim)) {
      throw createManagerDocumentDeletionError("MANAGER_DOCUMENT_CLAIM_CHANGED");
    }
    const validationIssue = normalizeValidationIssue(
        validateCurrentState(data, snapshot),
    );
    if (validationIssue) {
      throw createManagerDocumentDeletionError(
          "MANAGER_DOCUMENT_CLAIM_STATE_CHANGED",
          validationIssue,
      );
    }

    transaction.update(documentReference, {
      [MANAGER_DOCUMENT_DELETION_CLAIM_FIELD]: readyClaim,
    });
    return readyClaim;
  });
}

async function finalizeManagerDocumentDeletion({
  firestore,
  documentReference,
  claim,
  validateCurrentState,
  buildFinalizeUpdates,
}) {
  return firestore.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(documentReference);
    if (!snapshot.exists) {
      throw createManagerDocumentDeletionError("MANAGER_DOCUMENT_FINALIZE_DOCUMENT_MISSING");
    }
    const data = snapshot.data();
    const currentClaim = parseManagerDocumentDeletionClaim(
        data?.[MANAGER_DOCUMENT_DELETION_CLAIM_FIELD],
    );
    if (!currentClaim || !sameManagerDocumentDeletionClaim(currentClaim, claim) ||
        currentClaim.state !== "READY") {
      throw createManagerDocumentDeletionError("MANAGER_DOCUMENT_FINALIZE_CLAIM_CHANGED");
    }
    const validationIssue = normalizeValidationIssue(
        validateCurrentState(data, snapshot),
    );
    if (validationIssue) {
      throw createManagerDocumentDeletionError(
          "MANAGER_DOCUMENT_FINALIZE_STATE_CHANGED",
          validationIssue,
      );
    }

    const updates = buildFinalizeUpdates(data, snapshot);
    if (!isPlainObject(updates)) {
      throw createManagerDocumentDeletionError("MANAGER_DOCUMENT_FINALIZE_UPDATES_INVALID");
    }
    transaction.update(documentReference, {
      ...updates,
      [MANAGER_DOCUMENT_DELETION_CLAIM_FIELD]: FieldValue.delete(),
    });
    return true;
  });
}

function buildManagerDocumentDeletionClaim({candidate, operation, claimedAt}) {
  const claimedAtDate = claimedAt instanceof Date ? claimedAt : new Date(claimedAt);
  if (!candidate || !isExactText(candidate.managerId) ||
      !isExactText(candidate.documentKey) || !isExactText(candidate.storagePath) ||
      !MANAGER_DOCUMENT_DELETION_OPERATIONS.includes(operation) ||
      !Number.isFinite(claimedAtDate.getTime())) {
    throw createManagerDocumentDeletionError("MANAGER_DOCUMENT_CLAIM_INPUT_INVALID");
  }
  return {
    version: MANAGER_DOCUMENT_DELETION_CLAIM_VERSION,
    claimId: managerDocumentDeletionClaimId(candidate, operation),
    operation,
    documentKey: candidate.documentKey,
    storagePath: candidate.storagePath,
    state: "CLAIMED",
    claimedAt: claimedAtDate.toISOString(),
  };
}

function buildReadyManagerDocumentDeletionClaim(claim, object) {
  const parsed = parseManagerDocumentDeletionClaim(claim);
  if (!parsed || parsed.state !== "CLAIMED") {
    throw createManagerDocumentDeletionError("MANAGER_DOCUMENT_CLAIM_NOT_CLAIMED");
  }
  if (object === null || object?.objectMissing === true) {
    return {...parsed, state: "READY", objectMissing: true};
  }
  const generation = sanitizeText(object?.objectGeneration || object?.generation);
  if (!/^\d+$/.test(generation)) {
    throw createManagerDocumentDeletionError("MANAGER_DOCUMENT_GENERATION_INVALID");
  }
  return {...parsed, state: "READY", objectGeneration: generation};
}

function parseManagerDocumentDeletionClaim(value) {
  if (!isPlainObject(value) || value.version !== MANAGER_DOCUMENT_DELETION_CLAIM_VERSION ||
      !/^[a-f0-9]{64}$/.test(value.claimId) ||
      !MANAGER_DOCUMENT_DELETION_OPERATIONS.includes(value.operation) ||
      !isExactText(value.documentKey) || !isExactText(value.storagePath) ||
      !MANAGER_DOCUMENT_DELETION_STATES.includes(value.state) ||
      !isCanonicalIsoTimestamp(value.claimedAt)) {
    return null;
  }
  const baseKeys = [
    "version",
    "claimId",
    "operation",
    "documentKey",
    "storagePath",
    "state",
    "claimedAt",
  ];
  if (value.state === "CLAIMED") {
    return hasOnlyKeys(value, baseKeys) ? {...value} : null;
  }
  const hasGeneration = hasOwn(value, "objectGeneration");
  const hasMissing = hasOwn(value, "objectMissing");
  if (hasGeneration === hasMissing) {
    return null;
  }
  if (hasGeneration && (typeof value.objectGeneration !== "string" ||
      !/^\d+$/.test(value.objectGeneration))) {
    return null;
  }
  if (hasMissing && value.objectMissing !== true) {
    return null;
  }
  return hasOnlyKeys(
      value,
      [...baseKeys, hasGeneration ? "objectGeneration" : "objectMissing"],
  ) ? {...value} : null;
}

function sameManagerDocumentDeletionIdentity(left, right) {
  return Boolean(left && right) && left.version === right.version &&
    left.claimId === right.claimId && left.operation === right.operation &&
    left.documentKey === right.documentKey && left.storagePath === right.storagePath;
}

function sameManagerDocumentDeletionClaim(left, right) {
  const parsedLeft = parseManagerDocumentDeletionClaim(left);
  const parsedRight = parseManagerDocumentDeletionClaim(right);
  if (!parsedLeft || !parsedRight ||
      !sameManagerDocumentDeletionIdentity(parsedLeft, parsedRight) ||
      parsedLeft.state !== parsedRight.state ||
      parsedLeft.claimedAt !== parsedRight.claimedAt) {
    return false;
  }
  if (parsedLeft.state === "CLAIMED") {
    return true;
  }
  return parsedLeft.objectGeneration === parsedRight.objectGeneration &&
    parsedLeft.objectMissing === parsedRight.objectMissing;
}

function managerDocumentDeletionClaimId(candidate, operation) {
  return createHash("sha256")
      .update([
        String(MANAGER_DOCUMENT_DELETION_CLAIM_VERSION),
        operation,
        candidate.managerId,
        candidate.documentKey,
        candidate.storagePath,
      ].join("\0"), "utf8")
      .digest("hex");
}

function normalizeValidationIssue(value) {
  if (value === true || value === "") {
    return "";
  }
  if (value === false || value === null || value === undefined) {
    return "STATE_INELIGIBLE";
  }
  return sanitizeText(value) || "STATE_INELIGIBLE";
}

function isCanonicalIsoTimestamp(value) {
  if (!isExactText(value)) {
    return false;
  }
  const date = new Date(value);
  return Number.isFinite(date.getTime()) && date.toISOString() === value;
}

function hasOnlyKeys(value, allowedKeys) {
  const keys = Object.keys(value);
  return keys.length === allowedKeys.length && keys.every((key) => allowedKeys.includes(key));
}

function hasOwn(value, key) {
  return isPlainObject(value) && Object.prototype.hasOwnProperty.call(value, key);
}

function isExactText(value) {
  return typeof value === "string" && value.length > 0 && value.trim() === value;
}

function sanitizeText(value) {
  return typeof value === "string" ? value.trim() : "";
}

function isPlainObject(value) {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function createManagerDocumentDeletionError(code, detail = "") {
  const error = new Error(detail ? `${code}: ${detail}` : code);
  error.code = code;
  error.detail = detail;
  return error;
}

module.exports = {
  MANAGER_DOCUMENT_DELETION_CLAIM_FIELD,
  MANAGER_DOCUMENT_DELETION_CLAIM_VERSION,
  MANAGER_DOCUMENT_DELETION_OPERATIONS,
  acquireManagerDocumentDeletionClaim,
  bindManagerDocumentDeletionObject,
  buildManagerDocumentDeletionClaim,
  buildReadyManagerDocumentDeletionClaim,
  executeManagerDocumentDeletion,
  finalizeManagerDocumentDeletion,
  managerDocumentDeletionClaimId,
  parseManagerDocumentDeletionClaim,
  sameManagerDocumentDeletionClaim,
  sameManagerDocumentDeletionIdentity,
};
