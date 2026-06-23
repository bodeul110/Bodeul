package com.example.bodeul.data.firebase;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.CompanionChatMessage;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.domain.model.CompanionLocationAlertStage;
import com.example.bodeul.domain.model.CompanionLocationHistoryEntry;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.util.SafeEnumParser;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * `companionSessions` 문서를 공통 도메인 모델로 변환하고 위치 이력 payload를 만든다.
 */
final class FirebaseCompanionSessionMapper {
    private FirebaseCompanionSessionMapper() {
    }

    @Nullable
    static CompanionSession toSession(
            @Nullable DocumentSnapshot documentSnapshot,
            UserRole fallbackChatSenderRole
    ) {
        if (documentSnapshot == null || !documentSnapshot.exists()) {
            return null;
        }

        String appointmentRequestId = documentSnapshot.getString("appointmentRequestId");
        String managerUserId = documentSnapshot.getString("managerUserId");
        Long currentStepOrder = documentSnapshot.getLong("currentStepOrder");
        String statusValue = documentSnapshot.getString("currentStatus");
        if (appointmentRequestId == null || managerUserId == null || currentStepOrder == null || statusValue == null) {
            return null;
        }

        SessionStatus status = SafeEnumParser.parseOrNull(SessionStatus.class, statusValue);
        if (status == null) {
            return null;
        }

        CompanionSession session = new CompanionSession(
                documentSnapshot.getId(),
                appointmentRequestId,
                managerUserId,
                currentStepOrder.intValue(),
                status,
                stringOrEmpty(documentSnapshot.getString("guardianUpdate")),
                stringOrEmpty(documentSnapshot.getString("locationSummary")),
                stringOrEmpty(documentSnapshot.getString("fieldPhotoNote")),
                stringOrEmpty(documentSnapshot.getString("medicationNote")),
                stringOrEmpty(documentSnapshot.getString("pharmacySummary")),
                Boolean.TRUE.equals(documentSnapshot.getBoolean("pharmacyCompleted")),
                doubleOrNull(documentSnapshot.get("sharedLatitude")),
                doubleOrNull(documentSnapshot.get("sharedLongitude")),
                resolveTimestampMillis(documentSnapshot.get("sharedLocationUpdatedAt")),
                Boolean.TRUE.equals(documentSnapshot.getBoolean("liveLocationSharingActive")),
                resolveTimestampMillis(documentSnapshot.get("liveLocationSharingStartedAt")),
                toSharedLocationHistory(documentSnapshot.get("sharedLocationHistory")),
                toChatMessages(documentSnapshot.get("chatMessages"), fallbackChatSenderRole)
        );
        session.setPrescriptionCollected(
                Boolean.TRUE.equals(documentSnapshot.getBoolean("prescriptionCollected"))
        );
        session.setMedicationGuidanceCompleted(
                Boolean.TRUE.equals(documentSnapshot.getBoolean("medicationGuidanceCompleted"))
        );
        session.setLocationAlertStage(
                CompanionLocationAlertStage.fromValue(documentSnapshot.getString("locationAlertStage"))
        );
        session.setLocationAlertSentAtMillis(resolveTimestampMillis(documentSnapshot.get("locationAlertSentAt")));
        session.markChatRead(UserRole.PATIENT, resolveTimestampMillis(documentSnapshot.get("patientChatReadAt")));
        session.markChatRead(UserRole.GUARDIAN, resolveTimestampMillis(documentSnapshot.get("guardianChatReadAt")));
        session.markChatRead(UserRole.MANAGER, resolveTimestampMillis(documentSnapshot.get("managerChatReadAt")));
        return session;
    }

