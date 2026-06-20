const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {onSchedule} = require("firebase-functions/v2/scheduler");
const logger = require("firebase-functions/logger");
const {FieldValue, Timestamp, getFirestore} = require("firebase-admin/firestore");

const HTTP_FUNCTIONS_OPTIONS = {
  region: "asia-northeast3",
  cors: true,
  invoker: "public",
};

const APP_CHECK_ENFORCEMENT_ENABLED =
  `${process.env.ENABLE_APPCHECK_ENFORCEMENT ?? ""}`.trim() === "true";

const CALLABLE_FUNCTIONS_OPTIONS = {
  ...HTTP_FUNCTIONS_OPTIONS,
  enforceAppCheck: APP_CHECK_ENFORCEMENT_ENABLED,
};

const REMINDER_SYNC_SCHEDULE_OPTIONS = {
  region: "asia-northeast3",
  schedule: "0 9 * * *",
  timeZone: "Asia/Seoul",
};

const REMINDER_DELIVERY_SCHEDULE_OPTIONS = {
  region: "asia-northeast3",
  schedule: "*/10 * * * *",
  timeZone: "Asia/Seoul",
};

const REMINDER_SCAN_STATUSES = new Set(["REQUESTED", "MATCHED"]);
const REMINDER_RETRYABLE_STATES = new Set(["PENDING", "FAILED"]);
const REMINDER_STAGES = Object.freeze([
  {key: "D7", daysBefore: 7, templateKey: "appointment_d7"},
  {key: "D3", daysBefore: 3, templateKey: "appointment_d3"},
  {key: "D1", daysBefore: 1, templateKey: "appointment_d1"},
]);
const REMINDER_CHANNEL = "KAKAO_ALIMTALK";
const REMINDER_SOURCE_SYNC = "scheduler";
const REMINDER_SOURCE_DELIVERY = "delivery";
const REMINDER_PENDING_STATE = "PENDING";
const REMINDER_PROCESSING_STATE = "PROCESSING";
const REMINDER_SENT_STATE = "SENT";
const REMINDER_SIMULATED_STATE = "SIMULATED";
const REMINDER_SKIPPED_STATE = "SKIPPED";
const REMINDER_FAILED_STATE = "FAILED";
const SEOUL_TIME_ZONE = "Asia/Seoul";
const DAY_IN_MILLIS = 24 * 60 * 60 * 1000;
const REMINDER_DELIVERY_BATCH_SIZE = 20;

const syncAppointmentReminderJobs = onSchedule(REMINDER_SYNC_SCHEDULE_OPTIONS, async (event) => {
  const firestore = getFirestore();
  const scheduleTime = event.scheduleTime ? new Date(event.scheduleTime) : new Date();
  const appointmentSnapshot = await firestore.collection("appointmentRequests")
      .where("status", "in", Array.from(REMINDER_SCAN_STATUSES))
      .get();

  let createdJobs = 0;
  let skippedRequests = 0;
  let alreadyPreparedJobs = 0;

  for (const appointmentDocument of appointmentSnapshot.docs) {
    const appointmentData = appointmentDocument.data();
    const appointmentDate = resolveAppointmentDate(appointmentData);
    if (!appointmentDate) {
      skippedRequests++;
      logger.warn("예약 시각을 해석하지 못해 알림 작업 생성을 건너뜁니다.", {
        appointmentRequestId: appointmentDocument.id,
        appointmentAt: appointmentData.appointmentAt ?? null,
      });
      continue;
    }

    const stage = findReminderStage(diffSeoulCalendarDays(scheduleTime, appointmentDate));
    if (!stage) {
      continue;
    }

    const reminderDateKey = formatDateKey(scheduleTime);
    const reminderJobId = buildReminderJobId(
        appointmentDocument.id,
        stage.key,
        reminderDateKey,
    );

    try {
      await firestore.collection("appointmentReminderJobs")
          .doc(reminderJobId)
          .create(buildReminderJobDocument({
            appointmentRequestId: appointmentDocument.id,
            appointmentData,
            appointmentDate,
            scheduleTime,
            stage,
          }));
      createdJobs++;
    } catch (error) {
      if (isAlreadyExistsError(error)) {
        alreadyPreparedJobs++;
        continue;
      }
      throw error;
    }
  }

  logger.info("예약 알림 작업 동기를 마쳤습니다.", {
    scannedRequests: appointmentSnapshot.size,
    createdJobs,
    alreadyPreparedJobs,
    skippedRequests,
    scheduleTime: scheduleTime.toISOString(),
  });
});

