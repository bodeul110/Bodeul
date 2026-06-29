#!/usr/bin/env node

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const {MANAGED_COLLECTIONS} = require("./lib/baseline-config");
const {validateBackupSnapshot} = require("./lib/backup-validator");

const UUID_NAMESPACE = "8e884ace-2c0f-4a5b-9ddf-2ff3d8efb9d1";

async function main() {
  const options = parseOptions(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }
  if (!options.filePath) {
    throw new Error("seed 입력을 만들 Firestore 백업 JSON 경로가 필요합니다. --file 옵션을 지정해 주세요.");
  }

  const backupPath = path.resolve(process.cwd(), options.filePath);
  const snapshot = readJsonFile(backupPath);
  const validation = validateBackupSnapshot(snapshot, MANAGED_COLLECTIONS);
  const collections = normalizeCollections(snapshot);
  const seedInput = buildSeedInput({backupPath, snapshot, validation, collections});
  const outputPath = resolveOutputPath(options.outputPath);

  fs.mkdirSync(path.dirname(outputPath), {recursive: true});
  fs.writeFileSync(outputPath, `${JSON.stringify(seedInput, null, 2)}\n`, "utf8");

  printSummary(seedInput, outputPath);
  if (seedInput.status !== "passed") {
    process.exitCode = 1;
  }
}

function parseOptions(args) {
  const fileIndex = args.indexOf("--file");
  const outputIndex = args.indexOf("--output");
  const optionValueIndexes = new Set();
  if (fileIndex >= 0) {
    optionValueIndexes.add(fileIndex + 1);
  }
  if (outputIndex >= 0) {
    optionValueIndexes.add(outputIndex + 1);
  }
  const positionalArgs = args.filter((arg, index) => !arg.startsWith("--") && !optionValueIndexes.has(index));
  return {
    filePath: fileIndex >= 0 ? args[fileIndex + 1] : positionalArgs[0] || "",
    outputPath: outputIndex >= 0 ? args[outputIndex + 1] : positionalArgs[1] || "",
    help: args.includes("--help") || args.includes("-h"),
  };
}

function printHelp() {
  console.log("보들 Firestore -> PostgreSQL seed 입력 JSON 생성기");
  console.log("");
  console.log("사용법:");
  console.log("  node build-postgres-seed-input.js --file backups/firestore-backup.json");
  console.log("  node build-postgres-seed-input.js --file backups/firestore-backup.json --output reports/postgres-seed-input.json");
  console.log("  node build-postgres-seed-input.js backups/firestore-backup.json reports/postgres-seed-input.json");
  console.log("");
  console.log("주의:");
  console.log("- 이 스크립트는 Supabase나 Firestore에 쓰기 작업을 하지 않습니다.");
  console.log("- 출력 JSON은 운영 데이터와 개인정보를 포함할 수 있으므로 커밋하지 않습니다.");
}

function buildSeedInput({backupPath, snapshot, validation, collections}) {
  const diagnostics = [];
  const rows = {
    app_users: buildAppUsers(collections, diagnostics),
    manager_document_files: buildManagerDocumentFiles(collections),
    manager_document_reviews: buildManagerDocumentReviews(collections),
    hospital_guides: buildHospitalGuides(collections),
    appointment_requests: buildAppointmentRequests(collections, diagnostics),
    companion_sessions: buildCompanionSessions(collections, diagnostics),
    session_reports: buildSessionReports(collections, diagnostics),
    appointment_follow_ups: buildAppointmentFollowUps(collections),
    support_requests: buildSupportRequests(collections, diagnostics),
    admin_audit_logs: buildAdminAuditLogs(collections, diagnostics),
  };
  const tableOrder = [
    "app_users",
    "hospital_guides",
    "appointment_requests",
    "companion_sessions",
    "session_reports",
    "appointment_follow_ups",
    "support_requests",
    "manager_document_files",
    "manager_document_reviews",
    "admin_audit_logs",
  ];
  const rowCounts = {};
  for (const table of tableOrder) {
    rowCounts[table] = rows[table].length;
  }
  const status = validation.errors.length === 0 && diagnostics.every((item) => item.level !== "error")
    ? "passed"
    : "needs_review";

  return {
    schemaVersion: 1,
    mode: "seed-input",
    status,
    generatedAt: new Date().toISOString(),
    source: {
      backupPath,
      projectId: snapshot.projectId || "",
      backupGeneratedAt: snapshot.generatedAt || "",
      backupSchemaVersion: snapshot.schemaVersion ?? null,
    },
    idMapping: {
      strategy: "deterministic_uuid_v5_like_sha1",
      namespace: UUID_NAMESPACE,
      rule: "테이블명과 Firestore 문서 ID 또는 논리 키를 조합해 같은 입력은 항상 같은 UUID로 변환한다.",
    },
    validation: {
      errors: validation.errors,
      warnings: validation.warnings,
    },
    diagnostics,
    tableOrder,
    rowCounts,
    rows,
  };
}

