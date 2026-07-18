package com.example.bodeul.data.coreapi;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.domain.model.CompanionChatMessage;
import com.example.bodeul.domain.model.CompanionLocationAlertStage;
import com.example.bodeul.domain.model.CompanionLocationHistoryEntry;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.util.SafeEnumParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 동행 세션 진행 상태와 리포트를 Core API에서 읽고 변경한다.
 */
final class CoreApiCompanionSessionClient {
    private final CoreApiAuthenticatedClient authenticatedClient;
    private final Map<String, SessionSnapshot> references = new ConcurrentHashMap<>();

    CoreApiCompanionSessionClient(Context context) {
        authenticatedClient = new CoreApiAuthenticatedClient(context);
    }

    void getSessions(RepositoryCallback<List<SessionSnapshot>> callback) {
        authenticatedClient.execute(
                (idToken, appCheckToken) -> {
                    JSONObject response = authenticatedClient.requestJson(
                            "GET",
                            "/api/companion-sessions",
                            null,
                            idToken,
                            appCheckToken);
                    JSONArray items = response.optJSONArray("sessions");
                    List<SessionSnapshot> sessions = new ArrayList<>();
                    if (items == null) {
                        return sessions;
                    }
                    for (int index = 0; index < items.length(); index++) {
                        JSONObject item = items.optJSONObject(index);
                        if (item != null) {
                            sessions.add(parseAndRememberSession(item));
                        }
                    }
                    return sessions;
                },
                callback,
                "동행 정보를 불러오지 못했습니다.",
                "동행 세션 API"
        );
    }

