package com.example.bodeul.data.coreapi;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentFollowUpReviewRating;
import com.example.bodeul.domain.model.AppointmentFollowUpSettlementStatus;
import com.example.bodeul.domain.model.AppointmentFollowUpSupportEscalationStatus;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** 완료된 예약의 후기, 정산 확인, 긴급 지원 기록을 Core API에서 관리한다. */
final class CoreApiFollowUpClient {
    private final CoreApiAuthenticatedClient authenticatedClient;

    CoreApiFollowUpClient(Context context) {
        authenticatedClient = new CoreApiAuthenticatedClient(context);
    }

    void getFollowUp(
            String coreAppointmentId,
            String externalAppointmentId,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    ) {
        authenticatedClient.execute(
                (idToken, appCheckToken) -> parse(
                        authenticatedClient.requestJson(
                                "GET",
                                path(coreAppointmentId),
                                null,
                                idToken,
                                appCheckToken),
                        externalAppointmentId),
                callback,
                "예약 후속 기록을 불러오지 못했습니다.",
                "예약 후속 API"
        );
    }

    void saveReview(
            String coreAppointmentId,
            String externalAppointmentId,
            AppointmentFollowUpReviewRating reviewRating,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    ) {
        mutate(
                coreAppointmentId,
                externalAppointmentId,
                "reviewRatingCode",
                reviewRating == null ? "" : reviewRating.getValue(),
                null,
                callback);
    }

    void saveSettlement(
            String coreAppointmentId,
            String externalAppointmentId,
            AppointmentFollowUpSettlementStatus settlementStatus,
            String settlementNote,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    ) {
        mutate(
                coreAppointmentId,
                externalAppointmentId,
                "settlementFollowUpStatus",
                settlementStatus == null ? "" : settlementStatus.getValue(),
                settlementNote,
                callback);
    }

    void saveSupportEscalation(
            String coreAppointmentId,
            String externalAppointmentId,
            AppointmentFollowUpSupportEscalationStatus escalationStatus,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    ) {
        mutate(
                coreAppointmentId,
                externalAppointmentId,
                "supportEscalationStatus",
                escalationStatus == null ? "" : escalationStatus.getValue(),
                null,
                callback);
    }

    private void mutate(
            String coreAppointmentId,
            String externalAppointmentId,
            String field,
            String value,
            @Nullable String settlementNote,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    ) {
        authenticatedClient.execute(
                (idToken, appCheckToken) -> {
                    JSONObject current = authenticatedClient.requestJson(
                            "GET",
                            path(coreAppointmentId),
                            null,
                            idToken,
                            appCheckToken);
                    JSONObject body = new JSONObject();
                    body.put("version", current.getLong("version"));
                    body.put(field, value == null ? "" : value);
                    if (settlementNote != null) {
                        body.put("settlementFollowUpNote", settlementNote);
                    }
                    return parse(
                            authenticatedClient.requestJson(
                                    "PATCH",
                                    path(coreAppointmentId),
                                    body,
                                    idToken,
                                    appCheckToken),
                            externalAppointmentId);
                },
                callback,
                "예약 후속 기록을 저장하지 못했습니다. 최신 상태를 확인한 뒤 다시 시도해 주세요.",
                "예약 후속 API"
        );
    }

    private AppointmentFollowUpRecord parse(JSONObject item, String externalAppointmentId)
            throws JSONException {
        return new AppointmentFollowUpRecord(
                externalAppointmentId,
                AppointmentFollowUpReviewRating.fromValue(item.optString("reviewRatingCode", "")),
                parseInstantMillis(item.optString("reviewSavedAt", "")),
                AppointmentFollowUpSettlementStatus.fromValue(
                        item.optString("settlementFollowUpStatus", "")),
                item.optString("settlementFollowUpNote", "").trim(),
                parseInstantMillis(item.optString("settlementFollowUpSavedAt", "")),
                AppointmentFollowUpSupportEscalationStatus.fromValue(
                        item.optString("supportEscalationStatus", "")),
                parseInstantMillis(item.optString("supportEscalatedAt", "")));
    }

    private String path(String coreAppointmentId) {
        return "/api/appointments/" + coreAppointmentId + "/follow-up";
    }

    private long parseInstantMillis(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            return 0L;
        }
        String normalized = normalizeFraction(value);
        String pattern = normalized.contains(".")
                ? "yyyy-MM-dd'T'HH:mm:ss.SSSX"
                : "yyyy-MM-dd'T'HH:mm:ssX";
        SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
        parser.setLenient(false);
        parser.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            Date parsed = parser.parse(normalized);
            return parsed == null ? 0L : parsed.getTime();
        } catch (ParseException exception) {
            return 0L;
        }
    }

    private String normalizeFraction(String value) {
        int dot = value.indexOf('.');
        if (dot < 0) {
            return value;
        }
        int zone = value.indexOf('Z', dot);
        if (zone < 0) {
            zone = value.indexOf('+', dot);
        }
        if (zone < 0) {
            zone = value.indexOf('-', dot);
        }
        if (zone < 0) {
            return value;
        }
        String fraction = value.substring(dot + 1, zone);
        String millis = (fraction + "000").substring(0, 3);
        return value.substring(0, dot + 1) + millis + value.substring(zone);
    }
}
