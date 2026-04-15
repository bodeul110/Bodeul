const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {onDocumentWritten} = require("firebase-functions/v2/firestore");
const {onSchedule} = require("firebase-functions/v2/scheduler");
const logger = require("firebase-functions/logger");
const {initializeApp} = require("firebase-admin/app");
const {getAuth} = require("firebase-admin/auth");
const {FieldValue, Timestamp, getFirestore} = require("firebase-admin/firestore");

initializeApp();

const HTTP_FUNCTIONS_OPTIONS = {
  region: "asia-northeast3",
  cors: true,
  invoker: "public",
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

const USER_LINK_SYNC_OPTIONS = {
  region: "asia-northeast3",
  document: "users/{userId}",
};

const APPOINTMENT_REQUEST_SYNC_OPTIONS = {
  region: "asia-northeast3",
  document: "appointmentRequests/{appointmentRequestId}",
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
const REMINDER_DELIVERY_BATCH_SIZE = 20;

exports.kakaoCustomToken = onCall(HTTP_FUNCTIONS_OPTIONS, async (request) => {
  const accessToken = `${request.data?.accessToken ?? ""}`.trim();
  const role = `${request.data?.role ?? ""}`.trim();

  // 역할 선택 이후에만 계정을 만들 수 있게 서버에서 한 번 더 제한한다.
  if (!CLIENT_CREATABLE_ROLES.has(role)) {
    throw invalidArgument("허용되지 않은 사용자 역할입니다.");
  }

  if (!accessToken) {
    throw invalidArgument("카카오 access token이 필요합니다.");
  }

  const kakaoProfile = await fetchKakaoProfile(accessToken);
  const providerUserId = `${kakaoProfile.id ?? ""}`.trim();
  if (!providerUserId) {
    throw unauthenticated("카카오 사용자 정보를 확인하지 못했습니다.");
  }

  const firebaseToken = await getAuth().createCustomToken(`kakao_${providerUserId}`);
  return {
    firebaseToken,
    profile: {
      providerUserId,
      name: extractKakaoName(kakaoProfile),
      email: extractKakaoEmail(kakaoProfile, providerUserId),
      phone: extractKakaoPhone(kakaoProfile),
    },
  };
});

exports.naverCustomToken = onCall(HTTP_FUNCTIONS_OPTIONS, async (request) => {
  const accessToken = `${request.data?.accessToken ?? ""}`.trim();
  const role = `${request.data?.role ?? ""}`.trim();

  if (!CLIENT_CREATABLE_ROLES.has(role)) {
    throw invalidArgument("허용되지 않은 사용자 역할입니다.");
  }

  if (!accessToken) {
    throw invalidArgument("네이버 access token이 필요합니다.");
  }

  const naverProfileResponse = await fetchNaverProfile(accessToken);
  const providerUserId = `${naverProfileResponse?.response?.id ?? ""}`.trim();
  if (!providerUserId) {
    throw unauthenticated("네이버 사용자 정보를 확인하지 못했습니다.");
  }

  const firebaseToken = await getAuth().createCustomToken(`naver_${providerUserId}`);
  return {
    firebaseToken,
    profile: {
      providerUserId,
      name: extractNaverName(naverProfileResponse),
      email: extractNaverEmail(naverProfileResponse, providerUserId),
      phone: extractNaverPhone(naverProfileResponse),
    },
  };
});

// 매일 오전 9시에 예약 일정을 스캔해서 D-7, D-3, D-1 알림 작업 문서를 만든다.
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
exports.dispatchAppointmentReminderJobs = onCall(HTTP_FUNCTIONS_OPTIONS, async (request) => {
  await assertAdminCaller(request);
  const batchSize = sanitizeBatchSize(request.data?.batchSize);
  const summary = await processAppointmentReminderJobs({
    source: "manual",
    batchSize,
  });
  return summary;
});

// 사용자 계정이 생성되거나 연락처가 갱신되면 기존 신청 문서와 자동으로 다시 연결한다.
exports.syncLinkedAppointmentParticipants = onDocumentWritten(
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

// 예약 취소, 삭제, 일정 변경 직후 남아 있는 알림 작업을 즉시 정리한다.
exports.cleanupAppointmentReminderJobs = onDocumentWritten(
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
