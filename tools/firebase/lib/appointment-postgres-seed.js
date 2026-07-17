const crypto = require("crypto");

const UUID_NAMESPACE = "8e884ace-2c0f-4a5b-9ddf-2ff3d8efb9d1";
const TABLE_NAME = "bodeul.appointment_requests";

const ALLOWED_VALUES = Object.freeze({
  requester_role: new Set(["PATIENT", "GUARDIAN"]),
  mobility_support_code: new Set(["INDEPENDENT", "WALKING_AID", "WHEELCHAIR"]),
  trip_type_code: new Set(["ONE_WAY", "ROUND_TRIP"]),
  manager_gender_preference_code: new Set(["ANY", "FEMALE", "MALE"]),
  status: new Set(["REQUESTED", "MATCHED", "IN_PROGRESS", "COMPLETED", "CANCELED"]),
  payment_method_code: new Set(["CARD", "EASY_PAY", "ON_SITE"]),
  coupon_code: new Set(["NONE", "FIRST_VISIT", "FAMILY"]),
  payment_status_code: new Set(["PENDING", "AUTHORIZED", "DEFERRED"]),
});

const ROW_COLUMNS = Object.freeze([
  "id",
  "firestore_id",
  "patient_user_id",
  "guardian_user_id",
  "manager_user_id",
  "requester_user_id",
  "requester_role",
  "patient_name",
  "patient_phone",
  "patient_email",
  "guardian_name",
  "guardian_phone",
  "guardian_email",
  "requester_name",
  "requester_phone",
  "hospital_name",
  "department_name",
  "hospital_latitude",
  "hospital_longitude",
  "appointment_at",
  "appointment_at_epoch_millis",
  "appointment_date_key",
  "meeting_place",
  "special_notes",
  "patient_condition_summary",
  "medication_summary",
  "mobility_support_code",
  "trip_type_code",
  "manager_gender_preference_code",
  "status",
  "base_price",
  "option_surcharge_price",
  "coupon_discount_price",
  "final_price",
  "payment_method_code",
  "coupon_code",
  "payment_status_code",
  "payment_approval_code",
  "payment_approved_at",
  "payment_provider_label",
  "reminder_stages",
  "created_at",
  "updated_at",
]);

function buildAppointmentSeedPlan(snapshot) {
  const errors = [];
  if (!snapshot || typeof snapshot !== "object") {
    return failedPlan("Firestore 백업 JSON 루트가 객체가 아닙니다.");
  }

  const userDocuments = normalizeCollection(snapshot, "users");
  const requestDocuments = normalizeCollection(snapshot, "appointmentRequests");
  const userIds = new Set(userDocuments.map((document) => document.id).filter(Boolean));
  const requestIds = new Set();
  const rows = [];

  for (const document of requestDocuments) {
    const location = document.path || `appointmentRequests/${document.id || "(unknown)"}`;
    if (!document.id) {
      errors.push(diagnostic(location, "firestore_id", "문서 ID가 없습니다."));
      continue;
    }
    if (requestIds.has(document.id)) {
      errors.push(diagnostic(location, "firestore_id", "중복 문서 ID입니다."));
      continue;
    }
    requestIds.add(document.id);
    rows.push(buildAppointmentRow(document, userIds, errors));
  }

  return {
    mode: "appointment-requests-seed",
    status: errors.length === 0 ? "passed" : "needs_review",
    source: {
      projectId: text(snapshot.projectId),
      generatedAt: optionalTimestamp(snapshot.generatedAt, "backup", "generatedAt", errors),
      schemaVersion: snapshot.schemaVersion ?? null,
    },
    rowCount: rows.length,
    errors,
    rows,
  };
}

