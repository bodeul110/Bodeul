const {onDocumentWritten} = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const {FieldValue, Timestamp, getFirestore} = require("firebase-admin/firestore");

const USER_LINK_SYNC_OPTIONS = {
  region: "asia-northeast3",
  document: "users/{userId}",
};

const APPOINTMENT_REQUEST_SYNC_OPTIONS = {
  region: "asia-northeast3",
  document: "appointmentRequests/{appointmentRequestId}",
};

const REMINDER_SCAN_STATUSES = new Set(["REQUESTED", "MATCHED"]);
const REMINDER_SKIPPED_STATE = "SKIPPED";
const REMINDER_CLEANUP_STATES = new Set(["PENDING", "PROCESSING", "FAILED"]);

const syncLinkedAppointmentParticipants = onDocumentWritten(
    USER_LINK_SYNC_OPTIONS,
    async (event) => {
      if (!event.data?.after?.exists) {
        return;
      }

      const afterProfile = toLinkableUserProfile(event.data.after);
      if (!afterProfile) {
        return;
      }

      const beforeProfile = event.data?.before?.exists
          ? toLinkableUserProfile(event.data.before)
          : null;
      if (!shouldSyncLinkedAppointments(beforeProfile, afterProfile)) {
        return;
      }

      const summary = await syncLinkedAppointmentsForUser(getFirestore(), afterProfile);
      logger.info("기존 동행 신청 연결 상태를 동기화했습니다.", summary);
    },
);

const cleanupAppointmentReminderJobs = onDocumentWritten(
    APPOINTMENT_REQUEST_SYNC_OPTIONS,
    async (event) => {
      const appointmentRequestId = sanitizeText(event.params?.appointmentRequestId);
      if (!appointmentRequestId) {
        return;
      }

      const cleanupReason = resolveReminderCleanupReason(
          event.data?.before?.exists ? event.data.before.data() : null,
          event.data?.after?.exists ? event.data.after.data() : null,
      );
      if (!cleanupReason) {
        return;
      }

      const summary = await skipPendingReminderJobsForAppointment(
          getFirestore(),
          appointmentRequestId,
          cleanupReason,
      );
      if (summary.skippedJobs > 0) {
        logger.info("예약 알림 작업 정리를 마쳤습니다.", summary);
      }
    },
);

function toLinkableUserProfile(documentSnapshot) {
  const role = sanitizeText(documentSnapshot.get("role"));
  if (!["PATIENT", "GUARDIAN"].includes(role)) {
    return null;
  }

  return {
    userId: documentSnapshot.id,
    role,
    name: sanitizeText(documentSnapshot.get("name")),
    email: normalizeComparableEmail(documentSnapshot.get("email")),
    phone: normalizeProfilePhone(documentSnapshot.get("phone")),
  };
}

function shouldSyncLinkedAppointments(beforeProfile, afterProfile) {
  if (!afterProfile || (!afterProfile.email && !afterProfile.phone)) {
    return false;
  }
  if (!beforeProfile) {
    return true;
  }
  return beforeProfile.role !== afterProfile.role ||
      beforeProfile.email !== afterProfile.email ||
      beforeProfile.phone !== afterProfile.phone;
}

async function syncLinkedAppointmentsForUser(firestore, userProfile) {
  const roleConfig = resolveParticipantRoleConfig(userProfile.role);
  if (!roleConfig) {
    return {
      userId: userProfile.userId,
      role: userProfile.role,
      matchedRequests: 0,
      updatedRequests: 0,
    };
  }

  const matchedRequests = new Map();
  const emailMatches = await collectPendingLinkedRequests(
      firestore,
      roleConfig,
      roleConfig.emailField,
      userProfile.email,
      matchedRequests,
  );
  const phoneMatches = await collectPendingLinkedRequests(
      firestore,
      roleConfig,
      roleConfig.phoneField,
      userProfile.phone,
      matchedRequests,
  );

  if (matchedRequests.size === 0) {
    return {
      userId: userProfile.userId,
      role: userProfile.role,
      emailMatches,
      phoneMatches,
      matchedRequests: 0,
      updatedRequests: 0,
    };
  }

  const updatePayload = {
    [roleConfig.userIdField]: userProfile.userId,
    [roleConfig.nameField]: userProfile.name,
    [roleConfig.phoneField]: userProfile.phone,
    [roleConfig.emailField]: userProfile.email,
    updatedAt: FieldValue.serverTimestamp(),
  };

  await Promise.all(Array.from(matchedRequests.values()).map((documentSnapshot) =>
    documentSnapshot.ref.update(updatePayload),
  ));

  return {
    userId: userProfile.userId,
    role: userProfile.role,
    emailMatches,
    phoneMatches,
    matchedRequests: matchedRequests.size,
    updatedRequests: matchedRequests.size,
  };
}

async function collectPendingLinkedRequests(
    firestore,
    roleConfig,
    matchField,
    matchValue,
    matchedRequests,
) {
  if (!matchValue) {
    return 0;
  }

  const snapshots = await Promise.all([
    firestore.collection("appointmentRequests")
        .where(roleConfig.userIdField, "==", "")
        .where(matchField, "==", matchValue)
        .get(),
    firestore.collection("appointmentRequests")
        .where(roleConfig.userIdField, "==", null)
        .where(matchField, "==", matchValue)
        .get(),
  ]);

  let foundCount = 0;
  for (const snapshot of snapshots) {
    for (const documentSnapshot of snapshot.docs) {
      foundCount++;
      matchedRequests.set(documentSnapshot.id, documentSnapshot);
    }
  }
  return foundCount;
}

