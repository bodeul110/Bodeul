const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {onSchedule} = require("firebase-functions/v2/scheduler");
const logger = require("firebase-functions/logger");
const {initializeApp} = require("firebase-admin/app");
const {FieldPath, FieldValue, Timestamp, getFirestore} = require("firebase-admin/firestore");
const {getMessaging} = require("firebase-admin/messaging");

initializeApp();

const HTTP_FUNCTIONS_OPTIONS = {
  region: "asia-northeast3",
  cors: true,
  invoker: "public",
};

const APP_CHECK_ENFORCEMENT_ENABLED =
  `${process.env.ENABLE_APPCHECK_ENFORCEMENT ?? ""}`.trim() === "true";

const CALLABLE_FUNCTIONS_OPTIONS = {
  ...HTTP_FUNCTIONS_OPTIONS,
  // App Check 클라이언트 배포가 끝날 때까지 기본값은 끄고, 환경 변수로만 강제한다.
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

const CLIENT_SUPPORT_NOTIFICATION_OPTIONS = {
  region: "asia-northeast3",
  document: "clientSupportRequests/{supportRequestId}",
};
const COMPANION_CHAT_NOTIFICATION_OPTIONS = {
  region: "asia-northeast3",
  document: "companionSessions/{sessionId}",
};
const COMPANION_LOCATION_ALERT_NOTIFICATION_OPTIONS = {
  region: "asia-northeast3",
  document: "companionSessions/{sessionId}",
};
const CLIENT_SUPPORT_REMINDER_SCHEDULE_OPTIONS = {
  region: "asia-northeast3",
  schedule: "0 * * * *",
  timeZone: "Asia/Seoul",
};
const NOTIFICATION_TOKEN_CLEANUP_SCHEDULE_OPTIONS = {
  region: "asia-northeast3",
  schedule: "30 4 * * *",
  timeZone: "Asia/Seoul",
};

const CLIENT_CREATABLE_ROLES = new Set(["PATIENT", "GUARDIAN", "MANAGER"]);
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
const REMINDER_CLEANUP_STATES = new Set([
  REMINDER_PENDING_STATE,
  REMINDER_PROCESSING_STATE,
  REMINDER_FAILED_STATE,
]);
const SEOUL_TIME_ZONE = "Asia/Seoul";
const DAY_IN_MILLIS = 24 * 60 * 60 * 1000;
const CLIENT_SUPPORT_RESPONSE_REMINDER_THRESHOLD_MILLIS = DAY_IN_MILLIS;
const CLIENT_SUPPORT_RESPONSE_REMINDER_INTERVAL_MILLIS = DAY_IN_MILLIS;
const CLIENT_SUPPORT_RESPONSE_REMINDER_MAX_COUNT = 3;
const NOTIFICATION_TOKEN_STALE_THRESHOLD_MILLIS = 60 * DAY_IN_MILLIS;
const NOTIFICATION_TOKEN_CLEANUP_BATCH_SIZE = 200;
const REMINDER_DELIVERY_BATCH_SIZE = 20;
Object.assign(exports, require("./src/auth"));
Object.assign(exports, require("./src/notifications"));
Object.assign(exports, require("./src/action-delivery"));
Object.assign(exports, require("./src/sync"));

exports.syncAppointmentReminderJobs = onSchedule(REMINDER_SYNC_SCHEDULE_OPTIONS, async (event) => {
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

  logger.info("예약 알림 작업 동기화를 마쳤습니다.", {
    scannedRequests: appointmentSnapshot.size,
    createdJobs,
    alreadyPreparedJobs,
    skippedRequests,
    scheduleTime: scheduleTime.toISOString(),
  });
});

// 10분 단위로 보류 중인 알림 작업을 발송한다.
exports.deliverAppointmentReminderJobs = onSchedule(
    REMINDER_DELIVERY_SCHEDULE_OPTIONS,
    async () => {
      const summary = await processAppointmentReminderJobs({
        source: REMINDER_SOURCE_DELIVERY,
        batchSize: REMINDER_DELIVERY_BATCH_SIZE,
      });
      logger.info("예약 알림 발송 배치를 마쳤습니다.", summary);
    },
);

// 관리자 계정으로 수동 발송을 실행할 수 있게 열어둔다.
exports.dispatchAppointmentReminderJobs = onCall(CALLABLE_FUNCTIONS_OPTIONS, async (request) => {
  await assertAdminCaller(request);
  const batchSize = sanitizeBatchSize(request.data?.batchSize);
  const summary = await processAppointmentReminderJobs({
    source: "manual",
    batchSize,
  });
  return summary;
});

// 5분 단위로 관리자 후속 알림 푸시 큐를 처리한다.
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
    headers.Authorization = deliveryConfig.authScheme
        ? `${deliveryConfig.authScheme} ${deliveryConfig.apiKey}`
        : deliveryConfig.apiKey;
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

async function fetchKakaoProfile(accessToken) {
  const response = await fetch("https://kapi.kakao.com/v2/user/me", {
    method: "GET",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/x-www-form-urlencoded;charset=utf-8",
    },
  });

  let responseBody = {};
  try {
    responseBody = await response.json();
  } catch (error) {
    logger.error("카카오 응답 JSON 파싱 실패", error);
  }

  if (!response.ok) {
    logger.error("카카오 사용자 정보 조회 실패", {
      status: response.status,
      body: responseBody,
    });
    throw unauthenticated("카카오 로그인 정보가 유효하지 않거나 만료되었습니다.");
  }

  return responseBody;
}