const deliverAppointmentReminderJobs = onSchedule(
    REMINDER_DELIVERY_SCHEDULE_OPTIONS,
    async () => {
      const summary = await processAppointmentReminderJobs({
        source: REMINDER_SOURCE_DELIVERY,
        batchSize: REMINDER_DELIVERY_BATCH_SIZE,
      });
      logger.info("예약 알림 발송 배치를 마쳤습니다.", summary);
    },
);

const dispatchAppointmentReminderJobs = onCall(CALLABLE_FUNCTIONS_OPTIONS, async (request) => {
  await assertAdminCaller(request);
  const batchSize = sanitizeBatchSize(request.data?.batchSize);
  return processAppointmentReminderJobs({
    source: "manual",
    batchSize,
  });
});

async function processAppointmentReminderJobs({source, batchSize}) {
  const firestore = getFirestore();
  const reminderJobSnapshot = await firestore.collection("appointmentReminderJobs")
      .where("state", "in", Array.from(REMINDER_RETRYABLE_STATES))
      .limit(batchSize)
      .get();

  const summary = {
    fetchedJobs: reminderJobSnapshot.size,
    claimedJobs: 0,
    sentJobs: 0,
    simulatedJobs: 0,
    skippedJobs: 0,
    failedJobs: 0,
  };

  for (const reminderJobDocument of reminderJobSnapshot.docs) {
    const reminderJob = await claimReminderJob(firestore, reminderJobDocument.ref, source);
    if (!reminderJob) {
      continue;
    }

    summary.claimedJobs++;
    const resultState = await deliverReminderJob(firestore, reminderJob, source);
    if (resultState === REMINDER_SENT_STATE) {
      summary.sentJobs++;
    } else if (resultState === REMINDER_SIMULATED_STATE) {
      summary.simulatedJobs++;
    } else if (resultState === REMINDER_SKIPPED_STATE) {
      summary.skippedJobs++;
    } else if (resultState === REMINDER_FAILED_STATE) {
      summary.failedJobs++;
    }
  }

  return summary;
}

async function claimReminderJob(firestore, reminderJobRef, source) {
  return firestore.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(reminderJobRef);
    if (!snapshot.exists) {
      return null;
    }

    const reminderJob = snapshot.data();
    const state = sanitizeText(reminderJob.state);
    if (!REMINDER_RETRYABLE_STATES.has(state)) {
      return null;
    }

    const deliveryAttempts = toSafeInteger(reminderJob.deliveryAttempts) + 1;
    transaction.update(reminderJobRef, {
      state: REMINDER_PROCESSING_STATE,
      deliveryAttempts,
      lastDeliverySource: source,
      claimedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
      lastError: "",
    });

    return {
      id: snapshot.id,
      ...reminderJob,
      state: REMINDER_PROCESSING_STATE,
      deliveryAttempts,
    };
  });
}

async function deliverReminderJob(firestore, reminderJob, source) {
  try {
    const appointmentSnapshot = await firestore.collection("appointmentRequests")
        .doc(reminderJob.appointmentRequestId)
        .get();
    if (!appointmentSnapshot.exists) {
      await markReminderJobSkipped(firestore, reminderJob.id, "appointment_missing");
      return REMINDER_SKIPPED_STATE;
    }

    const appointmentData = appointmentSnapshot.data();
    const skipReason = resolveReminderSkipReason(reminderJob, appointmentData, new Date());
    if (skipReason) {
      await markReminderJobSkipped(firestore, reminderJob.id, skipReason);
      return REMINDER_SKIPPED_STATE;
    }

    const recipientPhones = await resolveRecipientPhones(firestore, reminderJob, appointmentData);
    if (recipientPhones.length === 0) {
      await markReminderJobSkipped(firestore, reminderJob.id, "recipient_phone_missing");
      return REMINDER_SKIPPED_STATE;
    }

    const deliveryConfig = getReminderDeliveryConfig();
    const deliveryPayload = buildReminderDeliveryPayload(
        reminderJob,
        appointmentData,
        recipientPhones,
    );

    if (!deliveryConfig.isConfigured) {
      logger.info("카카오 알림톡 연동값이 없어 시뮬레이션으로 처리합니다.", {
        reminderJobId: reminderJob.id,
        appointmentRequestId: reminderJob.appointmentRequestId,
        reminderStage: reminderJob.reminderStage,
        recipientPhones,
      });
      await markReminderJobSimulated(
          firestore,
          reminderJob.id,
          recipientPhones,
          source,
          deliveryPayload,
      );
      return REMINDER_SIMULATED_STATE;
    }

    const providerResult = await sendReminderToProvider(deliveryConfig, deliveryPayload);
    await markReminderJobSent(
        firestore,
        reminderJob.id,
        recipientPhones,
        source,
        providerResult,
    );
    return REMINDER_SENT_STATE;
  } catch (error) {
    logger.error("예약 알림 발송 중 오류가 발생했습니다.", {
      reminderJobId: reminderJob.id,
      appointmentRequestId: reminderJob.appointmentRequestId,
      message: `${error?.message ?? error}`,
    });
    await markReminderJobFailed(firestore, reminderJob.id, error);
    return REMINDER_FAILED_STATE;
  }
}

