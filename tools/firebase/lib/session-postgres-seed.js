const {
  fromFirestoreFields,
  stableUuid,
} = require("./appointment-postgres-seed");

const TABLES = Object.freeze({
  sessions: "bodeul.companion_sessions",
  reports: "bodeul.session_reports",
  followUps: "bodeul.appointment_follow_ups",
});

const SESSION_STATUSES = new Set([
  "READY",
  "MEETING",
  "WAITING",
  "IN_TREATMENT",
  "PAYMENT",
  "CANCELED",
  "COMPLETED",
]);
const MEDICATION_DECISIONS = new Set(["", "MATCHED", "CHANGED", "RECHECK_REQUIRED"]);
const REVIEW_RATINGS = new Set(["", "excellent", "good", "ok", "disappointing", "need_help"]);
const SETTLEMENT_STATUSES = new Set(["", "CONFIRMED", "NEEDS_HELP", "OVERTIME_REVIEW", "REFUND_REVIEW"]);
const SUPPORT_STATUSES = new Set(["", "GUIDE_VIEWED", "MANAGER_CALLED", "DIALED_119"]);

const SESSION_COLUMNS = Object.freeze([
  "id",
  "firestore_id",
  "appointment_request_id",
  "manager_user_id",
  "current_step_order",
  "current_status",
  "guardian_update",
  "location_summary",
  "field_photo_note",
  "medication_note",
  "pharmacy_summary",
  "prescription_collected",
  "pharmacy_completed",
  "medication_guidance_completed",
  "created_at",
  "updated_at",
]);

const REPORT_COLUMNS = Object.freeze([
  "id",
  "firestore_id",
  "companion_session_id",
  "summary",
  "treatment_notes",
  "medication_notes",
  "medication_name",
  "medication_change_summary",
  "medication_schedule_note",
  "medication_comparison_decision_code",
  "medication_comparison_note",
  "next_visit_at",
  "next_visit_note",
  "created_at",
  "updated_at",
]);

const FOLLOW_UP_COLUMNS = Object.freeze([
  "appointment_request_id",
  "review_rating_code",
  "review_comment",
  "review_saved_by_user_id",
  "review_saved_at",
  "settlement_follow_up_status",
  "settlement_follow_up_note",
  "settlement_follow_up_saved_by_user_id",
  "settlement_follow_up_saved_at",
  "support_escalation_status",
  "support_escalated_by_user_id",
  "support_escalated_at",
  "updated_at",
]);

function buildSessionSeedPlan(snapshot) {
  const errors = [];
  if (!snapshot || typeof snapshot !== "object") {
    return failedPlan("Firestore 백업 JSON 루트가 객체가 아닙니다.");
  }

  const users = normalizeCollection(snapshot, "users");
  const appointments = normalizeCollection(snapshot, "appointmentRequests");
  const sessions = normalizeCollection(snapshot, "companionSessions");
  const reports = normalizeCollection(snapshot, "sessionReports");
  const followUps = normalizeCollection(snapshot, "appointmentFollowUps");
  const userIds = new Set(users.map((document) => document.id).filter(Boolean));
  const appointmentIds = new Set(appointments.map((document) => document.id).filter(Boolean));
  const sessionIds = new Set(sessions.map((document) => document.id).filter(Boolean));

  const sessionRows = sessions.map((document) => buildSessionRow(
      document,
      userIds,
      appointmentIds,
      errors,
  ));
  const reportRows = reports.map((document) => buildReportRow(document, sessionIds, errors));
  const followUpRows = followUps.map((document) => buildFollowUpRow(
      document,
      userIds,
      appointmentIds,
      errors,
  ));

  validateUnique(sessionRows, "appointment_request_id", "companionSessions", errors);
  validateUnique(reportRows, "companion_session_id", "sessionReports", errors);
  validateUnique(followUpRows, "appointment_request_id", "appointmentFollowUps", errors);

  return {
    mode: "companion-session-seed",
    status: errors.length === 0 ? "passed" : "needs_review",
    errors,
    rows: {
      companion_sessions: sessionRows,
      session_reports: reportRows,
      appointment_follow_ups: followUpRows,
    },
    rowCounts: {
      companion_sessions: sessionRows.length,
      session_reports: reportRows.length,
      appointment_follow_ups: followUpRows.length,
    },
  };
}

