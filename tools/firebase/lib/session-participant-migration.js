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
  if (!value || typeof value !== "object") {
    return null;
  }
  if ("stringValue" in value) {
    return value.stringValue;
  }
  if ("integerValue" in value) {
    return Number(value.integerValue);
  }
  if ("doubleValue" in value) {
    return Number(value.doubleValue);
  }
  if ("booleanValue" in value) {
    return Boolean(value.booleanValue);
  }
  if ("nullValue" in value) {
    return null;
  }
  if ("mapValue" in value) {
    return fromFirestoreMap(value.mapValue?.fields || {});
  }
  if ("arrayValue" in value) {
    return (value.arrayValue?.values || []).map((entry) => fromFirestoreValue(entry));
  }
  if ("timestampValue" in value) {
    return value.timestampValue;
  }
  return null;
}

function buildSessionParticipantPatch(sessionData, appointmentData) {
  const patientUserId = requiredText(appointmentData?.patientUserId, "patientUserId");
  const guardianUserId = requiredText(appointmentData?.guardianUserId, "guardianUserId");
  const patch = {};

  if (normalizeText(sessionData?.patientUserId) !== patientUserId) {
    patch.patientUserId = patientUserId;
  }
  if (normalizeText(sessionData?.guardianUserId) !== guardianUserId) {
    patch.guardianUserId = guardianUserId;
  }

  return patch;
}

function requiredText(value, fieldName) {
  const normalized = normalizeText(value);
  if (!normalized) {
    throw new Error(`예약 문서의 ${fieldName} 값이 비어 있습니다.`);
  }
  return normalized;
}

function normalizeText(value) {
  return value === null || value === undefined ? "" : String(value).trim();
}

module.exports = {
  buildSessionParticipantPatch,
  fromFirestoreDocument,
};