async function fetchNaverProfile(accessToken) {
  const response = await fetch("https://openapi.naver.com/v1/nid/me", {
    method: "GET",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
  });

  let responseBody = {};
  try {
    responseBody = await response.json();
  } catch (error) {
    logger.error("네이버 응답 JSON 파싱 실패", error);
  }

  if (!response.ok) {
    logger.error("네이버 사용자 정보 조회 실패", {
      status: response.status,
      body: responseBody,
    });
    throw unauthenticated("네이버 로그인 정보가 유효하지 않거나 만료되었습니다.");
  }

  if (`${responseBody?.resultcode ?? ""}` !== "00") {
    logger.error("네이버 사용자 정보 응답 오류", responseBody);
    throw unauthenticated("네이버 사용자 정보를 확인하지 못했습니다.");
  }

  return responseBody;
}

function extractKakaoName(kakaoProfile) {
  const nickname = kakaoProfile?.kakao_account?.profile?.nickname;
  if (typeof nickname === "string" && nickname.trim()) {
    return nickname.trim();
  }
  return "카카오 사용자";
}

function extractKakaoEmail(kakaoProfile, providerUserId) {
  const email = kakaoProfile?.kakao_account?.email;
  if (typeof email === "string" && email.trim()) {
    return email.trim();
  }

  // 이메일 동의 항목이 비어 있을 때 모델이 깨지지 않도록 대체 메일을 사용한다.
  return `kakao_${providerUserId}@bodeul.local`;
}

function extractKakaoPhone(kakaoProfile) {
  const phone = kakaoProfile?.kakao_account?.phone_number;
  if (typeof phone === "string" && phone.trim()) {
    return phone.trim();
  }
  return "";
}

function extractNaverName(naverProfileResponse) {
  const name = naverProfileResponse?.response?.name;
  if (typeof name === "string" && name.trim()) {
    return name.trim();
  }

  const nickname = naverProfileResponse?.response?.nickname;
  if (typeof nickname === "string" && nickname.trim()) {
    return nickname.trim();
  }
  return "네이버 사용자";
}

function extractNaverEmail(naverProfileResponse, providerUserId) {
  const email = naverProfileResponse?.response?.email;
  if (typeof email === "string" && email.trim()) {
    return email.trim();
  }

  return `naver_${providerUserId}@bodeul.local`;
}

function extractNaverPhone(naverProfileResponse) {
  const mobileE164 = naverProfileResponse?.response?.mobile_e164;
  if (typeof mobileE164 === "string" && mobileE164.trim()) {
    return mobileE164.trim();
  }

  const mobile = naverProfileResponse?.response?.mobile;
  if (typeof mobile === "string" && mobile.trim()) {
    return mobile.trim();
  }
  return "";
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
    return `${visitLabel} 예약이 7일 남았습니다. ${appointmentLabel} 방문 전 신분증과 준비 서류를 미리 확인해 주세요.`;
  }
  if (stageKey === "D3") {
    return `${visitLabel} 예약이 3일 남았습니다. 보호자 연락처, 만남 장소, 이동 경로를 다시 확인해 주세요.`;
  }
  return `${visitLabel} 예약이 하루 남았습니다. ${appointmentLabel} 일정과 만남 장소를 최종 확인해 주세요.`;
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

