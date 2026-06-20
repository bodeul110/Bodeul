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

const ACTION_DELIVERY_SCHEDULE_OPTIONS = {
  region: "asia-northeast3",
  schedule: "*/5 * * * *",
  timeZone: "Asia/Seoul",
};

const ACTION_DELIVERY_RETRYABLE_STATES = new Set(["PENDING", "FAILED"]);
const ACTION_DELIVERY_PROCESSING_STATE = "PROCESSING";
const ACTION_DELIVERY_SENT_STATE = "SENT";
const ACTION_DELIVERY_SIMULATED_STATE = "SIMULATED";
const ACTION_DELIVERY_SKIPPED_STATE = "SKIPPED";
const ACTION_DELIVERY_FAILED_STATE = "FAILED";
const ACTION_DELIVERY_CHANNEL_PUSH = "app_push";
const ACTION_DELIVERY_BATCH_SIZE = 20;

const deliverAdminActionDeliveryJobs = onSchedule(
    ACTION_DELIVERY_SCHEDULE_OPTIONS,
    async () => {
      const summary = await processAdminActionDeliveryJobs({
        source: "delivery",
        batchSize: ACTION_DELIVERY_BATCH_SIZE,
      });
      logger.info("관리자 후속 알림 전달 처리를 마쳤습니다.", summary);
    },
);

const dispatchAdminActionDeliveryJobs = onCall(CALLABLE_FUNCTIONS_OPTIONS, async (request) => {
  await assertAdminCaller(request);
  const batchSize = sanitizeBatchSize(request.data?.batchSize);
  return processAdminActionDeliveryJobs({
    source: "manual",
    batchSize,
  });
});

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
          "푸시 채널이 아닌 작업이라 전달 대상에서 제외했습니다.",
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
    logger.error("관리자 후속 알림 전달 중 오류가 발생했습니다.", {
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
    deliveryNote: `관리자 앱 푸시 발송을 완료했습니다. 수신 관리자 ${recipientProfiles.length}명.`,
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
    deliveryNote: `푸시 연동값이 없어 시뮬레이션으로 처리했습니다. 수신 관리자 ${recipientProfiles.length}명.`,
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
      "관리자 앱 푸시 발송이 반복 실패해 최대 재시도 횟수를 모두 사용했습니다." :
      "관리자 앱 푸시 발송에 실패해 다음 재시도를 기다립니다.",
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
    return ACTION_DELIVERY_BATCH_SIZE;
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

function unauthenticated(message) {
  return new HttpsError("unauthenticated", message, {message});
}

module.exports = {
  deliverAdminActionDeliveryJobs,
  dispatchAdminActionDeliveryJobs,
};