function buildAppUsers(collections, diagnostics) {
  return documentsOf(collections, "users").map((document) => {
    const data = document.data;
    const role = pickText(data, ["role"]);
    if (!role) {
      diagnostics.push(createDiagnostic("error", "app_users", document.path, "role 필드가 없습니다."));
    }
    return {
      id: userUuid(document.id),
      firebase_uid: document.id,
      role: role || "PATIENT",
      name: pickText(data, ["name", "displayName"]),
      email: pickText(data, ["email"]),
      phone: pickText(data, ["phone", "phoneNumber"]),
      manager_document_status: pickNullableText(data, ["managerDocumentStatus"]),
      manager_document_review_note: pickText(data, ["managerDocumentReviewNote"]),
      manager_document_updated_at: pickTimestamp(data, ["managerDocumentUpdatedAt"]),
      manager_document_reviewed_at: pickTimestamp(data, ["managerDocumentReviewedAt"]),
      manager_document_reviewed_by_name: pickText(data, ["managerDocumentReviewedByName"]),
      created_at: pickTimestamp(data, ["createdAt"]),
      updated_at: pickTimestamp(data, ["updatedAt"]),
    };
  });
}

function buildManagerDocumentFiles(collections) {
  const rows = [];
  for (const document of documentsOf(collections, "users")) {
    for (const file of collectManagerDocumentFiles(document.data)) {
      rows.push({
        id: stableUuid("manager_document_files", `${document.id}:${file.document_key}:${file.storage_path}`),
        manager_user_id: userUuid(document.id),
        document_key: file.document_key,
        storage_provider: "firebase_storage",
        storage_path: file.storage_path,
        file_name: file.file_name,
        content_type: file.content_type,
        uploaded_at: file.uploaded_at,
      });
    }
  }
  return rows;
}

function buildManagerDocumentReviews(collections) {
  const rows = [];
  for (const document of documentsOf(collections, "users")) {
    const history = asArray(document.data.managerDocumentHistory);
    history.forEach((item, index) => {
      rows.push({
        id: stableUuid("manager_document_reviews", `${document.id}:${index}:${pickText(item, ["status"])}`),
        manager_user_id: userUuid(document.id),
        status: pickText(item, ["status"]) || "PENDING_REVIEW",
        review_note: pickText(item, ["reviewNote", "managerDocumentReviewNote"]),
        reviewed_by_user_id: nullableUserUuid(pickText(item, ["reviewedByUserId", "adminUserId"])),
        reviewed_by_name: pickText(item, ["reviewedByName", "managerDocumentReviewedByName"]),
        reviewed_at: pickTimestamp(item, ["reviewedAt", "happenedAt", "createdAt"]) || new Date(0).toISOString(),
      });
    });
  }
  return rows;
}

function buildHospitalGuides(collections) {
  return documentsOf(collections, "hospitalGuides").map((document) => ({
    id: stableUuid("hospital_guides", document.id),
    hospital_name: pickText(document.data, ["hospitalName"]),
    department_name: pickText(document.data, ["departmentName"]),
    steps: asArray(document.data.steps),
    created_at: pickTimestamp(document.data, ["createdAt"]),
    updated_at: pickTimestamp(document.data, ["updatedAt"]),
  }));
}