function resolveReminderSkipReason(reminderJob, appointmentData, now) {
  const requestStatus = sanitizeText(appointmentData.status);
  if (!REMINDER_SCAN_STATUSES.has(requestStatus)) {
    return "request_status_changed";
  }

  const appointmentDate = resolveAppointmentDate(appointmentData);
  if (!appointmentDate) {
    return "appointment_time_invalid";
  }

  const expectedStage = findReminderStage(diffSeoulCalendarDays(now, appointmentDate));
  if (!expectedStage) {
    return "reminder_window_missed";
  }

  if (expectedStage.key !== sanitizeText(reminderJob.reminderStage)) {
    return "reminder_stage_changed";
  }

  return "";
}

async function resolveRecipientPhones(firestore, reminderJob, appointmentData) {
  const candidateUserIds = Array.from(new Set([
    ...toStringArray(reminderJob.recipientUserIds),
    sanitizeText(appointmentData.patientUserId),
    sanitizeText(appointmentData.guardianUserId),
    sanitizeText(appointmentData.requesterUserId),
  ].filter(Boolean)));

  const phoneNumbers = new Set();
  await Promise.all(candidateUserIds.map(async (userId) => {
    const userSnapshot = await firestore.collection("users").doc(userId).get();
    if (!userSnapshot.exists) {
      return;
    }
    const normalizedPhone = normalizePhoneNumber(userSnapshot.get("phone"));
    if (normalizedPhone) {
      phoneNumbers.add(normalizedPhone);
    }
  }));

  const fallbackRequesterPhone = normalizePhoneNumber(appointmentData.requesterPhone);
  if (phoneNumbers.size === 0 && fallbackRequesterPhone) {
    phoneNumbers.add(fallbackRequesterPhone);
  }

  return Array.from(phoneNumbers);
}

function buildReminderDeliveryPayload(reminderJob, appointmentData, recipientPhones) {
  return {
    senderKey: getReminderDeliveryConfig().senderKey,
    templateKey: sanitizeText(reminderJob.templateKey),
    message: sanitizeText(reminderJob.messagePreview),
    recipients: recipientPhones.map((phoneNumber) => ({phoneNumber})),
    metadata: {
      reminderJobId: reminderJob.id,
      appointmentRequestId: reminderJob.appointmentRequestId,
      reminderStage: sanitizeText(reminderJob.reminderStage),
      appointmentAt: sanitizeText(appointmentData.appointmentAt),
      hospitalName: sanitizeText(appointmentData.hospitalName),
      departmentName: sanitizeText(appointmentData.departmentName),
      meetingPlace: sanitizeText(appointmentData.meetingPlace),
    },
  };
}

async function sendReminderToProvider(deliveryConfig, deliveryPayload) {
  const response = await fetch(deliveryConfig.endpoint, {
    method: "POST",
    headers: buildReminderDeliveryHeaders(deliveryConfig),
    body: JSON.stringify(deliveryPayload),
  });

  const responseBody = await parseHttpResponse(response);
  if (!response.ok) {
    throw new Error(`알림톡 발송 실패: ${response.status} ${stringifyResponseBody(responseBody)}`);
  }

  return {
    status: response.status,
    body: shrinkResponseBody(responseBody),
  };
}

function buildReminderDeliveryHeaders(deliveryConfig) {
  const headers = {
    "Content-Type": "application/json",
    "X-Bodeul-Sender-Key": deliveryConfig.senderKey,
  };

  if (deliveryConfig.apiKey) {
    headers.Authorization = deliveryConfig.authScheme ?
      `${deliveryConfig.authScheme} ${deliveryConfig.apiKey}` :
      deliveryConfig.apiKey;
  }
  return headers;
}