function buildAppointmentRow(document, userIds, errors) {
  const data = document.data;
  const location = document.path || `appointmentRequests/${document.id}`;
  const patientUserId = optionalCode(data.patientUserId);
  const guardianUserId = optionalCode(data.guardianUserId);
  const managerUserId = optionalCode(data.managerUserId);
  const requesterUserId = requiredText(data.requesterUserId, location, "requesterUserId", errors);
  const appointmentEpoch = requiredInteger(
      data.appointmentAtEpochMillis,
      location,
      "appointmentAtEpochMillis",
      errors,
  );
  const appointmentAt = requiredTimestamp(
      data.appointmentAt || timestampFromMillis(appointmentEpoch),
      location,
      "appointmentAt",
      errors,
  );

  validateUserReference(patientUserId, userIds, location, "patientUserId", errors);
  validateUserReference(guardianUserId, userIds, location, "guardianUserId", errors);
  validateUserReference(managerUserId, userIds, location, "managerUserId", errors);
  validateUserReference(requesterUserId, userIds, location, "requesterUserId", errors);

  if (appointmentAt && appointmentEpoch > 0) {
    const parsedMillis = Date.parse(appointmentAt);
    if (Math.abs(parsedMillis - appointmentEpoch) > 1000) {
      errors.push(diagnostic(
          location,
          "appointmentAtEpochMillis",
          "appointmentAt과 epoch millis가 일치하지 않습니다.",
      ));
    }
  }

  const appointmentDateKey = requiredText(
      data.appointmentDateKey,
      location,
      "appointmentDateKey",
      errors,
  );
  if (appointmentDateKey && !/^\d{4}-\d{2}-\d{2}$/.test(appointmentDateKey)) {
    errors.push(diagnostic(location, "appointmentDateKey", "YYYY-MM-DD 형식이 아닙니다."));
  }

  const hospitalLatitude = optionalNumber(data.hospitalLatitude, location, "hospitalLatitude", errors);
  const hospitalLongitude = optionalNumber(data.hospitalLongitude, location, "hospitalLongitude", errors);
  validateRange(hospitalLatitude, -90, 90, location, "hospitalLatitude", errors);
  validateRange(hospitalLongitude, -180, 180, location, "hospitalLongitude", errors);

  const basePrice = requiredNonnegativeInteger(data.basePrice, location, "basePrice", errors);
  const optionSurchargePrice = requiredNonnegativeInteger(
      data.optionSurchargePrice,
      location,
      "optionSurchargePrice",
      errors,
  );
  const couponDiscountPrice = requiredNonnegativeInteger(
      data.couponDiscountPrice,
      location,
      "couponDiscountPrice",
      errors,
  );
  const finalPrice = requiredNonnegativeInteger(data.finalPrice, location, "finalPrice", errors);
  if (basePrice + optionSurchargePrice - couponDiscountPrice !== finalPrice) {
    errors.push(diagnostic(location, "finalPrice", "현재 앱의 가격 계산식과 일치하지 않습니다."));
  }

  const reminderStages = data.reminderStages;
  if (!Array.isArray(reminderStages)) {
    errors.push(diagnostic(location, "reminderStages", "JSON 배열이 아닙니다."));
  }

  return {
    id: stableUuid("appointment_requests", document.id),
    firestore_id: document.id,
    patient_user_id: nullableUserUuid(patientUserId),
    guardian_user_id: nullableUserUuid(guardianUserId),
    manager_user_id: nullableUserUuid(managerUserId),
    requester_user_id: nullableUserUuid(requesterUserId),
    requester_role: requiredAllowedCode(data.requesterRole, location, "requesterRole", "requester_role", errors),
    patient_name: text(data.patientName),
    patient_phone: text(data.patientPhone),
    patient_email: text(data.patientEmail),
    guardian_name: text(data.guardianName),
    guardian_phone: text(data.guardianPhone),
    guardian_email: text(data.guardianEmail),
    requester_name: text(data.requesterName),
    requester_phone: text(data.requesterPhone),
    hospital_name: requiredText(data.hospitalName, location, "hospitalName", errors),
    department_name: requiredText(data.departmentName, location, "departmentName", errors),
    hospital_latitude: hospitalLatitude,
    hospital_longitude: hospitalLongitude,
    appointment_at: appointmentAt,
    appointment_at_epoch_millis: appointmentEpoch,
    appointment_date_key: appointmentDateKey,
    meeting_place: text(data.meetingPlace),
    special_notes: text(data.specialNotes),
    patient_condition_summary: text(data.patientConditionSummary),
    medication_summary: text(data.medicationSummary),
    mobility_support_code: requiredAllowedCode(
        data.mobilitySupportCode,
        location,
        "mobilitySupportCode",
        "mobility_support_code",
        errors,
    ),
    trip_type_code: requiredAllowedCode(data.tripTypeCode, location, "tripTypeCode", "trip_type_code", errors),
    manager_gender_preference_code: requiredAllowedCode(
        data.managerGenderPreferenceCode,
        location,
        "managerGenderPreferenceCode",
        "manager_gender_preference_code",
        errors,
    ),
    status: requiredAllowedCode(data.status, location, "status", "status", errors),
    base_price: basePrice,
    option_surcharge_price: optionSurchargePrice,
    coupon_discount_price: couponDiscountPrice,
    final_price: finalPrice,
    payment_method_code: requiredAllowedCode(
        data.paymentMethodCode,
        location,
        "paymentMethodCode",
        "payment_method_code",
        errors,
    ),
    coupon_code: requiredAllowedCode(data.couponCode, location, "couponCode", "coupon_code", errors),
    payment_status_code: requiredAllowedCode(
        data.paymentStatusCode,
        location,
        "paymentStatusCode",
        "payment_status_code",
        errors,
    ),
    payment_approval_code: text(data.paymentApprovalCode),
    payment_approved_at: optionalTimestamp(
        data.paymentApprovedAt,
        location,
        "paymentApprovedAt",
        errors,
    ),
    payment_provider_label: text(data.paymentProviderLabel),
    reminder_stages: Array.isArray(reminderStages) ? reminderStages : [],
    created_at: requiredTimestamp(data.createdAt, location, "createdAt", errors),
    updated_at: optionalTimestamp(data.updatedAt, location, "updatedAt", errors),
  };
}