function buildAppointmentRequests(collections, diagnostics) {
  return documentsOf(collections, "appointmentRequests").map((document) => {
    const data = document.data;
    const status = pickText(data, ["status"]);
    if (!status) {
      diagnostics.push(createDiagnostic("error", "appointment_requests", document.path, "status 필드가 없습니다."));
    }
    return {
      id: requestUuid(document.id),
      firestore_id: document.id,
      patient_user_id: nullableUserUuid(pickText(data, ["patientUserId"])),
      guardian_user_id: nullableUserUuid(pickText(data, ["guardianUserId"])),
      manager_user_id: nullableUserUuid(pickText(data, ["managerUserId"])),
      requester_user_id: nullableUserUuid(pickText(data, ["requesterUserId"])),
      requester_role: pickText(data, ["requesterRole"]),
      patient_name: pickText(data, ["patientName"]),
      patient_phone: pickText(data, ["patientPhone"]),
      guardian_name: pickText(data, ["guardianName"]),
      guardian_phone: pickText(data, ["guardianPhone"]),
      hospital_name: pickText(data, ["hospitalName"]),
      department_name: pickText(data, ["departmentName"]),
      hospital_latitude: pickNumber(data, ["hospitalLatitude", "latitude"]),
      hospital_longitude: pickNumber(data, ["hospitalLongitude", "longitude"]),
      appointment_at: pickTimestamp(data, ["appointmentAt"]) || timestampFromMillis(data.appointmentAtEpochMillis),
      appointment_date_key: pickText(data, ["appointmentDateKey"]),
      meeting_place: pickText(data, ["meetingPlace"]),
      special_notes: pickText(data, ["specialNotes"]),
      status: status || "REQUESTED",
      base_price: pickInteger(data, ["basePrice"]),
      option_surcharge_price: pickInteger(data, ["optionSurchargePrice"]),
      coupon_discount_price: pickInteger(data, ["couponDiscountPrice"]),
      final_price: pickInteger(data, ["finalPrice"]),
      payment_method_code: pickText(data, ["paymentMethodCode"]),
      payment_status_code: pickText(data, ["paymentStatusCode"]),
      payment_approval_code: pickText(data, ["paymentApprovalCode"]),
      payment_approved_at: pickTimestamp(data, ["paymentApprovedAt"]),
      created_at: pickTimestamp(data, ["createdAt"]),
      updated_at: pickTimestamp(data, ["updatedAt"]),
    };
  });
}

function buildCompanionSessions(collections, diagnostics) {
  return documentsOf(collections, "companionSessions").map((document) => {
    const data = document.data;
    const requestId = pickText(data, ["appointmentRequestId", "requestId"]);
    const currentStatus = pickText(data, ["currentStatus", "status"]);
    if (!requestId) {
      diagnostics.push(createDiagnostic("error", "companion_sessions", document.path, "appointmentRequestId 필드가 없습니다."));
    }
    if (!currentStatus) {
      diagnostics.push(createDiagnostic("error", "companion_sessions", document.path, "currentStatus 필드가 없습니다."));
    }
    return {
      id: sessionUuid(document.id),
      firestore_id: document.id,
      appointment_request_id: requestId ? requestUuid(requestId) : null,
      manager_user_id: nullableUserUuid(pickText(data, ["managerUserId"])),
      current_step_order: pickInteger(data, ["currentStepOrder", "currentStep"]),
      current_status: currentStatus || "REQUESTED",
      guardian_update: pickText(data, ["guardianUpdate"]),
      location_summary: pickText(data, ["locationSummary"]),
      field_photo_note: pickText(data, ["fieldPhotoNote"]),
      medication_note: pickText(data, ["medicationNote"]),
      pharmacy_summary: pickText(data, ["pharmacySummary"]),
      prescription_collected: pickBoolean(data, ["prescriptionCollected"]),
      pharmacy_completed: pickBoolean(data, ["pharmacyCompleted"]),
      medication_guidance_completed: pickBoolean(data, ["medicationGuidanceCompleted"]),
      created_at: pickTimestamp(data, ["createdAt"]),
      updated_at: pickTimestamp(data, ["updatedAt"]),
    };
  });
}

function buildSessionReports(collections, diagnostics) {
  return documentsOf(collections, "sessionReports").map((document) => {
    const data = document.data;
    const sessionId = pickText(data, ["sessionId", "companionSessionId"]);
    if (!sessionId) {
      diagnostics.push(createDiagnostic("error", "session_reports", document.path, "sessionId 필드가 없습니다."));
    }
    return {
      id: stableUuid("session_reports", document.id),
      firestore_id: document.id,
      companion_session_id: sessionId ? sessionUuid(sessionId) : null,
      summary: pickText(data, ["summary"]),
      treatment_notes: pickText(data, ["treatmentNotes"]),
      medication_notes: pickText(data, ["medicationNotes"]),
      medication_name: pickText(data, ["medicationName"]),
      medication_change_summary: pickText(data, ["medicationChangeSummary"]),
      medication_schedule_note: pickText(data, ["medicationScheduleNote"]),
      next_visit_at: pickTimestamp(data, ["nextVisitAt"]),
      created_at: pickTimestamp(data, ["createdAt"]),
    };
  });
}