function resolveParticipantRoleConfig(role) {
  if (role === "PATIENT") {
    return {
      userIdField: "patientUserId",
      nameField: "patientName",
      phoneField: "patientPhone",
      emailField: "patientEmail",
    };
  }
  if (role === "GUARDIAN") {
    return {
      userIdField: "guardianUserId",
      nameField: "guardianName",
      phoneField: "guardianPhone",
      emailField: "guardianEmail",
    };
  }
  return null;
}

function resolveReminderCleanupReason(beforeData, afterData) {
  if (!beforeData && !afterData) {
    return "";
  }
  if (beforeData && !afterData) {
    return "appointment_deleted";
  }
  if (!beforeData || !afterData) {
    return "";
  }

  const beforeStatus = sanitizeText(beforeData.status);
  const afterStatus = sanitizeText(afterData.status);
  if (beforeStatus !== afterStatus && !REMINDER_SCAN_STATUSES.has(afterStatus)) {
    return afterStatus === "CANCELED" ? "appointment_canceled" : "request_status_changed";
  }

  if (hasAppointmentScheduleChanged(beforeData, afterData)) {
    return "appointment_rescheduled";
  }

  return "";
}

function hasAppointmentScheduleChanged(beforeData, afterData) {
  const beforeEpochMillis = resolveComparableAppointmentEpoch(beforeData);
  const afterEpochMillis = resolveComparableAppointmentEpoch(afterData);
  if (beforeEpochMillis !== null && afterEpochMillis !== null) {
    return beforeEpochMillis !== afterEpochMillis;
  }

  return sanitizeText(beforeData?.appointmentAt) !== sanitizeText(afterData?.appointmentAt) ||
      sanitizeText(beforeData?.appointmentDateKey) !== sanitizeText(afterData?.appointmentDateKey);
}

function resolveComparableAppointmentEpoch(appointmentData) {
  const appointmentDate = resolveAppointmentDate(appointmentData);
  if (!appointmentDate) {
    return null;
  }

  const epochMillis = appointmentDate.getTime();
  return Number.isFinite(epochMillis) ? epochMillis : null;
}

async function skipPendingReminderJobsForAppointment(firestore, appointmentRequestId, skipReason) {
  const reminderJobSnapshot = await firestore.collection("appointmentReminderJobs")
      .where("appointmentRequestId", "==", appointmentRequestId)
      .get();

  const matchedDocuments = reminderJobSnapshot.docs.filter((documentSnapshot) =>
    REMINDER_CLEANUP_STATES.has(sanitizeText(documentSnapshot.get("state"))),
  );

  if (matchedDocuments.length === 0) {
    return {
      appointmentRequestId,
      skipReason,
      scannedJobs: reminderJobSnapshot.size,
      skippedJobs: 0,
    };
  }

  const batch = firestore.batch();
  matchedDocuments.forEach((documentSnapshot) => {
    batch.update(documentSnapshot.ref, {
      state: REMINDER_SKIPPED_STATE,
      skipReason,
      skippedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
      lastError: "",
    });
  });
  await batch.commit();

  return {
    appointmentRequestId,
    skipReason,
    scannedJobs: reminderJobSnapshot.size,
    skippedJobs: matchedDocuments.length,
  };
}

function resolveAppointmentDate(appointmentData) {
  const epochMillis = appointmentData?.appointmentAtEpochMillis;
  if (typeof epochMillis === "number" && Number.isFinite(epochMillis)) {
    return new Date(epochMillis);
  }

  const rawAppointmentAt = appointmentData?.appointmentAt;
  if (rawAppointmentAt instanceof Timestamp) {
    return rawAppointmentAt.toDate();
  }
  if (rawAppointmentAt instanceof Date && !Number.isNaN(rawAppointmentAt.getTime())) {
    return rawAppointmentAt;
  }
  if (typeof rawAppointmentAt === "number" && Number.isFinite(rawAppointmentAt)) {
    return new Date(rawAppointmentAt);
  }
  if (typeof rawAppointmentAt !== "string") {
    return null;
  }

  return parseAppointmentText(rawAppointmentAt.trim());
}

function parseAppointmentText(value) {
  if (!value) {
    return null;
  }

  const normalizedMatch = value.match(/^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})$/);
  if (normalizedMatch) {
    const [, year, month, day, hour, minute] = normalizedMatch;
    return new Date(`${year}-${month}-${day}T${hour}:${minute}:00+09:00`);
  }

  const parsedDate = new Date(value);
  if (Number.isNaN(parsedDate.getTime())) {
    return null;
  }
  return parsedDate;
}

function normalizeProfilePhone(value) {
  const normalizedValue = sanitizeText(value);
  if (!normalizedValue) {
    return "";
  }

  let digits = normalizedValue.replace(/\D+/g, "");
  if (!digits) {
    return "";
  }

  if (digits.startsWith("82") && digits.length >= 11) {
    digits = `0${digits.slice(2)}`;
  }
  return digits;
}

function normalizeComparableEmail(value) {
  return sanitizeText(value).toLowerCase();
}

function sanitizeText(value) {
  return typeof value === "string" ? value.trim() : "";
}

module.exports = {
  syncLinkedAppointmentParticipants,
  cleanupAppointmentReminderJobs,
};
