"use strict";

function resolveLegacyChatRecipientUserIds(senderRole, requestData, managerUserId) {
  const normalizedSenderRole = sanitizeText(senderRole);
  return uniqueUserIds([
    normalizedSenderRole === "PATIENT" ? "" : requestData?.patientUserId,
    normalizedSenderRole === "MANAGER" ? "" : managerUserId,
  ]);
}

function resolveLegacyLocationRecipientUserIds(requestData) {
  return uniqueUserIds([requestData?.patientUserId]);
}

function resolveLegacyReminderRecipientUserIds(appointmentData) {
  return uniqueUserIds([appointmentData?.patientUserId]);
}

function canUsePatientRequesterPhoneFallback(appointmentData) {
  const patientUserId = sanitizeText(appointmentData?.patientUserId);
  return patientUserId.length > 0
      && sanitizeText(appointmentData?.requesterUserId) === patientUserId
      && sanitizeText(appointmentData?.requesterRole) === "PATIENT";
}

function uniqueUserIds(values) {
  return Array.from(new Set(values.map(sanitizeText).filter(Boolean)));
}

function sanitizeText(value) {
  return typeof value === "string" ? value.trim() : "";
}

module.exports = {
  canUsePatientRequesterPhoneFallback,
  resolveLegacyChatRecipientUserIds,
  resolveLegacyLocationRecipientUserIds,
  resolveLegacyReminderRecipientUserIds,
};