function buildAppointmentSeedSql(plan, {rollback = false} = {}) {
  if (!plan || plan.mode !== "appointment-requests-seed") {
    throw new Error("예약 요청 seed plan 형식이 아닙니다.");
  }
  if (plan.status !== "passed") {
    throw new Error(`검증 오류 ${plan.errors?.length || 0}건이 있어 SQL을 생성하지 않습니다.`);
  }

  const lines = [
    "-- BoDeul appointment_requests PostgreSQL seed",
    "-- Firestore 백업의 개인정보가 포함되므로 생성 파일을 커밋하거나 공유하지 않는다.",
    "begin;",
    "set local role bodeul_migration;",
    "",
  ];

  if (rollback) {
    if (plan.rows.length === 0) {
      lines.push("-- 삭제할 예약 요청이 없습니다.");
    } else {
      const firestoreIds = plan.rows.map((row) => quoteString(row.firestore_id));
      lines.push(`delete from ${TABLE_NAME}`);
      lines.push(`where firestore_id in (${firestoreIds.join(", ")});`);
    }
  } else {
    for (const row of plan.rows) {
      lines.push(buildUpsert(row));
      lines.push("");
    }
  }

  lines.push("commit;");
  lines.push("");
  return lines.join("\n");
}

function buildUpsert(row) {
  const columns = ROW_COLUMNS.map(quoteIdentifier);
  const values = ROW_COLUMNS.map((column) => toSqlLiteral(row[column], column));
  const updateColumns = ROW_COLUMNS.filter((column) => !["id", "firestore_id"].includes(column));
  const updates = updateColumns.map(
      (column) => `${quoteIdentifier(column)} = excluded.${quoteIdentifier(column)}`,
  );
  updates.push("imported_at = now()");

  return [
    `insert into ${TABLE_NAME} (${columns.join(", ")})`,
    `values (${values.join(", ")})`,
    `on conflict (firestore_id) do update set ${updates.join(", ")};`,
  ].join("\n");
}