async function parseHttpResponse(response) {
  const text = await response.text();
  if (!text) {
    return {};
  }

  try {
    return JSON.parse(text);
  } catch (error) {
    return {raw: text};
  }
}

async function markReminderJobSent(firestore, reminderJobId, recipientPhones, source, providerResult) {
  await firestore.collection("appointmentReminderJobs").doc(reminderJobId).update({
    state: REMINDER_SENT_STATE,
    sentAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
    deliveredBy: source,
    recipientPhones,
    providerResult,
    lastError: "",
  });
}

async function markReminderJobSimulated(firestore, reminderJobId, recipientPhones, source, deliveryPayload) {
  await firestore.collection("appointmentReminderJobs").doc(reminderJobId).update({
    state: REMINDER_SIMULATED_STATE,
    simulatedAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
    deliveredBy: source,
    recipientPhones,
    simulatedPayload: {
      templateKey: deliveryPayload.templateKey,
      message: deliveryPayload.message,
      recipients: deliveryPayload.recipients,
    },
    lastError: "",
  });
}

async function markReminderJobSkipped(firestore, reminderJobId, skipReason) {
  await firestore.collection("appointmentReminderJobs").doc(reminderJobId).update({
    state: REMINDER_SKIPPED_STATE,
    skipReason,
    skippedAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
    lastError: "",
  });
}

async function markReminderJobFailed(firestore, reminderJobId, error) {
  await firestore.collection("appointmentReminderJobs").doc(reminderJobId).update({
    state: REMINDER_FAILED_STATE,
    failedAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
    lastError: `${error?.message ?? error}`.slice(0, 500),
  });
}

function getReminderDeliveryConfig() {
  const endpoint = sanitizeText(process.env.KAKAO_ALIMTALK_ENDPOINT);
  const apiKey = sanitizeText(process.env.KAKAO_ALIMTALK_API_KEY);
  const senderKey = sanitizeText(process.env.KAKAO_ALIMTALK_SENDER_KEY);
  const authScheme = sanitizeText(process.env.KAKAO_ALIMTALK_AUTH_SCHEME) || "Bearer";

  return {
    endpoint,
    apiKey,
    senderKey,
    authScheme,
    isConfigured: Boolean(endpoint && apiKey && senderKey),
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

function diffSeoulCalendarDays(baseDate, targetDate) {
  const baseDay = toUtcDay(formatDateKey(baseDate));
  const targetDay = toUtcDay(formatDateKey(targetDate));
  return Math.round((targetDay - baseDay) / DAY_IN_MILLIS);
}

function findReminderStage(daysBefore) {
  return REMINDER_STAGES.find((stage) => stage.daysBefore === daysBefore) ?? null;
}

function buildReminderJobId(appointmentRequestId, stageKey, reminderDateKey) {
  return `${appointmentRequestId}_${stageKey}_${reminderDateKey.replace(/-/g, "")}`;
}

function buildReminderJobDocument({
  appointmentRequestId,
  appointmentData,
  appointmentDate,
  scheduleTime,
  stage,
}) {
  return {
    appointmentRequestId,
    reminderStage: stage.key,
    daysBefore: stage.daysBefore,
    templateKey: stage.templateKey,
    channel: REMINDER_CHANNEL,
    state: REMINDER_PENDING_STATE,
    source: REMINDER_SOURCE_SYNC,
    reminderDateKey: formatDateKey(scheduleTime),
    appointmentDateKey: formatDateKey(appointmentDate),
    appointmentAt: sanitizeText(appointmentData.appointmentAt) || formatAppointmentLabel(appointmentDate),
    appointmentAtEpochMillis: appointmentDate.getTime(),
    requestStatus: sanitizeText(appointmentData.status),
    hospitalName: sanitizeText(appointmentData.hospitalName),
    departmentName: sanitizeText(appointmentData.departmentName),
    meetingPlace: sanitizeText(appointmentData.meetingPlace),
    specialNotes: sanitizeText(appointmentData.specialNotes),
    patientUserId: sanitizeText(appointmentData.patientUserId),
    guardianUserId: sanitizeText(appointmentData.guardianUserId),
    managerUserId: sanitizeText(appointmentData.managerUserId),
    requesterUserId: sanitizeText(appointmentData.requesterUserId),
    requesterRole: sanitizeText(appointmentData.requesterRole),
    requesterName: sanitizeText(appointmentData.requesterName),
    requesterPhone: sanitizeText(appointmentData.requesterPhone),
    recipientUserIds: buildRecipientUserIds(appointmentData),
    messagePreview: buildReminderMessagePreview(stage.key, appointmentData, appointmentDate),
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
    deliveryAttempts: 0,
  };
}

function buildRecipientUserIds(appointmentData) {
  return Array.from(new Set([
    sanitizeText(appointmentData.patientUserId),
    sanitizeText(appointmentData.guardianUserId),
    sanitizeText(appointmentData.requesterUserId),
  ].filter(Boolean)));
}

function buildReminderMessagePreview(stageKey, appointmentData, appointmentDate) {
  const visitLabel = buildVisitLabel(appointmentData);
  const appointmentLabel = formatAppointmentLabel(appointmentDate);

  if (stageKey === "D7") {
    return `${visitLabel} 예약이 7일 앞으로 다가옵니다. ${appointmentLabel} 방문 전 신분증과 준비 서류를 미리 확인해 주세요.`;
  }
  if (stageKey === "D3") {
    return `${visitLabel} 예약이 3일 앞으로 다가옵니다. 보호자 연락처, 만남 장소, 이동 경로를 다시 확인해 주세요.`;
  }
  return `${visitLabel} 예약이 하루 앞으로 다가옵니다. ${appointmentLabel} 일정과 만남 장소를 최종 확인해 주세요.`;
}

function buildVisitLabel(appointmentData) {
  const hospitalName = sanitizeText(appointmentData.hospitalName);
  const departmentName = sanitizeText(appointmentData.departmentName);
  if (hospitalName && departmentName) {
    return `${hospitalName} ${departmentName}`;
  }
  if (hospitalName) {
    return hospitalName;
  }
  return "병원 방문";
}

function formatAppointmentLabel(date) {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: SEOUL_TIME_ZONE,
    month: "long",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}

function formatDateKey(date) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: SEOUL_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date);
}