function buildSessionRow(document, userIds, appointmentIds, errors) {
  const data = document.data;
  const location = document.path;
  const requestId = requiredText(data.appointmentRequestId, location, "appointmentRequestId", errors);
  const managerId = requiredText(data.managerUserId, location, "managerUserId", errors);
  validateReference(requestId, appointmentIds, location, "appointmentRequestId", "appointmentRequests", errors);
  validateReference(managerId, userIds, location, "managerUserId", "users", errors);

  const status = requiredText(data.currentStatus || data.status, location, "currentStatus", errors);
  validateAllowed(status, SESSION_STATUSES, location, "currentStatus", errors);
  const currentStep = nonnegativeInteger(data.currentStepOrder ?? data.currentStep ?? 0, location, "currentStepOrder", errors);
  const createdAt = requiredTimestamp(data.createdAt, location, "createdAt", errors);

  return {
    id: stableUuid("companion_sessions", document.id),
    firestore_id: document.id,
    appointment_request_id: requestId ? stableUuid("appointment_requests", requestId) : null,
    manager_user_id: managerId ? stableUuid("app_users", managerId) : null,
    current_step_order: currentStep,
    current_status: status,
    guardian_update: text(data.guardianUpdate),
    location_summary: text(data.locationSummary),
    field_photo_note: text(data.fieldPhotoNote),
    medication_note: text(data.medicationNote),
    pharmacy_summary: text(data.pharmacySummary),
    prescription_collected: Boolean(data.prescriptionCollected),
    pharmacy_completed: Boolean(data.pharmacyCompleted),
    medication_guidance_completed: Boolean(data.medicationGuidanceCompleted),
    created_at: createdAt,
    updated_at: optionalTimestamp(data.updatedAt, location, "updatedAt", errors) || createdAt,
  };
}

function buildReportRow(document, sessionIds, errors) {
  const data = document.data;
  const location = document.path;
  const sessionId = requiredText(data.sessionId || data.companionSessionId, location, "sessionId", errors);
  validateReference(sessionId, sessionIds, location, "sessionId", "companionSessions", errors);
  const decision = text(data.medicationComparisonDecisionCode).trim();
  validateAllowed(decision, MEDICATION_DECISIONS, location, "medicationComparisonDecisionCode", errors);
  const createdAt = requiredTimestamp(data.createdAt, location, "createdAt", errors);
  const nextVisitNote = text(data.nextVisitAt).trim();

  return {
    id: stableUuid("session_reports", document.id),
    firestore_id: document.id,
    companion_session_id: sessionId ? stableUuid("companion_sessions", sessionId) : null,
    summary: text(data.summary),
    treatment_notes: text(data.treatmentNotes),
    medication_notes: text(data.medicationNotes),
    medication_name: text(data.medicationName),
    medication_change_summary: text(data.medicationChangeSummary),
    medication_schedule_note: text(data.medicationScheduleNote),
    medication_comparison_decision_code: decision,
    medication_comparison_note: text(data.medicationComparisonNote),
    next_visit_at: parseOptionalVisitTimestamp(nextVisitNote),
    next_visit_note: nextVisitNote,
    created_at: createdAt,
    updated_at: optionalTimestamp(data.updatedAt, location, "updatedAt", errors) || createdAt,
  };
}