function normalizeProfilePhone(value) {
  const digits = normalizePhoneNumber(value);
  if (!digits) {
    return "";
  }
  if (digits.length === 11) {
    return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
  }
  if (digits.length === 10 && digits.startsWith("02")) {
    return `${digits.slice(0, 2)}-${digits.slice(2, 6)}-${digits.slice(6)}`;
  }
  if (digits.length === 10) {
    return `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}`;
  }
  if (digits.length === 9 && digits.startsWith("02")) {
    return `${digits.slice(0, 2)}-${digits.slice(2, 5)}-${digits.slice(5)}`;
  }
  return digits;
}

function normalizeComparableEmail(value) {
  return sanitizeText(value).toLowerCase();
}

function toStringArray(value) {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
      .map((item) => sanitizeText(item))
      .filter(Boolean);
}

function collectInvalidNotificationTokens(tokens, responses) {
  if (!Array.isArray(tokens) || !Array.isArray(responses)) {
    return [];
  }

  const invalidTokens = [];
  for (let index = 0; index < tokens.length; index += 1) {
    const response = responses[index];
    const errorCode = sanitizeText(response?.error?.code);
    if (isInvalidNotificationTokenError(errorCode)) {
      invalidTokens.push(tokens[index]);
    }
  }
  return Array.from(new Set(invalidTokens));
}

function isInvalidNotificationTokenError(errorCode) {
  return errorCode === "messaging/invalid-registration-token" ||
      errorCode === "messaging/registration-token-not-registered";
}

function toNotificationTokenEntryMap(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return {};
  }

  const normalizedEntries = {};
  for (const [entryKey, entryValue] of Object.entries(value)) {
    const token = sanitizeText(entryValue?.token);
    if (!token) {
      continue;
    }
    normalizedEntries[entryKey] = {
      token,
      platform: sanitizeText(entryValue?.platform),
      updatedAtMillis: toSafeInteger(entryValue?.updatedAtMillis),
    };
  }
  return normalizedEntries;
}

function buildNotificationTokenEntryKey(token) {
  return Buffer.from(sanitizeText(token), "utf8").toString("base64url");
}

function buildNotificationTokenEntryDeleteUpdates(tokens) {
  const updates = {};
  for (const token of toStringArray(tokens)) {
    updates[`notificationTokenEntries.${buildNotificationTokenEntryKey(token)}`] = FieldValue.delete();
  }
  return updates;
}

function collectOrphanNotificationTokenEntryKeys(tokens, tokenEntries) {
  const tokenSet = new Set(toStringArray(tokens));
  const orphanKeys = [];
  for (const [entryKey, entryValue] of Object.entries(tokenEntries)) {
    if (!tokenSet.has(entryValue.token)) {
      orphanKeys.push(entryKey);
    }
  }
  return orphanKeys;
}

function resolveStaleNotificationTokens({
  tokens,
  tokenEntries,
  globalUpdatedAtMillis,
  nowMillis,
}) {
  const staleTokens = [];
  for (const token of toStringArray(tokens)) {
    const entryKey = buildNotificationTokenEntryKey(token);
    const entryUpdatedAtMillis = toSafeInteger(tokenEntries[entryKey]?.updatedAtMillis);
    const lastSeenAtMillis = entryUpdatedAtMillis > 0 ? entryUpdatedAtMillis : globalUpdatedAtMillis;
    if (lastSeenAtMillis <= 0) {
      continue;
    }
    if (nowMillis - lastSeenAtMillis >= NOTIFICATION_TOKEN_STALE_THRESHOLD_MILLIS) {
      staleTokens.push(token);
    }
  }
  return Array.from(new Set(staleTokens));
}

async function removeInvalidNotificationTokens(userDocumentReference, invalidTokens) {
  if (!userDocumentReference || !Array.isArray(invalidTokens) || invalidTokens.length === 0) {
    return;
  }

  const updates = {
    notificationTokens: FieldValue.arrayRemove(...invalidTokens),
    updatedAt: FieldValue.serverTimestamp(),
    ...buildNotificationTokenEntryDeleteUpdates(invalidTokens),
  };

  await userDocumentReference.update(updates);
}