function toUtcDay(dateKey) {
  const [year, month, day] = dateKey.split("-").map(Number);
  return Date.UTC(year, month - 1, day);
}

function normalizePhoneNumber(value) {
  const digits = `${value ?? ""}`.replace(/\D/g, "");
  if (!digits) {
    return "";
  }
  if (digits.startsWith("82")) {
    return `0${digits.slice(2)}`;
  }
  return digits;
}

function toStringArray(value) {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
      .map((item) => sanitizeText(item))
      .filter(Boolean);
}

function toSafeInteger(value) {
  const numericValue = Number(value);
  if (!Number.isFinite(numericValue)) {
    return 0;
  }
  return Math.max(0, Math.trunc(numericValue));
}

function stringifyResponseBody(responseBody) {
  if (typeof responseBody === "string") {
    return responseBody.slice(0, 300);
  }
  return JSON.stringify(responseBody).slice(0, 300);
}

function shrinkResponseBody(responseBody) {
  if (typeof responseBody === "string") {
    return responseBody.slice(0, 500);
  }
  return JSON.parse(JSON.stringify(responseBody)).body ?? responseBody;
}

function sanitizeBatchSize(value) {
  const numericValue = Number(value);
  if (!Number.isFinite(numericValue)) {
    return REMINDER_DELIVERY_BATCH_SIZE;
  }
  return Math.min(Math.max(Math.trunc(numericValue), 1), 100);
}

async function assertAdminCaller(request) {
  const uid = sanitizeText(request.auth?.uid);
  if (!uid) {
    throw unauthenticated("관리자 계정으로 로그인해 주세요.");
  }

  const userSnapshot = await getFirestore().collection("users").doc(uid).get();
  if (!userSnapshot.exists || sanitizeText(userSnapshot.get("role")) !== "ADMIN") {
    throw new HttpsError(
        "permission-denied",
        "관리자만 알림 발송을 수동 실행할 수 있습니다.",
    );
  }
}

function sanitizeText(value) {
  return typeof value === "string" ? value.trim() : "";
}

function isAlreadyExistsError(error) {
  return error?.code === 6
      || error?.code === "already-exists"
      || `${error?.message ?? ""}`.includes("Already exists");
}

function unauthenticated(message) {
  return new HttpsError("unauthenticated", message, {message});
}

module.exports = {
  syncAppointmentReminderJobs,
  deliverAppointmentReminderJobs,
  dispatchAppointmentReminderJobs,
};
