package com.example.bodeul.data.firebase;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.CompanionChatMessage;
import com.example.bodeul.domain.model.CompanionLocationHistoryEntry;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.UserRole;
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

        return new CompanionSession(
                documentSnapshot.getId(),
                appointmentRequestId,
                managerUserId,
                currentStepOrder.intValue(),
                SessionStatus.valueOf(statusValue),
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
            if (body.isEmpty()) {
                continue;
            }
            long sentAtMillis = resolveTimestampMillis(valueMap.get("sentAtMillis"));
            UserRole senderRole = fallbackChatSenderRole;
            if (!roleValue.isEmpty()) {
                try {
                    senderRole = UserRole.valueOf(roleValue);
                } catch (IllegalArgumentException ignored) {
                    senderRole = fallbackChatSenderRole;
                }
            }
            messages.add(new CompanionChatMessage(senderRole, body, sentAtMillis));
        }
        return messages;
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