function buildFollowUpRow(document, userIds, appointmentIds, errors) {
  const data = document.data;
  const location = document.path;
  const requestId = requiredText(data.requestId || data.appointmentRequestId || document.id, location, "requestId", errors);
  validateReference(requestId, appointmentIds, location, "requestId", "appointmentRequests", errors);

  const reviewRating = text(data.reviewRatingCode).trim();
  const settlementStatus = text(data.settlementFollowUpStatus).trim();
  const supportStatus = text(data.supportEscalationStatus).trim();
  validateAllowed(reviewRating, REVIEW_RATINGS, location, "reviewRatingCode", errors);
  validateAllowed(settlementStatus, SETTLEMENT_STATUSES, location, "settlementFollowUpStatus", errors);
  validateAllowed(supportStatus, SUPPORT_STATUSES, location, "supportEscalationStatus", errors);

  const reviewActor = optionalText(data.reviewSavedByUserId);
  const settlementActor = optionalText(data.settlementFollowUpSavedByUserId);
  const supportActor = optionalText(data.supportEscalatedByUserId);
  validateOptionalReference(reviewActor, userIds, location, "reviewSavedByUserId", errors);
  validateOptionalReference(settlementActor, userIds, location, "settlementFollowUpSavedByUserId", errors);
  validateOptionalReference(supportActor, userIds, location, "supportEscalatedByUserId", errors);

  return {
    appointment_request_id: requestId ? stableUuid("appointment_requests", requestId) : null,
    review_rating_code: reviewRating,
    review_comment: text(data.reviewComment),
    review_saved_by_user_id: nullableUserUuid(reviewActor),
    review_saved_at: optionalTimestamp(data.reviewSavedAt, location, "reviewSavedAt", errors),
    settlement_follow_up_status: settlementStatus,
    settlement_follow_up_note: text(data.settlementFollowUpNote),
    settlement_follow_up_saved_by_user_id: nullableUserUuid(settlementActor),
    settlement_follow_up_saved_at: optionalTimestamp(
        data.settlementFollowUpSavedAt,
        location,
        "settlementFollowUpSavedAt",
        errors,
    ),
    support_escalation_status: supportStatus,
    support_escalated_by_user_id: nullableUserUuid(supportActor),
    support_escalated_at: optionalTimestamp(data.supportEscalatedAt, location, "supportEscalatedAt", errors),
    updated_at: requiredTimestamp(data.updatedAt, location, "updatedAt", errors),
  };
}

function buildSessionSeedSql(plan, {rollback = false} = {}) {
  if (!plan || plan.mode !== "companion-session-seed") {
    throw new Error("동행 세션 seed plan 형식이 아닙니다.");
  }
  if (plan.status !== "passed") {
    throw new Error(`검증 오류 ${plan.errors?.length || 0}건이 있어 SQL을 생성하지 않습니다.`);
  }

  const lines = [
    "-- BoDeul companion session PostgreSQL seed",
    "-- Firestore 백업의 개인정보가 포함되므로 생성 파일을 커밋하거나 공유하지 않는다.",
    "begin;",
    "set local role bodeul_migration;",
    "",
  ];

  if (rollback) {
    appendRollback(lines, plan);
  } else {
    for (const row of plan.rows.companion_sessions) {
      lines.push(buildUpsert(TABLES.sessions, SESSION_COLUMNS, row, ["firestore_id"]));
      lines.push("");
    }
    for (const row of plan.rows.session_reports) {
      lines.push(buildUpsert(TABLES.reports, REPORT_COLUMNS, row, ["firestore_id"]));
      lines.push("");
    }
    for (const row of plan.rows.appointment_follow_ups) {
      lines.push(buildUpsert(
          TABLES.followUps,
          FOLLOW_UP_COLUMNS,
          row,
          ["appointment_request_id"],
      ));
      lines.push("");
    }
  }

  lines.push("commit;");
  lines.push("");
  return lines.join("\n");
}

function appendRollback(lines, plan) {
  appendDeleteByValues(
      lines,
      TABLES.reports,
      "firestore_id",
      plan.rows.session_reports.map((row) => row.firestore_id),
  );
  appendDeleteByValues(
      lines,
      TABLES.followUps,
      "appointment_request_id",
      plan.rows.appointment_follow_ups.map((row) => row.appointment_request_id),
  );
  appendDeleteByValues(
      lines,
      TABLES.sessions,
      "firestore_id",
      plan.rows.companion_sessions.map((row) => row.firestore_id),
  );
}

function appendDeleteByValues(lines, table, column, values) {
  if (values.length === 0) {
    lines.push(`-- ${table}: 삭제할 백필 행이 없습니다.`);
    return;
  }
  lines.push(`delete from ${table}`);
  lines.push(`where ${quoteIdentifier(column)} in (${values.map(quoteString).join(", ")});`);
  lines.push("");
}

function buildUpsert(table, columns, row, conflictColumns) {
  const insertColumns = [...columns, "imported_at"].map(quoteIdentifier);
  const values = [
    ...columns.map((column) => toSqlLiteral(row[column])),
    "now()",
  ];
  const updates = columns
      .filter((column) => !["id", ...conflictColumns].includes(column))
      .map((column) => `${quoteIdentifier(column)} = excluded.${quoteIdentifier(column)}`);
  updates.push(`${quoteIdentifier("imported_at")} = now()`);

  return [
    `insert into ${table} (${insertColumns.join(", ")})`,
    `values (${values.join(", ")})`,
    `on conflict (${conflictColumns.map(quoteIdentifier).join(", ")}) do update set ${updates.join(", ")};`,
  ].join("\n");
}

