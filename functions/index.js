const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {onDocumentWritten} = require("firebase-functions/v2/firestore");
const {onSchedule} = require("firebase-functions/v2/scheduler");
const logger = require("firebase-functions/logger");
const {initializeApp} = require("firebase-admin/app");
const {getAuth} = require("firebase-admin/auth");
const {FieldValue, Timestamp, getFirestore} = require("firebase-admin/firestore");
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

const ACTION_DELIVERY_SCHEDULE_OPTIONS = {
  region: "asia-northeast3",
  schedule: "*/5 * * * *",
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

const CLIENT_SUPPORT_NOTIFICATION_OPTIONS = {
  region: "asia-northeast3",
  document: "clientSupportRequests/{supportRequestId}",
};
const CLIENT_SUPPORT_REMINDER_SCHEDULE_OPTIONS = {
  region: "asia-northeast3",
  schedule: "0 * * * *",
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
const REMINDER_DELIVERY_BATCH_SIZE = 20;
const ACTION_DELIVERY_RETRYABLE_STATES = new Set(["PENDING", "FAILED"]);
const ACTION_DELIVERY_PENDING_STATE = "PENDING";
const ACTION_DELIVERY_PROCESSING_STATE = "PROCESSING";
const ACTION_DELIVERY_SENT_STATE = "SENT";
const ACTION_DELIVERY_SIMULATED_STATE = "SIMULATED";
const ACTION_DELIVERY_SKIPPED_STATE = "SKIPPED";
const ACTION_DELIVERY_FAILED_STATE = "FAILED";
const ACTION_DELIVERY_CHANNEL_PUSH = "app_push";
const ACTION_DELIVERY_BATCH_SIZE = 20;

exports.kakaoCustomToken = onCall(CALLABLE_FUNCTIONS_OPTIONS, async (request) => {
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

exports.naverCustomToken = onCall(CALLABLE_FUNCTIONS_OPTIONS, async (request) => {
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

exports.resolveLinkedParticipant = onCall(CALLABLE_FUNCTIONS_OPTIONS, async (request) => {
  const uid = sanitizeText(request.auth?.uid);
  if (!uid) {
    throw unauthenticated("로그인이 필요합니다.");
  }

  const callerSnapshot = await getFirestore().collection("users").doc(uid).get();
  const callerRole = sanitizeText(callerSnapshot.get("role"));
  if (!["PATIENT", "GUARDIAN", "ADMIN"].includes(callerRole)) {
    throw new HttpsError("permission-denied", "연결 계정 조회 권한이 없습니다.");
  }

  const expectedRole = sanitizeText(request.data?.expectedRole);
  if (!["PATIENT", "GUARDIAN"].includes(expectedRole)) {
    throw invalidArgument("연결할 사용자 유형이 올바르지 않습니다.");
  }

  const email = normalizeComparableEmail(request.data?.email);
  const phone = normalizeProfilePhone(request.data?.phone);
  if (!email && !phone) {
    return {participant: null};
  }

  const firestore = getFirestore();
  let matchedUser = null;
  if (email) {
    const emailSnapshot = await firestore.collection("users")
        .where("role", "==", expectedRole)
        .where("email", "==", email)
        .limit(1)
        .get();
    matchedUser = emailSnapshot.docs.length > 0 ? emailSnapshot.docs[0] : null;
  }

  if (!matchedUser && phone) {
    const phoneSnapshot = await firestore.collection("users")
        .where("role", "==", expectedRole)
        .where("phone", "==", phone)
        .limit(1)
        .get();
    matchedUser = phoneSnapshot.docs.length > 0 ? phoneSnapshot.docs[0] : null;
  }

  if (!matchedUser) {
    return {participant: null};
  }

  return {
    participant: {
      userId: matchedUser.id,
      role: sanitizeText(matchedUser.get("role")),
      name: sanitizeText(matchedUser.get("name")),
      email: sanitizeText(matchedUser.get("email")),
      phone: sanitizeText(matchedUser.get("phone")),
    },
  };
});

exports.findSocialDuplicateEmailProvider = onCall(CALLABLE_FUNCTIONS_OPTIONS, async (request) => {
  const uid = sanitizeText(request.auth?.uid);
  if (!uid) {
    throw unauthenticated("로그인이 필요합니다.");
  }

  const signInProvider = sanitizeText(request.auth?.token?.firebase?.sign_in_provider);
  if (!signInProvider || signInProvider === "password") {
    throw new HttpsError("permission-denied", "소셜 로그인 계정만 중복 확인을 요청할 수 있습니다.");
  }

  const email = normalizeComparableEmail(request.data?.email);
  if (!email) {
    throw invalidArgument("이메일이 필요합니다.");
  }

  const duplicateSnapshot = await getFirestore().collection("users")
      .where("email", "==", email)
      .limit(5)
      .get();

  const duplicateDocument = duplicateSnapshot.docs.find((documentSnapshot) =>
    documentSnapshot.id !== uid,
  );

  if (!duplicateDocument) {
    return {duplicate: null};
  }

  return {
    duplicate: {
      userId: duplicateDocument.id,
      provider: sanitizeText(duplicateDocument.get("provider")) || "EMAIL",
    },
  };
});

exports.resolveAssignedManagerProfile = onCall(CALLABLE_FUNCTIONS_OPTIONS, async (request) => {
  const uid = sanitizeText(request.auth?.uid);
  if (!uid) {
    throw unauthenticated("로그인이 필요합니다.");
  }

  const requestId = sanitizeText(request.data?.requestId);
  if (!requestId) {
    throw invalidArgument("예약 요청 ID가 필요합니다.");
  }

  const firestore = getFirestore();
  const callerSnapshot = await firestore.collection("users").doc(uid).get();
  const callerRole = sanitizeText(callerSnapshot.get("role"));
  if (!["PATIENT", "GUARDIAN", "MANAGER", "ADMIN"].includes(callerRole)) {
    throw new HttpsError("permission-denied", "매니저 정보를 조회할 권한이 없습니다.");
  }

  const requestSnapshot = await firestore.collection("appointmentRequests").doc(requestId).get();
  if (!requestSnapshot.exists) {
    throw invalidArgument("예약 요청 정보를 확인하지 못했습니다.");
  }

  const patientUserId = sanitizeText(requestSnapshot.get("patientUserId"));
  const guardianUserId = sanitizeText(requestSnapshot.get("guardianUserId"));
  const managerUserId = sanitizeText(requestSnapshot.get("managerUserId"));
  const isParticipant = uid === patientUserId || uid === guardianUserId || uid === managerUserId;
  if (!isParticipant && callerRole !== "ADMIN") {
    throw new HttpsError("permission-denied", "매니저 정보를 조회할 권한이 없습니다.");
  }

  if (!managerUserId) {
    return {manager: null};
  }

  const managerSnapshot = await firestore.collection("users").doc(managerUserId).get();
  if (!managerSnapshot.exists || sanitizeText(managerSnapshot.get("role")) !== "MANAGER") {
    return {manager: null};
  }

  return {
    manager: {
      userId: managerSnapshot.id,
      role: "MANAGER",
      name: sanitizeText(managerSnapshot.get("name")),
      email: sanitizeText(managerSnapshot.get("email")),
      phone: sanitizeText(managerSnapshot.get("phone")),
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
exports.deliverAdminActionDeliveryJobs = onSchedule(
    ACTION_DELIVERY_SCHEDULE_OPTIONS,
    async () => {
      const summary = await processAdminActionDeliveryJobs({
        source: "delivery",
        batchSize: ACTION_DELIVERY_BATCH_SIZE,
      });
      logger.info("관리자 후속 알림 전달 큐 처리를 마쳤습니다.", summary);
    },
);

// 관리자 계정은 후속 알림 전달 큐를 수동으로 다시 돌릴 수 있다.
exports.dispatchAdminActionDeliveryJobs = onCall(CALLABLE_FUNCTIONS_OPTIONS, async (request) => {
  await assertAdminCaller(request);
  const batchSize = sanitizeBatchSize(request.data?.batchSize);
  return processAdminActionDeliveryJobs({
    source: "manual",
    batchSize,
  });
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

// 이용자 문의 문서가 답변 완료로 바뀌면 사용자 단말에 새 답변 알림을 보낸다.
exports.notifyClientSupportAnswered = onDocumentWritten(
    CLIENT_SUPPORT_NOTIFICATION_OPTIONS,
    async (event) => {
      if (!event.data?.after?.exists) {
        return;
      }

      const beforeData = event.data?.before?.exists ? event.data.before.data() : null;
      const afterData = event.data.after.data();
      if (!shouldNotifyClientSupportAnswer(beforeData, afterData)) {
        return;
      }

      const title = "?? ?? ??? ??????";
      const categoryLabel = resolveClientSupportCategoryLabel(afterData.category);
      const requestTitle = sanitizeText(afterData.title) || "?? ??";
      const body = `${categoryLabel} ? ${requestTitle}`;
      const deliverySummary = await sendClientSupportNotification({
        supportRequestId: sanitizeText(event.params?.supportRequestId),
        supportData: afterData,
        title,
        body,
      });

      logger.info("??? ?? ?? ??? ??????.", {
        supportRequestId: sanitizeText(event.params?.supportRequestId),
        userId: deliverySummary.userId,
        tokenCount: deliverySummary.tokenCount,
        successCount: deliverySummary.successCount,
        failureCount: deliverySummary.failureCount,
        invalidTokenCount: deliverySummary.invalidTokenCount,
        skippedReason: deliverySummary.skippedReason,
      });
    },
);

exports.sendClientSupportAnswerReminders = onSchedule(
    CLIENT_SUPPORT_REMINDER_SCHEDULE_OPTIONS,
    async () => {
      const summary = await processClientSupportAnswerReminders();
      logger.info("??? ?? ?? ??? ??? ?????.", summary);
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

async function processAdminActionDeliveryJobs({source, batchSize}) {
  const firestore = getFirestore();
  const actionDeliveryJobSnapshot = await firestore.collection("adminActionDeliveryJobs")
      .where("state", "in", Array.from(ACTION_DELIVERY_RETRYABLE_STATES))
      .limit(batchSize)
      .get();

  const summary = {
    fetchedJobs: actionDeliveryJobSnapshot.size,
    claimedJobs: 0,
    sentJobs: 0,
    simulatedJobs: 0,
    skippedJobs: 0,
    failedJobs: 0,
  };

  for (const actionDeliveryJobDocument of actionDeliveryJobSnapshot.docs) {
    const actionDeliveryJob = await claimAdminActionDeliveryJob(
        firestore,
        actionDeliveryJobDocument.ref,
        source,
    );
    if (!actionDeliveryJob) {
      continue;
    }

    summary.claimedJobs++;
    const resultState = await deliverAdminActionDeliveryJob(
        firestore,
        actionDeliveryJob,
        source,
    );
    if (resultState === ACTION_DELIVERY_SENT_STATE) {
      summary.sentJobs++;
    } else if (resultState === ACTION_DELIVERY_SIMULATED_STATE) {
      summary.simulatedJobs++;
    } else if (resultState === ACTION_DELIVERY_SKIPPED_STATE) {
      summary.skippedJobs++;
    } else if (resultState === ACTION_DELIVERY_FAILED_STATE) {
      summary.failedJobs++;
    }
  }

  return summary;
}

async function claimAdminActionDeliveryJob(firestore, actionDeliveryJobRef, source) {
  return firestore.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(actionDeliveryJobRef);
    if (!snapshot.exists) {
      return null;
    }

    const actionDeliveryJob = snapshot.data();
    const state = sanitizeText(actionDeliveryJob.state);
    if (!ACTION_DELIVERY_RETRYABLE_STATES.has(state)) {
      return null;
    }

    const deliveryAttempts = toSafeInteger(actionDeliveryJob.deliveryAttempts) + 1;
    transaction.update(actionDeliveryJobRef, {
      state: ACTION_DELIVERY_PROCESSING_STATE,
      deliveryAttempts,
      lastDeliverySource: source,
      claimedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
      lastError: "",
    });

    return {
      id: snapshot.id,
      ...actionDeliveryJob,
      state: ACTION_DELIVERY_PROCESSING_STATE,
      deliveryAttempts,
    };
  });
}

async function deliverAdminActionDeliveryJob(firestore, actionDeliveryJob, source) {
  try {
    if (sanitizeText(actionDeliveryJob.channel) !== ACTION_DELIVERY_CHANNEL_PUSH) {
      await markAdminActionDeliveryJobSkipped(
          firestore,
          actionDeliveryJob,
          null,
          source,
          "unsupported_channel",
          "앱 푸시 채널이 아닌 작업이라 전달 큐에서 생략했습니다.",
      );
      return ACTION_DELIVERY_SKIPPED_STATE;
    }

    const deliveryId = sanitizeText(actionDeliveryJob.deliveryId);
    const deliverySnapshot = deliveryId ?
      await firestore.collection("adminActionDeliveries").doc(deliveryId).get() :
      null;
    if (!deliverySnapshot?.exists) {
      await markAdminActionDeliveryJobSkipped(
          firestore,
          actionDeliveryJob,
          null,
          source,
          "delivery_missing",
          "연결된 전달 기록을 찾지 못해 작업을 종료했습니다.",
      );
      return ACTION_DELIVERY_SKIPPED_STATE;
    }

    const recipientProfiles = await resolveAdminActionDeliveryRecipients(
        firestore,
        actionDeliveryJob,
    );
    if (recipientProfiles.length === 0) {
      await markAdminActionDeliveryJobSkipped(
          firestore,
          actionDeliveryJob,
          deliverySnapshot,
          source,
          "admin_recipient_missing",
          "수신할 관리자 계정을 찾지 못해 푸시 발송을 생략했습니다.",
      );
      return ACTION_DELIVERY_SKIPPED_STATE;
    }

    const deliveryConfig = getAdminPushDeliveryConfig();
    const deliveryPayload = buildAdminActionDeliveryPayload(
        actionDeliveryJob,
        recipientProfiles,
    );

    if (!deliveryConfig.isConfigured) {
      await markAdminActionDeliveryJobSimulated(
          firestore,
          actionDeliveryJob,
          deliverySnapshot,
          recipientProfiles,
          source,
          deliveryPayload,
      );
      return ACTION_DELIVERY_SIMULATED_STATE;
    }

    const providerResult = await sendAdminActionDeliveryToProvider(
        deliveryConfig,
        deliveryPayload,
    );
    await markAdminActionDeliveryJobSent(
        firestore,
        actionDeliveryJob,
        deliverySnapshot,
        recipientProfiles,
        source,
        providerResult,
    );
    return ACTION_DELIVERY_SENT_STATE;
  } catch (error) {
    logger.error("관리자 후속 알림 푸시 처리 중 오류가 발생했습니다.", {
      actionDeliveryJobId: actionDeliveryJob.id,
      notificationId: sanitizeText(actionDeliveryJob.notificationId),
      deliveryId: sanitizeText(actionDeliveryJob.deliveryId),
      message: `${error?.message ?? error}`,
    });

    const deliveryId = sanitizeText(actionDeliveryJob.deliveryId);
    const deliverySnapshot = deliveryId ?
      await firestore.collection("adminActionDeliveries").doc(deliveryId).get() :
      null;
    await markAdminActionDeliveryJobFailed(
        firestore,
        actionDeliveryJob,
        deliverySnapshot?.exists ? deliverySnapshot : null,
        error,
    );
    return ACTION_DELIVERY_FAILED_STATE;
  }
}

async function resolveAdminActionDeliveryRecipients(firestore, actionDeliveryJob) {
  const candidateUserIds = toStringArray(actionDeliveryJob.recipientUserIds);
  const recipientProfiles = new Map();

  if (candidateUserIds.length > 0) {
    await Promise.all(candidateUserIds.map(async (userId) => {
      const userSnapshot = await firestore.collection("users").doc(userId).get();
      if (!userSnapshot.exists || sanitizeText(userSnapshot.get("role")) !== "ADMIN") {
        return;
      }
      recipientProfiles.set(userSnapshot.id, {
        userId: userSnapshot.id,
        name: sanitizeText(userSnapshot.get("name")) || "관리자",
        phone: normalizePhoneNumber(userSnapshot.get("phone")),
        email: sanitizeText(userSnapshot.get("email")),
      });
    }));
  } else {
    const adminSnapshot = await firestore.collection("users")
        .where("role", "==", "ADMIN")
        .get();
    for (const documentSnapshot of adminSnapshot.docs) {
      recipientProfiles.set(documentSnapshot.id, {
        userId: documentSnapshot.id,
        name: sanitizeText(documentSnapshot.get("name")) || "관리자",
        phone: normalizePhoneNumber(documentSnapshot.get("phone")),
        email: sanitizeText(documentSnapshot.get("email")),
      });
    }
  }

  return Array.from(recipientProfiles.values());
}

function buildAdminActionDeliveryPayload(actionDeliveryJob, recipientProfiles) {
  const title = sanitizeText(actionDeliveryJob.title);
  const body = sanitizeText(actionDeliveryJob.body) || sanitizeText(actionDeliveryJob.messagePreview);
  return {
    messageType: "ADMIN_ACTION_PUSH",
    title,
    body,
    recipients: recipientProfiles.map((recipientProfile) => ({
      userId: recipientProfile.userId,
      name: recipientProfile.name,
      phone: recipientProfile.phone,
      email: recipientProfile.email,
    })),
    metadata: {
      actionDeliveryJobId: actionDeliveryJob.id,
      notificationId: sanitizeText(actionDeliveryJob.notificationId),
      deliveryId: sanitizeText(actionDeliveryJob.deliveryId),
      sourceType: sanitizeText(actionDeliveryJob.sourceType),
      trigger: sanitizeText(actionDeliveryJob.trigger),
      requestId: sanitizeText(actionDeliveryJob.requestId),
      inquiryId: sanitizeText(actionDeliveryJob.inquiryId),
      targetLabel: sanitizeText(actionDeliveryJob.targetLabel),
    },
  };
}

async function sendAdminActionDeliveryToProvider(deliveryConfig, deliveryPayload) {
  const response = await fetch(deliveryConfig.endpoint, {
    method: "POST",
    headers: buildAdminActionDeliveryHeaders(deliveryConfig),
    body: JSON.stringify(deliveryPayload),
  });

  const responseBody = await parseHttpResponse(response);
  if (!response.ok) {
    throw new Error(`관리자 푸시 발송 실패: ${response.status} ${stringifyResponseBody(responseBody)}`);
  }

  return {
    status: response.status,
    body: shrinkResponseBody(responseBody),
  };
}

function buildAdminActionDeliveryHeaders(deliveryConfig) {
  const headers = {
    "Content-Type": "application/json",
  };

  if (deliveryConfig.apiKey) {
    headers.Authorization = deliveryConfig.authScheme ?
      `${deliveryConfig.authScheme} ${deliveryConfig.apiKey}` :
      deliveryConfig.apiKey;
  }
  return headers;
}

async function markAdminActionDeliveryJobSent(
    firestore,
    actionDeliveryJob,
    deliverySnapshot,
    recipientProfiles,
    source,
    providerResult,
) {
  const processedAt = new Date();
  await applyAdminActionDeliveryJobResult({
    firestore,
    actionDeliveryJob,
    deliverySnapshot,
    jobUpdates: {
      state: ACTION_DELIVERY_SENT_STATE,
      sentAt: FieldValue.serverTimestamp(),
      deliveredBy: source,
      recipientUserIds: recipientProfiles.map((recipientProfile) => recipientProfile.userId),
      recipientPhones: recipientProfiles
          .map((recipientProfile) => recipientProfile.phone)
          .filter(Boolean),
      recipientEmails: recipientProfiles
          .map((recipientProfile) => recipientProfile.email)
          .filter(Boolean),
      providerResult,
      lastError: "",
    },
    deliveryStatus: "sent",
    deliveryNote: `관리자 앱 푸시 발송을 완료했습니다. 수신 관리자 ${recipientProfiles.length}명`,
    processedAt,
    confirmedAt: null,
  });
}

async function markAdminActionDeliveryJobSimulated(
    firestore,
    actionDeliveryJob,
    deliverySnapshot,
    recipientProfiles,
    source,
    deliveryPayload,
) {
  const processedAt = new Date();
  await applyAdminActionDeliveryJobResult({
    firestore,
    actionDeliveryJob,
    deliverySnapshot,
    jobUpdates: {
      state: ACTION_DELIVERY_SIMULATED_STATE,
      simulatedAt: FieldValue.serverTimestamp(),
      deliveredBy: source,
      recipientUserIds: recipientProfiles.map((recipientProfile) => recipientProfile.userId),
      recipientPhones: recipientProfiles
          .map((recipientProfile) => recipientProfile.phone)
          .filter(Boolean),
      recipientEmails: recipientProfiles
          .map((recipientProfile) => recipientProfile.email)
          .filter(Boolean),
      simulatedPayload: {
        title: deliveryPayload.title,
        body: deliveryPayload.body,
        recipients: deliveryPayload.recipients,
      },
      lastError: "",
    },
    deliveryStatus: "sent",
    deliveryNote: `푸시 연동값이 없어 시뮬레이션으로 처리했습니다. 수신 관리자 ${recipientProfiles.length}명`,
    processedAt,
    confirmedAt: processedAt,
  });
}

async function markAdminActionDeliveryJobSkipped(
    firestore,
    actionDeliveryJob,
    deliverySnapshot,
    source,
    skipReason,
    deliveryNote,
) {
  const processedAt = new Date();
  await applyAdminActionDeliveryJobResult({
    firestore,
    actionDeliveryJob,
    deliverySnapshot,
    jobUpdates: {
      state: ACTION_DELIVERY_SKIPPED_STATE,
      skipReason,
      skippedAt: FieldValue.serverTimestamp(),
      deliveredBy: source,
      lastError: "",
    },
    deliveryStatus: "skipped",
    deliveryNote,
    processedAt,
    confirmedAt: null,
  });
}

async function markAdminActionDeliveryJobFailed(
    firestore,
    actionDeliveryJob,
    deliverySnapshot,
    error,
) {
  const processedAt = new Date();
  const maxAttempts = Math.max(
      toSafeInteger(actionDeliveryJob.maxAttempts),
      resolveActionDeliveryDefaultMaxAttempts(sanitizeText(actionDeliveryJob.channel)),
  );
  const exhausted = toSafeInteger(actionDeliveryJob.deliveryAttempts) >= maxAttempts;
  await applyAdminActionDeliveryJobResult({
    firestore,
    actionDeliveryJob,
    deliverySnapshot,
    jobUpdates: {
      state: ACTION_DELIVERY_FAILED_STATE,
      failedAt: FieldValue.serverTimestamp(),
      lastError: `${error?.message ?? error}`.slice(0, 500),
    },
    deliveryStatus: "failed",
    deliveryNote: exhausted ?
      "관리자 앱 푸시 발송이 반복 실패해 최대 재시도 횟수를 소진했습니다." :
      "관리자 앱 푸시 발송에 실패해 다음 재시도를 대기합니다.",
    processedAt,
    confirmedAt: null,
  });
}

async function applyAdminActionDeliveryJobResult({
  firestore,
  actionDeliveryJob,
  deliverySnapshot,
  jobUpdates,
  deliveryStatus,
  deliveryNote,
  processedAt,
  confirmedAt,
}) {
  const batch = firestore.batch();
  batch.update(
      firestore.collection("adminActionDeliveryJobs").doc(actionDeliveryJob.id),
      {
        ...jobUpdates,
        updatedAt: FieldValue.serverTimestamp(),
      },
  );

  if (deliverySnapshot?.exists) {
    batch.update(
        deliverySnapshot.ref,
        buildAdminActionDeliveryUpdateFields({
          deliverySnapshot,
          actionDeliveryJob,
          deliveryStatus,
          deliveryNote,
          processedAt,
          confirmedAt,
        }),
    );
  }

  await batch.commit();
}

function buildAdminActionDeliveryUpdateFields({
  deliverySnapshot,
  actionDeliveryJob,
  deliveryStatus,
  deliveryNote,
  processedAt,
  confirmedAt,
}) {
  const deliveryData = deliverySnapshot.data() ?? {};
  const createdAtMillis = resolveFirestoreTimestampMillis(deliveryData.createdAt) || Date.now();
  const processedAtMillis = processedAt ? processedAt.getTime() : 0;
  const confirmedAtMillis = confirmedAt ? confirmedAt.getTime() : 0;
  const contractFields = buildActionDeliveryContractFields({
    sourceType: (sanitizeText(deliveryData.sourceType) ||
      sanitizeText(actionDeliveryJob.sourceType)).toLowerCase(),
    channel: sanitizeText(deliveryData.channel) || sanitizeText(actionDeliveryJob.channel),
    status: deliveryStatus,
    createdAtMillis,
    processedAtMillis,
    attemptCount: Math.max(toSafeInteger(actionDeliveryJob.deliveryAttempts), 1),
    maxAttemptCount: Math.max(
        toSafeInteger(actionDeliveryJob.maxAttempts),
        resolveActionDeliveryDefaultMaxAttempts(sanitizeText(actionDeliveryJob.channel)),
    ),
    confirmedAtMillis,
    slaDueAtMillis: resolveFirestoreTimestampMillis(deliveryData.slaDueAt),
  });

  return {
    status: deliveryStatus,
    note: deliveryNote,
    processedAt,
    ...contractFields,
  };
}

function buildActionDeliveryContractFields({
  sourceType,
  channel,
  status,
  createdAtMillis,
  processedAtMillis,
  attemptCount,
  maxAttemptCount,
  confirmedAtMillis,
  slaDueAtMillis,
}) {
  const resolvedConfirmedAtMillis = confirmedAtMillis ||
      resolveActionDeliveryConfirmedAt(status, processedAtMillis);
  const resolvedSlaDueAtMillis = slaDueAtMillis ||
      resolveActionDeliverySlaDueAt(
          sourceType,
          channel,
          status,
          createdAtMillis,
          processedAtMillis,
      );
  const resolvedNextRetryAtMillis = resolveActionDeliveryNextRetryAt(
      channel,
      status,
      attemptCount,
      maxAttemptCount,
      processedAtMillis,
  );
  const state = resolveActionDeliveryState({
    channel,
    status,
    confirmedAtMillis: resolvedConfirmedAtMillis,
    nextRetryAtMillis: resolvedNextRetryAtMillis,
    slaDueAtMillis: resolvedSlaDueAtMillis,
    nowMillis: Date.now(),
  });
  const priority = resolveActionDeliveryPriority(sourceType, state);
  const filterKeys = resolveActionDeliveryFilterKeys(state);
  const slaStatus = resolveActionDeliverySlaStatus(state);

  return {
    state,
    priority,
    filterKeys,
    slaStatus,
    attemptCount,
    maxAttemptCount,
    confirmedAt: resolvedConfirmedAtMillis > 0 ?
      Timestamp.fromMillis(resolvedConfirmedAtMillis) :
      FieldValue.delete(),
    nextRetryAt: resolvedNextRetryAtMillis > 0 ?
      Timestamp.fromMillis(resolvedNextRetryAtMillis) :
      FieldValue.delete(),
    slaDueAt: resolvedSlaDueAtMillis > 0 ?
      Timestamp.fromMillis(resolvedSlaDueAtMillis) :
      FieldValue.delete(),
  };
}

function resolveActionDeliveryDefaultMaxAttempts(channel) {
  return channel === ACTION_DELIVERY_CHANNEL_PUSH ? 3 : 1;
}

function resolveActionDeliveryConfirmedAt(status, processedAtMillis) {
  if (status === "confirmed") {
    return Math.max(processedAtMillis, 0);
  }
  return 0;
}

function resolveActionDeliveryNextRetryAt(
    channel,
    status,
    attemptCount,
    maxAttemptCount,
    processedAtMillis,
) {
  if (channel !== ACTION_DELIVERY_CHANNEL_PUSH ||
      status !== "failed" ||
      attemptCount >= maxAttemptCount ||
      processedAtMillis <= 0) {
    return 0;
  }
  return processedAtMillis + (10 * 60 * 1000 * Math.max(attemptCount, 1));
}

function resolveActionDeliverySlaDueAt(
    sourceType,
    channel,
    status,
    createdAtMillis,
    processedAtMillis,
) {
  if (channel !== ACTION_DELIVERY_CHANNEL_PUSH) {
    return 0;
  }
  const baseTime = Math.max(processedAtMillis, createdAtMillis);
  if (status === "confirmed" || status === "skipped") {
    return baseTime;
  }
  const confirmationWindowMillis = sourceType === "emergency" ?
    15 * 60 * 1000 :
    60 * 60 * 1000;
  return baseTime + confirmationWindowMillis;
}

function resolveActionDeliveryState({
  channel,
  status,
  confirmedAtMillis,
  nextRetryAtMillis,
  slaDueAtMillis,
  nowMillis,
}) {
  if (status === "skipped") {
    return "skipped";
  }
  if (status === "failed") {
    return "follow_up_required";
  }
  if (status === "confirmed" || confirmedAtMillis > 0) {
    return "delivered";
  }
  if (channel === ACTION_DELIVERY_CHANNEL_PUSH) {
    if (nextRetryAtMillis > 0) {
      return "follow_up_required";
    }
    if (slaDueAtMillis > 0 && nowMillis > slaDueAtMillis) {
      return "follow_up_required";
    }
    return "pending_confirmation";
  }
  return "delivered";
}

function resolveActionDeliveryPriority(sourceType, state) {
  if (state === "follow_up_required") {
    return sourceType === "emergency" ? "immediate" : "action_required";
  }
  if (state === "pending_confirmation") {
    return sourceType === "emergency" ? "immediate" : "monitoring";
  }
  if (state === "skipped") {
    return "archived";
  }
  return "monitoring";
}

function resolveActionDeliveryFilterKeys(state) {
  if (state === "pending_confirmation") {
    return ["pending_confirmation"];
  }
  if (state === "follow_up_required") {
    return ["follow_up_required"];
  }
  return ["completed"];
}

function resolveActionDeliverySlaStatus(state) {
  if (state === "follow_up_required") {
    return "attention_required";
  }
  if (state === "pending_confirmation") {
    return "on_track";
  }
  return "completed";
}

function getAdminPushDeliveryConfig() {
  const endpoint = sanitizeText(process.env.ADMIN_PUSH_ENDPOINT);
  const apiKey = sanitizeText(process.env.ADMIN_PUSH_API_KEY);
  const authScheme = sanitizeText(process.env.ADMIN_PUSH_AUTH_SCHEME) || "Bearer";

  return {
    endpoint,
    apiKey,
    authScheme,
    isConfigured: Boolean(endpoint && apiKey),
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

async function removeInvalidNotificationTokens(userDocumentReference, invalidTokens) {
  if (!userDocumentReference || !Array.isArray(invalidTokens) || invalidTokens.length === 0) {
    return;
  }

  await userDocumentReference.update({
    notificationTokens: FieldValue.arrayRemove(...invalidTokens),
    updatedAt: FieldValue.serverTimestamp(),
  });
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
