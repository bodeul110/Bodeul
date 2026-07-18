package com.example.bodeul.data.coreapi;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private SessionSnapshot findKnown(
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
            this.version = version;
        }

        String getExternalId() {
            return legacyFirestoreId.isEmpty() ? coreId : legacyFirestoreId;
        }

        String getCoreAppointmentId() {
            return appointmentRequestId;
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
            return result;
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