    static List<Map<String, Object>> toSharedLocationHistoryPayload(
            List<CompanionLocationHistoryEntry> historyEntries
    ) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (CompanionLocationHistoryEntry historyEntry : historyEntries) {
            Map<String, Object> item = new HashMap<>();
            item.put("latitude", historyEntry.getLatitude());
            item.put("longitude", historyEntry.getLongitude());
            item.put("summary", historyEntry.getSummary());
            item.put("capturedAt", historyEntry.getCapturedAtMillis());
            payload.add(item);
        }
        return payload;
    }

    private static List<CompanionLocationHistoryEntry> toSharedLocationHistory(@Nullable Object rawValue) {
        if (!(rawValue instanceof List)) {
            return Collections.emptyList();
        }

        List<CompanionLocationHistoryEntry> historyEntries = new ArrayList<>();
        for (Object rawEntry : (List<?>) rawValue) {
            if (!(rawEntry instanceof Map)) {
                continue;
            }
            Map<?, ?> valueMap = (Map<?, ?>) rawEntry;
            Double latitude = doubleOrNull(valueMap.get("latitude"));
            Double longitude = doubleOrNull(valueMap.get("longitude"));
            if (latitude == null || longitude == null) {
                continue;
            }
            long capturedAtMillis = resolveTimestampMillis(valueMap.get("capturedAt"));
            if (capturedAtMillis <= 0L) {
                capturedAtMillis = resolveTimestampMillis(valueMap.get("capturedAtMillis"));
            }
            historyEntries.add(new CompanionLocationHistoryEntry(
                    latitude,
                    longitude,
                    stringValue(valueMap.get("summary")),
                    capturedAtMillis
            ));
        }
        historyEntries.sort(Comparator.comparingLong(CompanionLocationHistoryEntry::getCapturedAtMillis).reversed());
        return historyEntries;
    }

    private static List<CompanionChatMessage> toChatMessages(
            @Nullable Object rawValue,
            UserRole fallbackChatSenderRole
    ) {
        List<CompanionChatMessage> messages = new ArrayList<>();
        if (!(rawValue instanceof List)) {
            return messages;
        }
        for (Object rawMessage : (List<?>) rawValue) {
            if (!(rawMessage instanceof Map)) {
                continue;
            }
            Map<?, ?> valueMap = (Map<?, ?>) rawMessage;
            String roleValue = stringValue(valueMap.get("senderRole"));
            String body = stringValue(valueMap.get("body"));
            List<CompanionChatAttachment> attachments = toChatAttachments(
                    valueMap.get("attachments"),
                    valueMap.get("attachment")
            );
            if (body.isEmpty() && attachments.isEmpty()) {
                continue;
            }
            long sentAtMillis = resolveTimestampMillis(valueMap.get("sentAtMillis"));
            UserRole senderRole = SafeEnumParser.parseOrDefault(
                    UserRole.class,
                    roleValue,
                    fallbackChatSenderRole
            );
            messages.add(new CompanionChatMessage(senderRole, body, sentAtMillis, attachments));
        }
        return messages;
    }

    @Nullable
    private static CompanionChatAttachment toChatAttachment(@Nullable Object rawValue) {
        if (!(rawValue instanceof Map)) {
            return null;
        }
        Map<?, ?> valueMap = (Map<?, ?>) rawValue;
        String fullPath = stringValue(valueMap.get("fullPath"));
        if (fullPath.isEmpty()) {
            return null;
        }
        return new CompanionChatAttachment(
                fullPath,
                stringValue(valueMap.get("fileName")),
                stringValue(valueMap.get("contentType")),
                resolveTimestampMillis(valueMap.get("uploadedAtMillis"))
        );
    }

    private static List<CompanionChatAttachment> toChatAttachments(
            @Nullable Object rawAttachments,
            @Nullable Object rawAttachment
    ) {
        List<CompanionChatAttachment> attachments = new ArrayList<>();
        if (rawAttachments instanceof List) {
            for (Object item : (List<?>) rawAttachments) {
                CompanionChatAttachment attachment = toChatAttachment(item);
                if (attachment != null && !attachment.isEmpty()) {
                    attachments.add(attachment);
                }
            }
        }
        if (!attachments.isEmpty()) {
            return attachments;
        }
        CompanionChatAttachment attachment = toChatAttachment(rawAttachment);
        if (attachment != null && !attachment.isEmpty()) {
            attachments.add(attachment);
        }
        return attachments;
    }

    @Nullable
    private static Double doubleOrNull(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    private static long resolveTimestampMillis(@Nullable Object rawValue) {
        if (rawValue instanceof Timestamp) {
            return ((Timestamp) rawValue).toDate().getTime();
        }
        if (rawValue instanceof Number) {
            return ((Number) rawValue).longValue();
        }
        return 0L;
    }

    private static String stringOrEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    private static String stringValue(@Nullable Object rawValue) {
        return rawValue == null ? "" : String.valueOf(rawValue).trim();
    }
}