function normalizeCollection(snapshot, collectionName) {
  const documents = snapshot.collections?.[collectionName];
  if (!Array.isArray(documents)) {
    return [];
  }
  return documents.map((document) => ({
    id: typeof document.id === "string" ? document.id : "",
    path: typeof document.path === "string" ? document.path : `${collectionName}/${document.id || ""}`,
    data: document.data || fromFirestoreFields(document.fields || {}),
  }));
}

function validateUnique(rows, field, collectionName, errors) {
  const seen = new Set();
  for (const row of rows) {
    const value = row[field];
    if (!value || seen.has(value)) {
      if (value) {
        errors.push(diagnostic(collectionName, field, "1:1 관계를 위반하는 중복 참조입니다."));
      }
      continue;
    }
    seen.add(value);
  }
}

function validateReference(value, references, path, field, collection, errors) {
  if (value && !references.has(value)) {
    errors.push(diagnostic(path, field, `${collection} 백업에서 참조 대상을 찾지 못했습니다.`));
  }
}

function validateOptionalReference(value, references, path, field, errors) {
  if (value) {
    validateReference(value, references, path, field, "users", errors);
  }
}

function validateAllowed(value, allowed, path, field, errors) {
  if (!allowed.has(value)) {
    errors.push(diagnostic(path, field, "현재 앱 계약에 없는 코드입니다."));
  }
}

function requiredText(value, path, field, errors) {
  const normalized = optionalText(value);
  if (!normalized) {
    errors.push(diagnostic(path, field, "필수 값이 비어 있습니다."));
  }
  return normalized;
}

function nonnegativeInteger(value, path, field, errors) {
  const numberValue = Number(value);
  if (!Number.isSafeInteger(numberValue) || numberValue < 0) {
    errors.push(diagnostic(path, field, "0 이상의 안전한 정수가 아닙니다."));
    return 0;
  }
  return numberValue;
}

function requiredTimestamp(value, path, field, errors) {
  if (value === null || value === undefined || value === "") {
    errors.push(diagnostic(path, field, "필수 시각이 비어 있습니다."));
    return null;
  }
  return optionalTimestamp(value, path, field, errors);
}

function optionalTimestamp(value, path, field, errors) {
  if (value === null || value === undefined || value === "") {
    return null;
  }
  const parsed = parseTimestampMillis(value);
  if (!Number.isFinite(parsed)) {
    errors.push(diagnostic(path, field, "지원하는 시각 형식으로 해석할 수 없습니다."));
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

function parseOptionalVisitTimestamp(value) {
  if (!value) {
    return null;
  }
  const localDateTime = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})$/.exec(value);
  if (localDateTime) {
    const year = Number(localDateTime[1]);
    const month = Number(localDateTime[2]);
    const day = Number(localDateTime[3]);
    const hour = Number(localDateTime[4]);
    const minute = Number(localDateTime[5]);
    const localCalendar = new Date(Date.UTC(year, month - 1, day, hour, minute));
    if (localCalendar.getUTCFullYear() !== year
        || localCalendar.getUTCMonth() !== month - 1
        || localCalendar.getUTCDate() !== day
        || localCalendar.getUTCHours() !== hour
        || localCalendar.getUTCMinutes() !== minute) {
      return null;
    }
    return new Date(localCalendar.getTime() - (9 * 60 * 60 * 1000)).toISOString();
  }
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? new Date(parsed).toISOString() : null;
}

function nullableUserUuid(firebaseUid) {
  return firebaseUid ? stableUuid("app_users", firebaseUid) : null;
}

function optionalText(value) {
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
    mode: "companion-session-seed",
    status: "needs_review",
    errors: [diagnostic("backup", "root", message)],
    rows: {},
    rowCounts: {},
  };
}

function toSqlLiteral(value) {
  if (value === null || value === undefined) {
    return "null";
  }
  if (typeof value === "number") {
    return Number.isFinite(value) ? String(value) : "null";
  }
  if (typeof value === "boolean") {
    return value ? "true" : "false";
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
  buildSessionSeedPlan,
  buildSessionSeedSql,
};