function buildAppointmentFollowUps(collections) {
  const byRequestId = new Map();
  mergeFollowUpCollection(byRequestId, collections, "appointmentFollowUps", (row, data) => {
    row.review_rating_code = pickText(data, ["reviewRatingCode"]);
    row.review_comment = pickText(data, ["reviewComment"]);
    row.review_saved_at = pickTimestamp(data, ["reviewSavedAt"]);
  });
  mergeFollowUpCollection(byRequestId, collections, "adminSettlementRecords", (row, data) => {
    row.settlement_follow_up_status = pickText(data, ["settlementFollowUpStatus", "status"]);
    row.settlement_follow_up_note = pickText(data, ["settlementFollowUpNote", "note"]);
    row.settlement_follow_up_saved_at = pickTimestamp(data, ["settlementFollowUpSavedAt", "updatedAt", "createdAt"]);
  });
  mergeFollowUpCollection(byRequestId, collections, "adminEmergencyIssues", (row, data) => {
    row.support_escalation_status = pickText(data, ["supportEscalationStatus", "status"]);
    row.support_escalated_at = pickTimestamp(data, ["supportEscalatedAt", "createdAt"]);
  });
  return Array.from(byRequestId.values());
}

function buildSupportRequests(collections, diagnostics) {
  const rows = [];
  for (const collectionName of ["supportInquiries", "clientSupportRequests"]) {
    for (const document of documentsOf(collections, collectionName)) {
      const data = document.data;
      const requestId = pickNullableText(data, ["appointmentRequestId", "requestId"]);
      const statusCode = pickText(data, ["statusCode", "status"]);
      const responseText = pickText(data, ["responseText", "answer", "adminResponse"]);
      const respondedByName = pickText(data, ["respondedByName", "adminName"]);
      const respondedAt = pickTimestamp(data, ["respondedAt", "answeredAt"]);
      const createdAt = pickTimestamp(data, ["createdAt"]);
      rows.push({
        id: stableUuid("support_requests", `${collectionName}:${document.id}`),
        firestore_id: `${collectionName}/${document.id}`,
        requester_user_id: nullableUserUuid(pickText(data, ["requesterUserId", "userId", "managerUserId"])),
        requester_role: pickText(data, ["requesterRole", "role"]),
        appointment_request_id: requestId ? requestUuid(requestId) : null,
        category_code: pickText(data, ["categoryCode", "category"]),
        title: pickText(data, ["title"]),
        body: pickText(data, ["body", "message", "content"]),
        status_code: statusCode,
        response_text: responseText,
        responded_by_user_id: nullableUserUuid(pickText(data, ["respondedByUserId", "adminUserId"])),
        responded_by_name: respondedByName,
        responded_at: hasSupportResponse({statusCode, responseText, respondedByName}) ? respondedAt : null,
        created_at: createdAt,
        updated_at: pickTimestamp(data, ["updatedAt"]) || createdAt,
      });
    }
  }
  if (rows.some((row) => !row.title && !row.body)) {
    diagnostics.push(createDiagnostic("warning", "support_requests", "", "제목과 본문이 모두 비어 있는 문의 후보가 있습니다."));
  }
  return rows;
}

function buildAdminAuditLogs(collections, diagnostics) {
  const supportIdIndex = buildSupportIdIndex(collections);
  return documentsOf(collections, "adminAuditLogs").map((document) => {
    const data = document.data;
    const actionSummary = pickText(data, ["actionSummary", "summary", "message"]);
    if (!actionSummary) {
      diagnostics.push(createDiagnostic("error", "admin_audit_logs", document.path, "actionSummary 필드가 없습니다."));
    }
    return {
      id: stableUuid("admin_audit_logs", document.id),
      actor_user_id: nullableUserUuid(pickText(data, ["actorUserId", "adminUserId"])),
      actor_name: pickText(data, ["actorName", "adminName"]),
      source_type: pickText(data, ["sourceType", "type"]),
      request_id: nullableRequestUuid(pickText(data, ["requestId", "appointmentRequestId"])),
      inquiry_id: resolveSupportUuid(pickText(data, ["inquiryId", "supportRequestId"]), supportIdIndex),
      action_summary: actionSummary || "",
      note: pickText(data, ["note"]),
      created_at: pickTimestamp(data, ["createdAt"]),
    };
  });
}

