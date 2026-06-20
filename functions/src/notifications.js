const {onDocumentWritten} = require("firebase-functions/v2/firestore");
const {onSchedule} = require("firebase-functions/v2/scheduler");
const logger = require("firebase-functions/logger");
const {FieldPath, FieldValue, Timestamp, getFirestore} = require("firebase-admin/firestore");
const {getMessaging} = require("firebase-admin/messaging");

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

const DAY_IN_MILLIS = 24 * 60 * 60 * 1000;
const CLIENT_SUPPORT_RESPONSE_REMINDER_THRESHOLD_MILLIS = DAY_IN_MILLIS;
const CLIENT_SUPPORT_RESPONSE_REMINDER_INTERVAL_MILLIS = DAY_IN_MILLIS;
const CLIENT_SUPPORT_RESPONSE_REMINDER_MAX_COUNT = 3;
const NOTIFICATION_TOKEN_STALE_THRESHOLD_MILLIS = 60 * DAY_IN_MILLIS;
const NOTIFICATION_TOKEN_CLEANUP_BATCH_SIZE = 200;

const notifyClientSupportAnswered = onDocumentWritten(
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

      const title = "문의 답변이 도착했습니다";
      const categoryLabel = resolveClientSupportCategoryLabel(afterData.category);
      const requestTitle = sanitizeText(afterData.title) || "문의";
      const body = `${categoryLabel} · ${requestTitle}`;
      const deliverySummary = await sendClientSupportNotification({
        supportRequestId: sanitizeText(event.params?.supportRequestId),
        supportData: afterData,
        title,
        body,
      });

      logger.info("이용자 문의 답변 알림을 보냈습니다.", {
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

const notifyCompanionChatMessage = onDocumentWritten(
    COMPANION_CHAT_NOTIFICATION_OPTIONS,
    async (event) => {
      if (!event.data?.after?.exists) {
        return;
      }

      const beforeData = event.data?.before?.exists ? event.data.before.data() : null;
      const afterData = event.data.after.data();
      const latestMessage = resolveLatestCompanionChatMessage(beforeData, afterData);
      if (!latestMessage) {
        return;
      }

      const appointmentRequestId = sanitizeText(afterData?.appointmentRequestId);
      const managerUserId = sanitizeText(afterData?.managerUserId);
      if (!appointmentRequestId || !managerUserId) {
        logger.warn("안심 채팅 알림 대상 정보를 찾지 못했습니다.", {
          sessionId: sanitizeText(event.params?.sessionId),
          appointmentRequestId,
          managerUserId,
        });
        return;
      }

      const requestSnapshot = await getFirestore()
          .collection("appointmentRequests")
          .doc(appointmentRequestId)
          .get();
      if (!requestSnapshot.exists) {
        logger.warn("안심 채팅 알림에 필요한 예약 문서를 찾지 못했습니다.", {
          sessionId: sanitizeText(event.params?.sessionId),
          appointmentRequestId,
        });
        return;
      }

      const recipientUserIds = resolveCompanionChatRecipientUserIds(
          latestMessage.senderRole,
          requestSnapshot.data(),
          managerUserId,
      );
      if (recipientUserIds.length === 0) {
        return;
      }

      const title = resolveCompanionChatNotificationTitle(latestMessage.senderRole);
      const body = buildCompanionChatNotificationBody(latestMessage);
      const deliverySummary = await sendCompanionChatNotification({
        sessionId: sanitizeText(event.params?.sessionId),
        appointmentRequestId,
        recipientUserIds,
        latestMessage,
        title,
        body,
      });

      logger.info("안심 채팅 새 메시지 알림을 보냈습니다.", {
        sessionId: sanitizeText(event.params?.sessionId),
        appointmentRequestId,
        senderRole: latestMessage.senderRole,
        recipientUserCount: recipientUserIds.length,
        successCount: deliverySummary.successCount,
        failureCount: deliverySummary.failureCount,
        invalidTokenCount: deliverySummary.invalidTokenCount,
      });
    },
);

const notifyCompanionLocationAlert = onDocumentWritten(
    COMPANION_LOCATION_ALERT_NOTIFICATION_OPTIONS,
    async (event) => {
      if (!event.data?.after?.exists) {
        return;
      }

      const beforeData = event.data?.before?.exists ? event.data.before.data() : null;
      const afterData = event.data.after.data();
      if (!shouldNotifyCompanionLocationAlert(beforeData, afterData)) {
        return;
      }

      const appointmentRequestId = sanitizeText(afterData?.appointmentRequestId);
      if (!appointmentRequestId) {
        return;
      }

      const requestSnapshot = await getFirestore()
          .collection("appointmentRequests")
          .doc(appointmentRequestId)
          .get();
      if (!requestSnapshot.exists) {
        return;
      }

      const requestData = requestSnapshot.data();
      const alertStage = sanitizeText(afterData?.locationAlertStage);
      const title = resolveCompanionLocationAlertTitle(alertStage);
      const body = buildCompanionLocationAlertBody(alertStage, requestData);
      const recipientUserIds = resolveCompanionLocationRecipientUserIds(requestData);
      const deliverySummary = await sendCompanionLocationAlertNotification({
        sessionId: sanitizeText(event.params?.sessionId),
        appointmentRequestId,
        alertStage,
        recipientUserIds,
        title,
        body,
      });

      logger.info("동행 위치 자동 알림을 보냈습니다.", {
        sessionId: sanitizeText(event.params?.sessionId),
        appointmentRequestId,
        alertStage,
        successCount: deliverySummary.successCount,
        failureCount: deliverySummary.failureCount,
        invalidTokenCount: deliverySummary.invalidTokenCount,
      });
    },
);

const sendClientSupportAnswerReminders = onSchedule(
    CLIENT_SUPPORT_REMINDER_SCHEDULE_OPTIONS,
    async () => {
      const summary = await processClientSupportAnswerReminders();
      logger.info("문의 답변 재알림 스케줄을 실행했습니다.", summary);
    },
);

const cleanupStaleNotificationTokens = onSchedule(
    NOTIFICATION_TOKEN_CLEANUP_SCHEDULE_OPTIONS,
    async () => {
      const summary = await processStaleNotificationTokenCleanup();
      logger.info("FCM 장기 미사용 토큰 정리를 마쳤습니다.", summary);
    },
);

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
    const requestTitle = sanitizeText(supportData.title) || "문의";
    const deliverySummary = await sendClientSupportNotification({
      supportRequestId: supportDocument.id,
      supportData,
      title: "문의 답변을 아직 확인하지 않았습니다",
      body: `${categoryLabel} · ${requestTitle}`,
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
      return `첨부 파일 ${attachments.length}개를 보냈습니다`;
    }
    const contentType = sanitizeText(attachments[0]?.contentType);
    if (contentType === "application/pdf") {
      return "PDF 파일을 보냈습니다.";
    }
    if (contentType.startsWith("image/")) {
      return "이미지를 보냈습니다.";
    }
    return "첨부 파일을 보냈습니다.";
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
      return `${hospitalName} 인근 약국 도착이 확인되었습니다.`;
    }
    return "인근 약국 도착이 확인되었습니다.";
  }
  if (hospitalName) {
    return `${hospitalName} 도착이 확인되었습니다.`;
  }
  return "병원 도착이 확인되었습니다.";
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

  return beforeStatus !== afterStatus ||
      beforeResponse !== afterResponse ||
      beforeRespondedAt !== afterRespondedAt;
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

function sanitizeText(value) {
  return typeof value === "string" ? value.trim() : "";
}

module.exports = {
  notifyClientSupportAnswered,
  notifyCompanionChatMessage,
  notifyCompanionLocationAlert,
  sendClientSupportAnswerReminders,
  cleanupStaleNotificationTokens,
};