function normalizeCollection(snapshot, collectionName) {
  const documents = snapshot.collections?.[collectionName];
  if (!Array.isArray(documents)) {
    return [];
  }
  return documents.map((document) => ({
    id: typeof document.id === "string" ? document.id : "",
    path: typeof document.path === "string"
      ? document.path
      : `${collectionName}/${document.id || ""}`,
    data: document.data || fromFirestoreFields(document.fields || {}),
  }));
}

function fromFirestoreFields(fields) {
  const result = {};
  for (const [key, value] of Object.entries(fields || {})) {
    result[key] = fromFirestoreValue(value);
  }
  return result;
}

function fromFirestoreValue(value) {
  if (!value || typeof value !== "object") {
    return value;
  }
  if (Object.prototype.hasOwnProperty.call(value, "stringValue")) {
    return value.stringValue;
  }
  if (Object.prototype.hasOwnProperty.call(value, "integerValue")) {
    return Number(value.integerValue);
  }
  if (Object.prototype.hasOwnProperty.call(value, "doubleValue")) {
    return Number(value.doubleValue);
  }
  if (Object.prototype.hasOwnProperty.call(value, "booleanValue")) {
    return Boolean(value.booleanValue);
  }
  if (Object.prototype.hasOwnProperty.call(value, "nullValue")) {
    return null;
  }
  if (Object.prototype.hasOwnProperty.call(value, "timestampValue")) {
    return value.timestampValue;
  }
  if (Object.prototype.hasOwnProperty.call(value, "mapValue")) {
    return fromFirestoreFields(value.mapValue?.fields || {});
  }
  if (Object.prototype.hasOwnProperty.call(value, "arrayValue")) {
    return (value.arrayValue?.values || []).map((item) => fromFirestoreValue(item));
  }
  return value;
}

function stableUuid(scope, key) {
  const namespaceBytes = Buffer.from(UUID_NAMESPACE.replaceAll("-", ""), "hex");

  // 암호화가 아니라 Firestore 문서 ID를 반복 가능한 UUID로 바꾸기 위한 식별자 해시다.
  const hash = crypto.createHash("sha256")
      .update(namespaceBytes)
      .update(`${scope}:${key}`)
      .digest();
  const bytes = Buffer.from(hash.subarray(0, 16));
  bytes[6] = (bytes[6] & 0x0f) | 0x50;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  return [
    bytes.subarray(0, 4).toString("hex"),
    bytes.subarray(4, 6).toString("hex"),
    bytes.subarray(6, 8).toString("hex"),
    bytes.subarray(8, 10).toString("hex"),
    bytes.subarray(10, 16).toString("hex"),
  ].join("-");
}

function nullableUserUuid(firebaseUid) {
  return firebaseUid ? stableUuid("app_users", firebaseUid) : null;
}

function validateUserReference(firebaseUid, userIds, location, field, errors) {
  if (firebaseUid && !userIds.has(firebaseUid)) {
    errors.push(diagnostic(location, field, "users 백업에서 참조 대상을 찾지 못했습니다."));
  }
}

function requiredAllowedCode(value, location, field, allowedKey, errors) {
  const normalized = requiredText(value, location, field, errors);
  if (normalized && !ALLOWED_VALUES[allowedKey].has(normalized)) {
    errors.push(diagnostic(location, field, "현재 앱 계약에 없는 코드입니다."));
  }
  return normalized;
}