function mergeFollowUpCollection(byRequestId, collections, collectionName, applyData) {
  for (const document of documentsOf(collections, collectionName)) {
    const requestId = pickText(document.data, ["requestId", "appointmentRequestId"]) || document.id;
    if (!requestId) {
      continue;
    }
    const row = byRequestId.get(requestId) || {
      appointment_request_id: requestUuid(requestId),
      review_rating_code: "",
      review_comment: "",
      review_saved_at: null,
      settlement_follow_up_status: "",
      settlement_follow_up_note: "",
      settlement_follow_up_saved_at: null,
      support_escalation_status: "",
      support_escalated_at: null,
      updated_at: pickTimestamp(document.data, ["updatedAt", "createdAt"]),
    };
    applyData(row, document.data);
    row.updated_at = pickTimestamp(document.data, ["updatedAt", "createdAt"]) || row.updated_at;
    byRequestId.set(requestId, row);
  }
}

function normalizeCollections(snapshot) {
  const collections = {};
  for (const [collectionName, documents] of Object.entries(snapshot.collections || {})) {
    collections[collectionName] = Array.isArray(documents)
      ? documents.map((document) => ({
        id: typeof document.id === "string" ? document.id : "",
        path: typeof document.path === "string" ? document.path : `${collectionName}/${document.id || ""}`,
        data: document.data || fromFirestoreFields(document.fields || {}),
      }))
      : [];
  }
  return collections;
}