    void findSession(
            @Nullable String legacySessionId,
            @Nullable String coreAppointmentId,
            RepositoryCallback<SessionSnapshot> callback
    ) {
        getSessions(new RepositoryCallback<List<SessionSnapshot>>() {
            @Override
            public void onSuccess(List<SessionSnapshot> result) {
                callback.onSuccess(findKnown(legacySessionId, coreAppointmentId));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    void getReport(SessionSnapshot session, RepositoryCallback<ReportSnapshot> callback) {
        authenticatedClient.execute(
                (idToken, appCheckToken) -> parseReport(authenticatedClient.requestJson(
                        "GET",
                        "/api/companion-sessions/" + session.coreId + "/report",
                        null,
                        idToken,
                        appCheckToken)),
                callback,
                "동행 리포트를 불러오지 못했습니다.",
                "동행 리포트 API"
        );
    }

    void getRealtime(
            SessionSnapshot session,
            RepositoryCallback<RealtimeSnapshot> callback
    ) {
        authenticatedClient.execute(
                (idToken, appCheckToken) -> parseRealtime(authenticatedClient.requestJson(
                        "GET",
                        "/api/companion-sessions/" + session.coreId + "/realtime",
                        null,
                        idToken,
                        appCheckToken)),
                callback,
                "실시간 동행 정보를 불러오지 못했습니다.",
                "동행 실시간 API"
        );
    }

    void enrichWithRealtime(
            SessionSnapshot session,
            CompanionSession model,
            RepositoryCallback<CompanionSession> callback
    ) {
        getRealtime(session, new RepositoryCallback<RealtimeSnapshot>() {
            @Override
            public void onSuccess(RealtimeSnapshot result) {
                result.applyTo(model);
                callback.onSuccess(model);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    void sendRealtimeMessage(
            String externalSessionId,
            String bodyText,
            List<CompanionChatAttachment> attachments,
            RepositoryCallback<RealtimeSnapshot> callback
    ) {
        resolveSession(externalSessionId, new RepositoryCallback<SessionSnapshot>() {
            @Override
            public void onSuccess(SessionSnapshot session) {
                JSONObject body = new JSONObject();
                try {
                    body.put("clientMessageId", UUID.randomUUID().toString());
                    body.put("body", valueOrEmpty(bodyText));
                    JSONArray attachmentItems = new JSONArray();
                    if (attachments != null) {
                        for (CompanionChatAttachment attachment : attachments) {
                            if (attachment == null || attachment.isEmpty()) {
                                continue;
                            }
                            JSONObject item = new JSONObject();
                            item.put("storagePath", attachment.getFullPath());
                            item.put("fileName", attachment.getFileName());
                            item.put("contentType", attachment.getContentType());
                            item.put("sizeBytes", attachment.getSizeBytes());
                            attachmentItems.put(item);
                        }
                    }
                    body.put("attachments", attachmentItems);
                } catch (JSONException exception) {
                    callback.onError("채팅 전송 요청을 준비하지 못했습니다.");
                    return;
                }
                authenticatedClient.execute(
                        (idToken, appCheckToken) -> authenticatedClient.requestJson(
                                "POST",
                                "/api/companion-sessions/" + session.coreId + "/messages",
                                body,
                                idToken,
                                appCheckToken),
                        new RepositoryCallback<JSONObject>() {
                            @Override
                            public void onSuccess(JSONObject ignored) {
                                getRealtime(session, callback);
                            }

                            @Override
                            public void onError(String message) {
                                callback.onError(message);
                            }
                        },
                        "채팅 메시지를 보내지 못했습니다.",
                        "동행 채팅 API"
                );
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    void markRealtimeRead(String externalSessionId) {
        resolveSession(externalSessionId, new RepositoryCallback<SessionSnapshot>() {
            @Override
            public void onSuccess(SessionSnapshot session) {
                getRealtime(session, new RepositoryCallback<RealtimeSnapshot>() {
                    @Override
                    public void onSuccess(RealtimeSnapshot snapshot) {
                        String lastMessageId = snapshot.lastMessageId();
                        if (lastMessageId.isEmpty()) {
                            return;
                        }
                        JSONObject body = new JSONObject();
                        try {
                            body.put("lastReadMessageId", lastMessageId);
                        } catch (JSONException ignored) {
                            return;
                        }
                        authenticatedClient.execute(
                                (idToken, appCheckToken) -> authenticatedClient.requestJson(
                                        "PUT",
                                        "/api/companion-sessions/" + session.coreId + "/read-receipt",
                                        body,
                                        idToken,
                                        appCheckToken),
                                new RepositoryCallback<JSONObject>() {
                                    @Override
                                    public void onSuccess(JSONObject result) {
                                        // 읽음 표시는 화면 흐름을 막지 않는 보조 동작이다.
                                    }

                                    @Override
                                    public void onError(String message) {
                                        // 다음 화면 진입이나 Realtime 이벤트에서 다시 동기화한다.
                                    }
                                },
                                "채팅 읽음 상태를 저장하지 못했습니다.",
                                "동행 채팅 읽음 API"
                        );
                    }

                    @Override
                    public void onError(String message) {
                        // 읽음 표시는 화면 오류로 확장하지 않는다.
                    }
                });
            }

            @Override
            public void onError(String message) {
                // 읽음 표시는 화면 오류로 확장하지 않는다.
            }
        });
    }

    void shareRealtimeLocation(
            String externalSessionId,
            double latitude,
            double longitude,
            RepositoryCallback<RealtimeSnapshot> callback
    ) {
        resolveSession(externalSessionId, new RepositoryCallback<SessionSnapshot>() {
            @Override
            public void onSuccess(SessionSnapshot session) {
                JSONObject body = new JSONObject();
                try {
                    body.put("clientLocationId", UUID.randomUUID().toString());
                    body.put("latitude", latitude);
                    body.put("longitude", longitude);
                    body.put("capturedAt", Instant.now().toString());
                } catch (JSONException exception) {
                    callback.onError("위치 공유 요청을 준비하지 못했습니다.");
                    return;
                }
                authenticatedClient.execute(
                        (idToken, appCheckToken) -> authenticatedClient.requestJson(
                                "POST",
                                "/api/companion-sessions/" + session.coreId + "/locations",
                                body,
                                idToken,
                                appCheckToken),
                        new RepositoryCallback<JSONObject>() {
                            @Override
                            public void onSuccess(JSONObject ignored) {
                                getRealtime(session, callback);
                            }

                            @Override
                            public void onError(String message) {
                                callback.onError(message);
                            }
                        },
                        "현재 위치를 공유하지 못했습니다.",
                        "동행 위치 API"
                );
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    void updateText(
            String externalSessionId,
            String field,
            String value,
            RepositoryCallback<SessionSnapshot> callback
    ) {
        updateSession(externalSessionId, field, value == null ? "" : value, callback);
    }

    void updateBoolean(
            String externalSessionId,
            String field,
            boolean value,
            RepositoryCallback<SessionSnapshot> callback
    ) {
        updateSession(externalSessionId, field, value, callback);
    }

    void advance(String externalSessionId, RepositoryCallback<SessionSnapshot> callback) {
        resolveSession(externalSessionId, new RepositoryCallback<SessionSnapshot>() {
            @Override
            public void onSuccess(SessionSnapshot session) {
                JSONObject body = new JSONObject();
                try {
                    body.put("version", session.version);
                } catch (JSONException exception) {
                    callback.onError("동행 단계 요청을 준비하지 못했습니다.");
                    return;
                }
                authenticatedClient.execute(
                        (idToken, appCheckToken) -> parseAndRememberSession(
                                authenticatedClient.requestJson(
                                        "POST",
                                        "/api/companion-sessions/" + session.coreId + "/advance",
                                        body,
                                        idToken,
                                        appCheckToken)),
                        callback,
                        "다음 동행 단계로 이동하지 못했습니다.",
                        "동행 단계 API"
                );
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    void submitReport(
            String externalSessionId,
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String medicationName,
            String medicationChangeSummary,
            String medicationScheduleNote,
            @Nullable MedicationComparisonDecision medicationComparisonDecision,
            String medicationComparisonNote,
            String nextVisitAt,
            RepositoryCallback<ReportSnapshot> callback
    ) {
        resolveSession(externalSessionId, new RepositoryCallback<SessionSnapshot>() {
            @Override
            public void onSuccess(SessionSnapshot session) {
                JSONObject body = new JSONObject();
                try {
                    body.put("version", session.version);
                    body.put("summary", valueOrEmpty(summary));
                    body.put("treatmentNotes", valueOrEmpty(treatmentNotes));
                    body.put("medicationNotes", valueOrEmpty(medicationNotes));
                    body.put("medicationName", valueOrEmpty(medicationName));
                    body.put("medicationChangeSummary", valueOrEmpty(medicationChangeSummary));
                    body.put("medicationScheduleNote", valueOrEmpty(medicationScheduleNote));
                    body.put(
                            "medicationComparisonDecisionCode",
                            medicationComparisonDecision == null
                                    ? ""
                                    : medicationComparisonDecision.name());
                    body.put("medicationComparisonNote", valueOrEmpty(medicationComparisonNote));
                    body.put("nextVisitAt", valueOrEmpty(nextVisitAt));
                } catch (JSONException exception) {
                    callback.onError("동행 리포트 요청을 준비하지 못했습니다.");
                    return;
                }
                authenticatedClient.execute(
                        (idToken, appCheckToken) -> {
                            ReportSnapshot report = parseReport(authenticatedClient.requestJson(
                                    "PUT",
                                    "/api/companion-sessions/" + session.coreId + "/report",
                                    body,
                                    idToken,
                                    appCheckToken));
                            forget(session);
                            return report;
                        },
                        callback,
                        "동행 리포트를 저장하지 못했습니다.",
                        "동행 리포트 API"
                );
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private void updateSession(
            String externalSessionId,
            String field,
            Object value,
            RepositoryCallback<SessionSnapshot> callback
    ) {
        resolveSession(externalSessionId, new RepositoryCallback<SessionSnapshot>() {
            @Override
            public void onSuccess(SessionSnapshot session) {
                JSONObject body = new JSONObject();
                try {
                    body.put("version", session.version);
                    body.put(field, value);
                } catch (JSONException exception) {
                    callback.onError("동행 변경 요청을 준비하지 못했습니다.");
                    return;
                }
                authenticatedClient.execute(
                        (idToken, appCheckToken) -> parseAndRememberSession(
                                authenticatedClient.requestJson(
                                        "PATCH",
                                        "/api/companion-sessions/" + session.coreId,
                                        body,
                                        idToken,
                                        appCheckToken)),
                        callback,
                        "동행 정보를 저장하지 못했습니다.",
                        "동행 세션 API"
                );
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private void resolveSession(
            String externalSessionId,
            RepositoryCallback<SessionSnapshot> callback
    ) {
        SessionSnapshot known = references.get(valueOrEmpty(externalSessionId));
        if (known != null) {
            callback.onSuccess(known);
            return;
        }
        findSession(externalSessionId, null, new RepositoryCallback<SessionSnapshot>() {
            @Override
            public void onSuccess(SessionSnapshot result) {
                if (result == null) {
                    callback.onError("동행 세션 정보를 찾지 못했습니다.");
                    return;
                }
                callback.onSuccess(result);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Nullable
    SessionSnapshot findKnown(
            @Nullable String legacySessionId,
            @Nullable String coreAppointmentId
    ) {
        String normalizedLegacyId = valueOrEmpty(legacySessionId);
        if (!normalizedLegacyId.isEmpty()) {
            SessionSnapshot bySession = references.get(normalizedLegacyId);
            if (bySession != null) {
                return bySession;
            }
        }
        String normalizedAppointmentId = valueOrEmpty(coreAppointmentId);
        if (!normalizedAppointmentId.isEmpty()) {
            return references.get("appointment:" + normalizedAppointmentId);
        }
        return null;
    }

    private SessionSnapshot parseAndRememberSession(JSONObject item) throws JSONException {
        String coreId = requireText(item, "id");
        String legacyFirestoreId = optText(item, "legacyFirestoreId");
        String appointmentRequestId = requireText(item, "appointmentRequestId");
        String managerUserId = requireText(item, "managerUserId");
        SessionStatus status;
        try {
            status = SessionStatus.valueOf(requireText(item, "currentStatus"));
        } catch (IllegalArgumentException exception) {
            throw new JSONException("알 수 없는 동행 세션 상태입니다.");
        }
        SessionSnapshot snapshot = new SessionSnapshot(
                coreId,
                legacyFirestoreId,
                appointmentRequestId,
                managerUserId,
                item.getInt("currentStepOrder"),
                item.optInt("totalStepCount", 0),
                status,
                optText(item, "guardianUpdate"),
                optText(item, "locationSummary"),
                optText(item, "fieldPhotoNote"),
                optText(item, "medicationNote"),
                optText(item, "pharmacySummary"),
                item.optBoolean("prescriptionCollected", false),
                item.optBoolean("pharmacyCompleted", false),
                item.optBoolean("medicationGuidanceCompleted", false),
                item.optBoolean("liveLocationSharingActive", false),
                optText(item, "liveLocationSharingStartedAt"),
                optText(item, "locationAlertStage"),
                optText(item, "locationAlertSentAt"),
                item.getLong("version"));
        references.put(coreId, snapshot);
        references.put("appointment:" + appointmentRequestId, snapshot);
        if (!legacyFirestoreId.isEmpty()) {
            references.put(legacyFirestoreId, snapshot);
        }
        return snapshot;
    }

    private void forget(SessionSnapshot session) {
        references.remove(session.coreId);
        references.remove("appointment:" + session.appointmentRequestId);
        if (!session.legacyFirestoreId.isEmpty()) {
            references.remove(session.legacyFirestoreId);
        }
    }

    private ReportSnapshot parseReport(JSONObject item) throws JSONException {
        return new ReportSnapshot(
                requireText(item, "id"),
                optText(item, "legacyFirestoreId"),
                requireText(item, "companionSessionId"),
                optText(item, "summary"),
                optText(item, "treatmentNotes"),
                optText(item, "medicationNotes"),
                optText(item, "medicationName"),
                optText(item, "medicationChangeSummary"),
                optText(item, "medicationScheduleNote"),
                MedicationComparisonDecision.fromValue(
                        optText(item, "medicationComparisonDecisionCode")),
                optText(item, "medicationComparisonNote"),
                optText(item, "nextVisitAt"));
    }

    private RealtimeSnapshot parseRealtime(JSONObject item) throws JSONException {
        String realtimeTopic = requireText(item, "realtimeTopic");
        List<CompanionChatMessage> messages = new ArrayList<>();
        JSONArray messageItems = item.optJSONArray("messages");
        if (messageItems != null) {
            for (int index = 0; index < messageItems.length(); index++) {
                JSONObject message = messageItems.optJSONObject(index);
                if (message == null) {
                    continue;
                }
                List<CompanionChatAttachment> attachments = new ArrayList<>();
                JSONArray attachmentItems = message.optJSONArray("attachments");
                if (attachmentItems != null) {
                    for (int attachmentIndex = 0;
                         attachmentIndex < attachmentItems.length();
                         attachmentIndex++) {
                        JSONObject attachment = attachmentItems.optJSONObject(attachmentIndex);
                        if (attachment == null) {
                            continue;
                        }
                        attachments.add(new CompanionChatAttachment(
                                optText(attachment, "storagePath"),
                                optText(attachment, "fileName"),
                                optText(attachment, "contentType"),
                                parseInstantMillis(optText(message, "sentAt")),
                                Math.max(attachment.optLong("sizeBytes", 0L), 0L),
                                ""));
                    }
                }
                messages.add(new CompanionChatMessage(
                        requireText(message, "id"),
                        SafeEnumParser.parseOrDefault(
                                UserRole.class,
                                optText(message, "senderRole"),
                                UserRole.MANAGER),
                        optText(message, "body"),
                        parseInstantMillis(optText(message, "sentAt")),
                        attachments));
            }
        }

        List<ReadReceiptSnapshot> receipts = new ArrayList<>();
        JSONArray receiptItems = item.optJSONArray("readReceipts");
        if (receiptItems != null) {
            for (int index = 0; index < receiptItems.length(); index++) {
                JSONObject receipt = receiptItems.optJSONObject(index);
                if (receipt == null) {
                    continue;
                }
                receipts.add(new ReadReceiptSnapshot(
                        SafeEnumParser.parseOrDefault(
                                UserRole.class,
                                optText(receipt, "userRole"),
                                UserRole.PATIENT),
                        parseInstantMillis(optText(receipt, "lastReadAt"))));
            }
        }

        List<CompanionLocationHistoryEntry> locations = new ArrayList<>();
        JSONArray locationItems = item.optJSONArray("locations");
        if (locationItems != null) {
            for (int index = locationItems.length() - 1; index >= 0; index--) {
                JSONObject location = locationItems.optJSONObject(index);
                if (location == null) {
                    continue;
                }
                locations.add(new CompanionLocationHistoryEntry(
                        location.getDouble("latitude"),
                        location.getDouble("longitude"),
                        "",
                        parseInstantMillis(optText(location, "capturedAt"))));
            }
        }
        return new RealtimeSnapshot(realtimeTopic, messages, receipts, locations);
    }

    private static long parseInstantMillis(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        try {
            return Instant.parse(value.trim()).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return 0L;
        }
    }

    private String requireText(JSONObject object, String key) throws JSONException {
        String value = optText(object, key);
        if (value.isEmpty()) {
            throw new JSONException(key + " 값이 없습니다.");
        }
        return value;
    }

    private String optText(JSONObject object, String key) {
        if (object == null || object.isNull(key)) {
            return "";
        }
        return valueOrEmpty(object.optString(key, ""));
    }

    private static String valueOrEmpty(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    static final class SessionSnapshot {
        private final String coreId;
        private final String legacyFirestoreId;
        private final String appointmentRequestId;
        private final String managerUserId;
        private final int currentStepOrder;
        private final int totalStepCount;
        private final SessionStatus status;
        private final String guardianUpdate;
        private final String locationSummary;
        private final String fieldPhotoNote;
        private final String medicationNote;
        private final String pharmacySummary;
        private final boolean prescriptionCollected;
        private final boolean pharmacyCompleted;
        private final boolean medicationGuidanceCompleted;
        private final boolean liveLocationSharingActive;
        private final String liveLocationSharingStartedAt;
        private final String locationAlertStage;
        private final String locationAlertSentAt;
        private final long version;

        private SessionSnapshot(
                String coreId,
                String legacyFirestoreId,
                String appointmentRequestId,
                String managerUserId,
                int currentStepOrder,
                int totalStepCount,
                SessionStatus status,
                String guardianUpdate,
                String locationSummary,
                String fieldPhotoNote,
                String medicationNote,
                String pharmacySummary,
                boolean prescriptionCollected,
                boolean pharmacyCompleted,
                boolean medicationGuidanceCompleted,
                boolean liveLocationSharingActive,
                String liveLocationSharingStartedAt,
                String locationAlertStage,
                String locationAlertSentAt,
                long version
        ) {
            this.coreId = coreId;
            this.legacyFirestoreId = legacyFirestoreId;
            this.appointmentRequestId = appointmentRequestId;
            this.managerUserId = managerUserId;
            this.currentStepOrder = currentStepOrder;
            this.totalStepCount = totalStepCount;
            this.status = status;
            this.guardianUpdate = guardianUpdate;
            this.locationSummary = locationSummary;
            this.fieldPhotoNote = fieldPhotoNote;
            this.medicationNote = medicationNote;
            this.pharmacySummary = pharmacySummary;
            this.prescriptionCollected = prescriptionCollected;
            this.pharmacyCompleted = pharmacyCompleted;
            this.medicationGuidanceCompleted = medicationGuidanceCompleted;
            this.liveLocationSharingActive = liveLocationSharingActive;
            this.liveLocationSharingStartedAt = liveLocationSharingStartedAt;
            this.locationAlertStage = locationAlertStage;
            this.locationAlertSentAt = locationAlertSentAt;
            this.version = version;
        }

        String getExternalId() {
            return legacyFirestoreId.isEmpty() ? coreId : legacyFirestoreId;
        }

        String getCoreAppointmentId() {
            return appointmentRequestId;
        }

        String getCoreId() {
            return coreId;
        }

        SessionStatus getStatus() {
            return status;
        }

        int getTotalStepCount() {
            return totalStepCount;
        }

        CompanionSession merge(
                @Nullable CompanionSession legacySession,
                @Nullable String legacyAppointmentId
        ) {
            CompanionSession result = legacySession;
            if (result == null) {
                result = new CompanionSession(
                        getExternalId(),
                        valueOrEmpty(legacyAppointmentId).isEmpty()
                                ? appointmentRequestId
                                : valueOrEmpty(legacyAppointmentId),
                        managerUserId,
                        currentStepOrder,
                        status,
                        guardianUpdate,
                        locationSummary,
                        fieldPhotoNote,
                        medicationNote,
                        pharmacySummary,
                        pharmacyCompleted);
            } else {
                result.setCurrentStepOrder(currentStepOrder);
                result.setStatus(status);
                result.setGuardianUpdate(guardianUpdate);
                result.setLocationSummary(locationSummary);
                result.setFieldPhotoNote(fieldPhotoNote);
                result.setMedicationNote(medicationNote);
                result.setPharmacySummary(pharmacySummary);
                result.setPharmacyCompleted(pharmacyCompleted);
            }
            result.setPrescriptionCollected(prescriptionCollected);
            result.setMedicationGuidanceCompleted(medicationGuidanceCompleted);
            result.setRealtimeSessionId(coreId);
            result.updateLiveLocationSharing(
                    liveLocationSharingActive,
                    parseInstantMillis(liveLocationSharingStartedAt));
            result.setLocationAlertStage(CompanionLocationAlertStage.fromValue(locationAlertStage));
            result.setLocationAlertSentAtMillis(parseInstantMillis(locationAlertSentAt));
            return result;
        }
    }

    static final class RealtimeSnapshot {
        private final String realtimeTopic;
        private final List<CompanionChatMessage> messages;
        private final List<ReadReceiptSnapshot> readReceipts;
        private final List<CompanionLocationHistoryEntry> locations;

        private RealtimeSnapshot(
                String realtimeTopic,
                List<CompanionChatMessage> messages,
                List<ReadReceiptSnapshot> readReceipts,
                List<CompanionLocationHistoryEntry> locations
        ) {
            this.realtimeTopic = realtimeTopic;
            this.messages = messages;
            this.readReceipts = readReceipts;
            this.locations = locations;
        }

        String getRealtimeTopic() {
            return realtimeTopic;
        }

        String lastMessageId() {
            return messages.isEmpty() ? "" : messages.get(messages.size() - 1).getId();
        }

        void applyTo(CompanionSession session) {
            session.replaceChatMessages(messages);
            List<CompanionLocationHistoryEntry> summarizedLocations = new ArrayList<>();
            for (CompanionLocationHistoryEntry location : locations) {
                summarizedLocations.add(new CompanionLocationHistoryEntry(
                        location.getLatitude(),
                        location.getLongitude(),
                        session.getLocationSummary(),
                        location.getCapturedAtMillis()));
            }
            session.replaceSharedLocationHistory(summarizedLocations);
            session.clearChatReadState();
            for (ReadReceiptSnapshot receipt : readReceipts) {
                session.markChatRead(receipt.role, receipt.readAtMillis);
            }
        }
    }

    private static final class ReadReceiptSnapshot {
        private final UserRole role;
        private final long readAtMillis;

        private ReadReceiptSnapshot(UserRole role, long readAtMillis) {
            this.role = role;
            this.readAtMillis = readAtMillis;
        }
    }

    static final class ReportSnapshot {
        private final String coreId;
        private final String legacyFirestoreId;
        private final String companionSessionId;
        private final String summary;
        private final String treatmentNotes;
        private final String medicationNotes;
        private final String medicationName;
        private final String medicationChangeSummary;
        private final String medicationScheduleNote;
        @Nullable
        private final MedicationComparisonDecision medicationComparisonDecision;
        private final String medicationComparisonNote;
        private final String nextVisitAt;

        private ReportSnapshot(
                String coreId,
                String legacyFirestoreId,
                String companionSessionId,
                String summary,
                String treatmentNotes,
                String medicationNotes,
                String medicationName,
                String medicationChangeSummary,
                String medicationScheduleNote,
                @Nullable MedicationComparisonDecision medicationComparisonDecision,
                String medicationComparisonNote,
                String nextVisitAt
        ) {
            this.coreId = coreId;
            this.legacyFirestoreId = legacyFirestoreId;
            this.companionSessionId = companionSessionId;
            this.summary = summary;
            this.treatmentNotes = treatmentNotes;
            this.medicationNotes = medicationNotes;
            this.medicationName = medicationName;
            this.medicationChangeSummary = medicationChangeSummary;
            this.medicationScheduleNote = medicationScheduleNote;
            this.medicationComparisonDecision = medicationComparisonDecision;
            this.medicationComparisonNote = medicationComparisonNote;
            this.nextVisitAt = nextVisitAt;
        }

        SessionReport toModel(String externalSessionId) {
            return new SessionReport(
                    legacyFirestoreId.isEmpty() ? coreId : legacyFirestoreId,
                    valueOrEmpty(externalSessionId).isEmpty()
                            ? companionSessionId
                            : externalSessionId,
                    summary,
                    treatmentNotes,
                    medicationNotes,
                    medicationName,
                    medicationChangeSummary,
                    medicationScheduleNote,
                    medicationComparisonDecision,
                    medicationComparisonNote,
                    nextVisitAt);
        }
    }
}