async function processStaleNotificationTokenCleanup() {
  const firestore = getFirestore();
  const nowMillis = Date.now();
  const summary = {
    scannedUsers: 0,
    touchedUsers: 0,
    removedTokens: 0,
    removedOrphanEntries: 0,
  };

  let lastDocument = null;
  while (true) {
    let query = firestore.collection("users")
        .orderBy(FieldPath.documentId())
        .limit(NOTIFICATION_TOKEN_CLEANUP_BATCH_SIZE);
    if (lastDocument) {
      query = query.startAfter(lastDocument);
    }

    const userSnapshot = await query.get();
    if (userSnapshot.empty) {
      break;
    }

    for (const userDocument of userSnapshot.docs) {
      summary.scannedUsers++;
      const cleanupSummary = await cleanupUserNotificationTokens(userDocument, nowMillis);
      if (!cleanupSummary.touched) {
        continue;
      }
      summary.touchedUsers++;
      summary.removedTokens += cleanupSummary.removedTokenCount;
      summary.removedOrphanEntries += cleanupSummary.removedOrphanEntryCount;
    }

    lastDocument = userSnapshot.docs[userSnapshot.docs.length - 1];
    if (userSnapshot.size < NOTIFICATION_TOKEN_CLEANUP_BATCH_SIZE) {
      break;
    }
  }

  return summary;
}

async function cleanupUserNotificationTokens(userDocument, nowMillis) {
  const tokens = toStringArray(userDocument.get("notificationTokens"));
  const tokenEntries = toNotificationTokenEntryMap(userDocument.get("notificationTokenEntries"));
  const globalUpdatedAtMillis = resolveFirestoreTimestampMillis(
      userDocument.get("notificationTokenUpdatedAt"),
  );
  const staleTokens = resolveStaleNotificationTokens({
    tokens,
    tokenEntries,
    globalUpdatedAtMillis,
    nowMillis,
  });
  const orphanEntryKeys = collectOrphanNotificationTokenEntryKeys(tokens, tokenEntries);

  if (staleTokens.length === 0 && orphanEntryKeys.length === 0) {
    return {
      touched: false,
      removedTokenCount: 0,
      removedOrphanEntryCount: 0,
    };
  }

  const updates = {
    updatedAt: FieldValue.serverTimestamp(),
  };
  if (staleTokens.length > 0) {
    updates.notificationTokens = FieldValue.arrayRemove(...staleTokens);
  }
  for (const staleToken of staleTokens) {
    updates[`notificationTokenEntries.${buildNotificationTokenEntryKey(staleToken)}`] = FieldValue.delete();
  }
  for (const orphanEntryKey of orphanEntryKeys) {
    updates[`notificationTokenEntries.${orphanEntryKey}`] = FieldValue.delete();
  }

  const remainingTokens = tokens.filter((token) => !staleTokens.includes(token));
  if (remainingTokens.length === 0) {
    updates.notificationTokenUpdatedAt = FieldValue.delete();
    updates.notificationTokenPlatform = FieldValue.delete();
  }

  await userDocument.ref.update(updates);
  return {
    touched: true,
    removedTokenCount: staleTokens.length,
    removedOrphanEntryCount: orphanEntryKeys.length,
  };
}