function fromFirestoreFields(fields) {
  const data = {};
  for (const [key, value] of Object.entries(fields || {})) {
    data[key] = fromFirestoreValue(value);
  }
  return data;
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

function collectManagerDocumentFiles(data) {
  const files = [];
  const fileMap = isPlainObject(data.managerDocumentFiles) ? data.managerDocumentFiles : {};
  const pathMap = isPlainObject(data.managerDocumentFilePaths) ? data.managerDocumentFilePaths : {};
  const keys = new Set(Object.keys(fileMap).concat(Object.keys(pathMap)));
  for (const key of keys) {
    const file = isPlainObject(fileMap[key]) ? fileMap[key] : {};
    const storagePath = firstNonEmpty(file.storagePath, file.fullPath, pathMap[key]);
    if (!storagePath) {
      continue;
    }
    files.push({
      document_key: key,
      storage_path: storagePath,
      file_name: firstNonEmpty(file.fileName, file.name),
      content_type: firstNonEmpty(file.contentType),
      uploaded_at: toTimestamp(firstNonEmpty(file.uploadedAt, file.createdAt)),
    });
  }
  return files;
}

function documentsOf(collections, collectionName) {
  return Array.isArray(collections[collectionName]) ? collections[collectionName] : [];
}

function readJsonFile(filePath) {
  return JSON.parse(fs.readFileSync(filePath, "utf8").replace(/^\uFEFF/, ""));
}

function resolveOutputPath(outputPath) {
  if (outputPath) {
    return path.resolve(process.cwd(), outputPath);
  }
  return path.resolve(process.cwd(), "reports", `postgres-seed-input-${formatTimestamp(new Date())}.json`);
}

function stableUuid(scope, key) {
  const namespaceBytes = Buffer.from(UUID_NAMESPACE.replaceAll("-", ""), "hex");

  // 보안용 해시가 아니라 Firestore 문서 키를 반복 가능한 UUID로 바꾸기 위한 식별자 해시다.
  // codeql[js/weak-cryptographic-algorithm]
  const hash = crypto.createHash("sha1")
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

function userUuid(firebaseUid) {
  return stableUuid("app_users", firebaseUid);
}

function nullableUserUuid(firebaseUid) {
  return firebaseUid ? userUuid(firebaseUid) : null;
}

function requestUuid(firestoreId) {
  return stableUuid("appointment_requests", firestoreId);
}

function nullableRequestUuid(firestoreId) {
  return firestoreId ? requestUuid(firestoreId) : null;
}

function sessionUuid(firestoreId) {
  return stableUuid("companion_sessions", firestoreId);
}

function nullableSupportUuid(firestoreId) {
  if (!firestoreId) {
    return null;
  }
  const parts = String(firestoreId).split("/");
  if (parts.length >= 2) {
    return stableUuid("support_requests", `${parts[0]}:${parts[1]}`);
  }
  return stableUuid("support_requests", `supportInquiries:${firestoreId}`);
}

function buildSupportIdIndex(collections) {
  const index = new Map();
  for (const collectionName of ["supportInquiries", "clientSupportRequests"]) {
    for (const document of documentsOf(collections, collectionName)) {
      index.set(document.id, stableUuid("support_requests", `${collectionName}:${document.id}`));
      index.set(`${collectionName}/${document.id}`, stableUuid("support_requests", `${collectionName}:${document.id}`));
    }
  }
  return index;
}

function resolveSupportUuid(firestoreId, supportIdIndex) {
  if (!firestoreId) {
    return null;
  }
  const key = String(firestoreId);
  return supportIdIndex.get(key) || nullableSupportUuid(key);
}

function pickText(data, aliases) {
  const value = pickValue(data, aliases);
  return value === undefined || value === null ? "" : String(value);
}

function pickNullableText(data, aliases) {
  const value = pickText(data, aliases);
  return value || null;
}

function pickInteger(data, aliases) {
  const value = pickValue(data, aliases);
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? Math.trunc(numberValue) : 0;
}

function pickNumber(data, aliases) {
  const value = pickValue(data, aliases);
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : null;
}

function pickBoolean(data, aliases) {
  return Boolean(pickValue(data, aliases));
}

function pickTimestamp(data, aliases) {
  return toTimestamp(pickValue(data, aliases));
}

function pickValue(data, aliases) {
  for (const alias of aliases) {
    if (data && data[alias] !== undefined && data[alias] !== null) {
      return data[alias];
    }
  }
  return undefined;
}

function timestampFromMillis(value) {
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? new Date(numberValue).toISOString() : null;
}

function toTimestamp(value) {
  if (value === undefined || value === null || value === "") {
    return null;
  }
  if (typeof value === "number") {
    return new Date(value).toISOString();
  }
  if (typeof value === "string") {
    const parsed = Date.parse(value);
    return Number.isNaN(parsed) ? null : new Date(parsed).toISOString();
  }
  if (isPlainObject(value)) {
    if (typeof value.seconds === "number") {
      return new Date(value.seconds * 1000).toISOString();
    }
    if (typeof value._seconds === "number") {
      return new Date(value._seconds * 1000).toISOString();
    }
  }
  return null;
}

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function isPlainObject(value) {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function firstNonEmpty(...values) {
  for (const value of values) {
    if (value !== undefined && value !== null && value !== "") {
      return value;
    }
  }
  return "";
}

function hasSupportResponse({statusCode, responseText, respondedByName}) {
  const normalizedStatus = String(statusCode || "").toUpperCase();
  return Boolean(
      responseText ||
      respondedByName ||
      ["ANSWERED", "RESOLVED", "COMPLETED"].includes(normalizedStatus),
  );
}

function createDiagnostic(level, table, sourcePath, message) {
  return {level, table, sourcePath, message};
}

function formatTimestamp(date) {
  const year = String(date.getFullYear());
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const hour = String(date.getHours()).padStart(2, "0");
  const minute = String(date.getMinutes()).padStart(2, "0");
  const second = String(date.getSeconds()).padStart(2, "0");
  return `${year}${month}${day}-${hour}${minute}${second}`;
}

function printSummary(seedInput, outputPath) {
  console.log("Firestore -> PostgreSQL seed 입력 JSON");
  console.log(`- 상태: ${seedInput.status}`);
  console.log(`- 백업 파일: ${seedInput.source.backupPath}`);
  console.log(`- 출력 파일: ${outputPath}`);
  console.log(`- 진단: ${seedInput.diagnostics.length}건`);
  for (const table of seedInput.tableOrder) {
    console.log(`- ${table}: ${seedInput.rowCounts[table]}건`);
  }
}

main().catch((error) => {
  console.error("PostgreSQL seed 입력 JSON 생성 중 오류가 발생했습니다.");
  console.error(error);
  process.exitCode = 1;
});