function requiredText(value, location, field, errors) {
  const normalized = optionalCode(value);
  if (!normalized) {
    errors.push(diagnostic(location, field, "필수 값이 비어 있습니다."));
  }
  return normalized;
}

function requiredInteger(value, location, field, errors) {
  const numberValue = Number(value);
  if (!Number.isSafeInteger(numberValue) || numberValue <= 0) {
    errors.push(diagnostic(location, field, "0보다 큰 안전한 정수가 아닙니다."));
    return 0;
  }
  return numberValue;
}

function requiredNonnegativeInteger(value, location, field, errors) {
  const numberValue = Number(value);
  if (!Number.isSafeInteger(numberValue) || numberValue < 0) {
    errors.push(diagnostic(location, field, "0 이상의 안전한 정수가 아닙니다."));
    return 0;
  }
  return numberValue;
}

function optionalNumber(value, location, field, errors) {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  const numberValue = Number(value);
  if (!Number.isFinite(numberValue)) {
    errors.push(diagnostic(location, field, "유효한 숫자가 아닙니다."));
    return null;
  }
  return numberValue;
}

function validateRange(value, minimum, maximum, location, field, errors) {
  if (value !== null && (value < minimum || value > maximum)) {
    errors.push(diagnostic(location, field, `${minimum}~${maximum} 범위를 벗어났습니다.`));
  }
}

function requiredTimestamp(value, location, field, errors) {
  if (value === null || value === undefined || value === "") {
    errors.push(diagnostic(location, field, "필수 시각이 비어 있습니다."));
    return null;
  }
  return optionalTimestamp(value, location, field, errors);
}

function optionalTimestamp(value, location, field, errors) {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  const parsed = parseTimestampMillis(value);
  if (!Number.isFinite(parsed)) {
    errors.push(diagnostic(location, field, "지원하는 시각 형식으로 해석할 수 없습니다."));
    return null;
  }
  return new Date(parsed).toISOString();
}

function parseTimestampMillis(value) {
  if (typeof value === "number") {
    return value;
  }
  if (typeof value === "string" && /^\d{13}$/.test(value)) {
    return Number(value);
  }
  if (value && typeof value === "object" && value.seconds !== undefined) {
    const seconds = Number(value.seconds);
    const nanos = Number(value.nanoseconds ?? value.nanos ?? 0);
    return Number.isFinite(seconds) && Number.isFinite(nanos)
      ? (seconds * 1000) + Math.trunc(nanos / 1000000)
      : Number.NaN;
  }
  return Date.parse(String(value));
}

function timestampFromMillis(value) {
  const numberValue = Number(value);
  return Number.isFinite(numberValue) && numberValue > 0
    ? new Date(numberValue).toISOString()
    : null;
}

function optionalCode(value) {
  return value === null || value === undefined ? "" : String(value).trim();
}

function text(value) {
  return value === null || value === undefined ? "" : String(value);
}

function diagnostic(path, field, message) {
  return {path, field, message};
}

function failedPlan(message) {
  return {
    mode: "appointment-requests-seed",
    status: "needs_review",
    source: {},
    rowCount: 0,
    errors: [diagnostic("backup", "root", message)],
    rows: [],
  };
}

function toSqlLiteral(value, column) {
  if (value === null || value === undefined) {
    return "null";
  }
  if (typeof value === "number") {
    return Number.isFinite(value) ? String(value) : "null";
  }
  if (typeof value === "boolean") {
    return value ? "true" : "false";
  }
  if (column === "reminder_stages") {
    return `${quoteString(JSON.stringify(value))}::jsonb`;
  }
  return quoteString(String(value));
}

function quoteString(value) {
  return `'${String(value).replaceAll("'", "''")}'`;
}

function quoteIdentifier(value) {
  return `"${String(value).replaceAll('"', '""')}"`;
}

module.exports = {
  buildAppointmentSeedPlan,
  buildAppointmentSeedSql,
  fromFirestoreFields,
  stableUuid,
};