async function processClientSupportAnswerReminders() {
  const firestore = getFirestore();
  const nowMillis = Date.now();
  const supportSnapshot = await firestore.collection("clientSupportRequests")
      .where("status", "==", "ANSWERED")
      .get();

  const summary = {
    scannedRequests: supportSnapshot.size,
    dueRequests: 0,
    sentRequests: 0,
    skippedRequests: 0,
    invalidTokenCount: 0,
  };

  for (const supportDocument of supportSnapshot.docs) {
    const supportData = supportDocument.data();
    if (!shouldSendClientSupportReminder(supportData, nowMillis)) {
      continue;
    }

    summary.dueRequests++;
    const categoryLabel = resolveClientSupportCategoryLabel(supportData.category);
    const requestTitle = sanitizeText(supportData.title) || "?? ??";
    const deliverySummary = await sendClientSupportNotification({
      supportRequestId: supportDocument.id,
      supportData,
      title: "?? ?? ??? ?? ???? ?????",
      body: `${categoryLabel} ? ${requestTitle}`,
    });

    summary.invalidTokenCount += deliverySummary.invalidTokenCount;
    if (!deliverySummary.sent) {
      summary.skippedRequests++;
      continue;
    }

    await supportDocument.ref.update({
      responseReminderCount: FieldValue.increment(1),
      responseReminderSentAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    summary.sentRequests++;
  }

  return summary;
}

function toSafeInteger(value) {
  const numericValue = Number(value);
  if (!Number.isFinite(numericValue)) {
    return 0;
  }
  return Math.max(0, Math.trunc(numericValue));
}

function resolveLatestCompanionChatMessage(beforeData, afterData) {
  const beforeMessages = toCompanionChatMessageArray(beforeData?.chatMessages);
  const afterMessages = toCompanionChatMessageArray(afterData?.chatMessages);
  if (afterMessages.length <= beforeMessages.length) {
    return null;
  }

  const latestMessage = afterMessages[afterMessages.length - 1];
  if (!latestMessage) {
    return null;
  }

  const beforeMessageKeys = new Set(beforeMessages.map(buildCompanionChatMessageKey));
  if (beforeMessageKeys.has(buildCompanionChatMessageKey(latestMessage))) {
    return null;
  }
  return latestMessage;
}

function toCompanionChatMessageArray(value) {
  if (!Array.isArray(value)) {
    return [];
  }

  return value.map((item) => ({
    senderRole: sanitizeText(item?.senderRole),
    body: sanitizeText(item?.body),
    sentAtMillis: toSafeInteger(item?.sentAtMillis),
    attachments: toCompanionChatAttachmentArray(item?.attachments, item?.attachment),
    attachmentFullPath: sanitizeText(item?.attachment?.fullPath),
    attachmentFileName: sanitizeText(item?.attachment?.fileName),
    attachmentContentType: sanitizeText(item?.attachment?.contentType),
  })).filter((item) =>
    item.senderRole && (
      item.body ||
      item.attachments.length > 0 ||
      item.attachmentFullPath ||
      item.attachmentFileName
    )
  );
}

function buildCompanionChatMessageKey(message) {
  return [
    sanitizeText(message?.senderRole),
    sanitizeText(message?.body),
    toSafeInteger(message?.sentAtMillis),
    ...toCompanionChatAttachmentArray(message?.attachments, {
      fullPath: message?.attachmentFullPath,
      fileName: message?.attachmentFileName,
      contentType: message?.attachmentContentType,
    }).map((attachment) => [
      sanitizeText(attachment?.fullPath),
      sanitizeText(attachment?.fileName),
      sanitizeText(attachment?.contentType),
    ].join(":")),
  ].join("|");
}

function toCompanionChatAttachmentArray(rawAttachments, rawAttachment) {
  const attachments = [];
  if (Array.isArray(rawAttachments)) {
    rawAttachments.forEach((item) => {
      const fullPath = sanitizeText(item?.fullPath);
      const fileName = sanitizeText(item?.fileName);
      const contentType = sanitizeText(item?.contentType);
      if (!fullPath && !fileName) {
        return;
      }
      attachments.push({fullPath, fileName, contentType});
    });
  }
  if (attachments.length > 0) {
    return attachments;
  }
  const fullPath = sanitizeText(rawAttachment?.fullPath);
  const fileName = sanitizeText(rawAttachment?.fileName);
  const contentType = sanitizeText(rawAttachment?.contentType);
  if (!fullPath && !fileName) {
    return [];
  }
  return [{fullPath, fileName, contentType}];
}

function resolveCompanionChatRecipientUserIds(senderRole, requestData, managerUserId) {
  const patientUserId = sanitizeText(requestData?.patientUserId);
  const guardianUserId = sanitizeText(requestData?.guardianUserId);
  const normalizedSenderRole = sanitizeText(senderRole);
  const recipientUserIds = [];

  if (normalizedSenderRole !== "PATIENT" && patientUserId) {
    recipientUserIds.push(patientUserId);
  }
  if (normalizedSenderRole !== "GUARDIAN" && guardianUserId) {
    recipientUserIds.push(guardianUserId);
  }
  if (normalizedSenderRole !== "MANAGER" && managerUserId) {
    recipientUserIds.push(managerUserId);
  }

  return Array.from(new Set(recipientUserIds));
}

function resolveCompanionChatNotificationTitle(senderRole) {
  const normalizedSenderRole = sanitizeText(senderRole);
  if (normalizedSenderRole === "MANAGER") {
    return "매니저가 안심 채팅을 보냈습니다";
  }
  if (normalizedSenderRole === "PATIENT") {
    return "환자가 안심 채팅을 보냈습니다";
  }
  if (normalizedSenderRole === "GUARDIAN") {
    return "보호자가 안심 채팅을 보냈습니다";
  }
  return "안심 채팅 새 메시지가 도착했습니다";
}

function buildCompanionChatNotificationBody(message) {
  const normalizedBody = sanitizeText(message?.body);
  if (!normalizedBody) {
    const attachments = toCompanionChatAttachmentArray(message?.attachments, {
      fullPath: message?.attachmentFullPath,
      fileName: message?.attachmentFileName,
      contentType: message?.attachmentContentType,
    });
    if (attachments.length > 1) {
      return `첨부 파일 ${attachments.length}개를 보냈습니다.`;
    }
    const contentType = sanitizeText(attachments[0]?.contentType);
    if (contentType === "application/pdf") {
      return "PDF ??? ?????.";
    }
    if (contentType.startsWith("image/")) {
      return "??? ??? ?????.";
    }
    return "?? ??? ?????.";
  }
  if (normalizedBody.length <= 120) {
    return normalizedBody;
  }
  return `${normalizedBody.slice(0, 117)}...`;
}

function shouldNotifyCompanionLocationAlert(beforeData, afterData) {
  const afterStage = sanitizeText(afterData?.locationAlertStage);
  if (!afterStage || afterStage === "none") {
    return false;
  }
  const beforeStage = sanitizeText(beforeData?.locationAlertStage);
  const beforeSentAt = resolveFirestoreTimestampMillis(beforeData?.locationAlertSentAt);
  const afterSentAt = resolveFirestoreTimestampMillis(afterData?.locationAlertSentAt);
  return beforeStage !== afterStage || beforeSentAt !== afterSentAt;
}

function resolveCompanionLocationRecipientUserIds(requestData) {
  return Array.from(new Set([
    sanitizeText(requestData?.patientUserId),
    sanitizeText(requestData?.guardianUserId),
  ].filter(Boolean)));
}

function resolveCompanionLocationAlertTitle(alertStage) {
  if (sanitizeText(alertStage) === "pharmacy_near") {
    return "매니저가 약국에 도착했습니다";
  }
  return "매니저가 병원에 도착했습니다";
}

function buildCompanionLocationAlertBody(alertStage, requestData) {
  const hospitalName = sanitizeText(requestData?.hospitalName);
  if (sanitizeText(alertStage) === "pharmacy_near") {
    if (hospitalName) {
      return `${hospitalName} 인근 약국 도착을 확인했습니다.`;
    }
    return "인근 약국 도착을 확인했습니다.";
  }
  if (hospitalName) {
    return `${hospitalName} 도착을 확인했습니다.`;
  }
  return "병원 도착을 확인했습니다.";
}

async function sendCompanionChatNotification({
  sessionId,
  appointmentRequestId,
  recipientUserIds,
  latestMessage,
  title,
  body,
}) {
  const summary = {
    successCount: 0,
    failureCount: 0,
    invalidTokenCount: 0,
  };

  for (const userId of recipientUserIds) {
    const deliverySummary = await sendCompanionChatNotificationToUser({
      userId,
      sessionId,
      appointmentRequestId,
      latestMessage,
      title,
      body,
    });
    summary.successCount += deliverySummary.successCount;
    summary.failureCount += deliverySummary.failureCount;
    summary.invalidTokenCount += deliverySummary.invalidTokenCount;
  }

  return summary;
}

async function sendCompanionChatNotificationToUser({
  userId,
  sessionId,
  appointmentRequestId,
  latestMessage,
  title,
  body,
}) {
  const userSnapshot = await getFirestore().collection("users").doc(userId).get();
  if (!userSnapshot.exists) {
    return {
      successCount: 0,
      failureCount: 0,
      invalidTokenCount: 0,
    };
  }

  const notificationTokens = toStringArray(userSnapshot.get("notificationTokens"));
  if (notificationTokens.length === 0) {
    return {
      successCount: 0,
      failureCount: 0,
      invalidTokenCount: 0,
    };
  }

  const response = await getMessaging().sendEachForMulticast({
    tokens: notificationTokens,
    data: {
      type: "companion_chat_message",
      sessionId: sanitizeText(sessionId),
      appointmentRequestId: sanitizeText(appointmentRequestId),
      senderRole: sanitizeText(latestMessage?.senderRole),
      sentAtMillis: `${toSafeInteger(latestMessage?.sentAtMillis)}`,
      title: sanitizeText(title),
      body: sanitizeText(body),
    },
    android: {
      priority: "high",
    },
  });

  const invalidTokens = collectInvalidNotificationTokens(
      notificationTokens,
      response.responses,
  );
  if (invalidTokens.length > 0) {
    await removeInvalidNotificationTokens(userSnapshot.ref, invalidTokens);
  }

  return {
    successCount: response.successCount,
    failureCount: response.failureCount,
    invalidTokenCount: invalidTokens.length,
  };
}

async function sendCompanionLocationAlertNotification({
  sessionId,
  appointmentRequestId,
  alertStage,
  recipientUserIds,
  title,
  body,
}) {
  const summary = {
    successCount: 0,
    failureCount: 0,
    invalidTokenCount: 0,
  };

  for (const userId of recipientUserIds) {
    const deliverySummary = await sendCompanionLocationAlertNotificationToUser({
      userId,
      sessionId,
      appointmentRequestId,
      alertStage,
      title,
      body,
    });
    summary.successCount += deliverySummary.successCount;
    summary.failureCount += deliverySummary.failureCount;
    summary.invalidTokenCount += deliverySummary.invalidTokenCount;
  }

  return summary;
}

async function sendCompanionLocationAlertNotificationToUser({
  userId,
  sessionId,
  appointmentRequestId,
  alertStage,
  title,
  body,
}) {
  const userSnapshot = await getFirestore().collection("users").doc(userId).get();
  if (!userSnapshot.exists) {
    return {
      successCount: 0,
      failureCount: 0,
      invalidTokenCount: 0,
    };
  }

  const notificationTokens = toStringArray(userSnapshot.get("notificationTokens"));
  if (notificationTokens.length === 0) {
    return {
      successCount: 0,
      failureCount: 0,
      invalidTokenCount: 0,
    };
  }

  const response = await getMessaging().sendEachForMulticast({
    tokens: notificationTokens,
    notification: {
      title,
      body,
    },
    data: {
      type: "companion_location_alert",
      sessionId: sanitizeText(sessionId),
      appointmentRequestId: sanitizeText(appointmentRequestId),
      alertStage: sanitizeText(alertStage),
      title: sanitizeText(title),
      body: sanitizeText(body),
    },
    android: {
      priority: "high",
    },
  });

  const invalidTokens = collectInvalidNotificationTokens(
      notificationTokens,
      response.responses,
  );
  if (invalidTokens.length > 0) {
    await removeInvalidNotificationTokens(userSnapshot.ref, invalidTokens);
  }

  return {
    successCount: response.successCount,
    failureCount: response.failureCount,
    invalidTokenCount: invalidTokens.length,
  };
}

function shouldNotifyClientSupportAnswer(beforeData, afterData) {
  const afterStatus = sanitizeText(afterData?.status);
  const afterResponse = sanitizeText(afterData?.responseText);
  if (afterStatus !== "ANSWERED" || !afterResponse) {
    return false;
  }

  if (afterData?.responseReadByUser === true) {
    return false;
  }

  const beforeStatus = sanitizeText(beforeData?.status);
  const beforeResponse = sanitizeText(beforeData?.responseText);
  const beforeRespondedAt = resolveFirestoreTimestampMillis(beforeData?.respondedAt);
  const afterRespondedAt = resolveFirestoreTimestampMillis(afterData?.respondedAt);

  return beforeStatus !== afterStatus
      || beforeResponse !== afterResponse
      || beforeRespondedAt !== afterRespondedAt;
}

function shouldSendClientSupportReminder(supportData, nowMillis) {
  if (sanitizeText(supportData?.status) !== "ANSWERED") {
    return false;
  }
  if (supportData?.responseReadByUser === true) {
    return false;
  }
  if (!sanitizeText(supportData?.responseText)) {
    return false;
  }

  const respondedAtMillis = resolveFirestoreTimestampMillis(supportData?.respondedAt);
  if (respondedAtMillis <= 0 ||
      nowMillis - respondedAtMillis < CLIENT_SUPPORT_RESPONSE_REMINDER_THRESHOLD_MILLIS) {
    return false;
  }

  const reminderCount = toSafeInteger(supportData?.responseReminderCount);
  if (reminderCount >= CLIENT_SUPPORT_RESPONSE_REMINDER_MAX_COUNT) {
    return false;
  }

  const reminderSentAtMillis = resolveFirestoreTimestampMillis(supportData?.responseReminderSentAt);
  if (reminderSentAtMillis > 0 &&
      nowMillis - reminderSentAtMillis < CLIENT_SUPPORT_RESPONSE_REMINDER_INTERVAL_MILLIS) {
    return false;
  }

  return true;
}

async function sendClientSupportNotification({
  supportRequestId,
  supportData,
  title,
  body,
}) {
  const userId = sanitizeText(supportData?.userId);
  if (!userId) {
    return buildClientSupportDeliverySummary({skippedReason: "user_missing"});
  }

  const userSnapshot = await getFirestore().collection("users").doc(userId).get();
  if (!userSnapshot.exists) {
    return buildClientSupportDeliverySummary({userId, skippedReason: "user_document_missing"});
  }

  const notificationTokens = toStringArray(userSnapshot.get("notificationTokens"));
  if (notificationTokens.length === 0) {
    return buildClientSupportDeliverySummary({userId, skippedReason: "notification_token_missing"});
  }

  const response = await getMessaging().sendEachForMulticast({
    tokens: notificationTokens,
    notification: {
      title,
      body,
    },
    data: {
      type: "client_support_answered",
      supportRequestId: sanitizeText(supportRequestId),
      appointmentRequestId: sanitizeText(supportData?.appointmentRequestId),
      status: sanitizeText(supportData?.status),
    },
    android: {
      priority: "high",
    },
  });

  const invalidTokens = collectInvalidNotificationTokens(
      notificationTokens,
      response.responses,
  );
  if (invalidTokens.length > 0) {
    await removeInvalidNotificationTokens(userSnapshot.ref, invalidTokens);
  }

  return buildClientSupportDeliverySummary({
    userId,
    tokenCount: notificationTokens.length,
    successCount: response.successCount,
    failureCount: response.failureCount,
    invalidTokenCount: invalidTokens.length,
    sent: response.successCount > 0,
  });
}

function buildClientSupportDeliverySummary({
  userId = "",
  tokenCount = 0,
  successCount = 0,
  failureCount = 0,
  invalidTokenCount = 0,
  sent = false,
  skippedReason = "",
}) {
  return {
    userId,
    tokenCount,
    successCount,
    failureCount,
    invalidTokenCount,
    sent,
    skippedReason,
  };
}

function resolveClientSupportCategoryLabel(category) {
  const normalized = sanitizeText(category);
  if (normalized === "PAYMENT") {
    return "결제 문의";
  }
  if (normalized === "REPORT") {
    return "리포트 문의";
  }
  if (normalized === "ACCOUNT") {
    return "계정 문의";
  }
  if (normalized === "EMERGENCY") {
    return "긴급 문의";
  }
  return "예약 문의";
}

function resolveFirestoreTimestampMillis(value) {
  if (value instanceof Timestamp) {
    return value.toMillis();
  }
  if (value instanceof Date) {
    return value.getTime();
  }
  const numericValue = Number(value);
  if (Number.isFinite(numericValue)) {
    return numericValue;
  }
  return 0;
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

function sanitizeText(value) {
  return typeof value === "string" ? value.trim() : "";
}

function isAlreadyExistsError(error) {
  return error?.code === 6
      || error?.code === "already-exists"
      || `${error?.message ?? ""}`.includes("Already exists");
}

function invalidArgument(message) {
  return new HttpsError("invalid-argument", message, {message});
}

function unauthenticated(message) {
  return new HttpsError("unauthenticated", message, {message});
}
