package com.example.bodeul.ui.support;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.ClientSupportCategory;
import com.example.bodeul.domain.model.ClientSupportRequest;
import com.example.bodeul.domain.model.ClientSupportStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.booking.BookingPresentationFormatter;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 사용자 문의 저장 목록을 화면 표현 모델로 변환한다.
 */
public final class ClientSupportCoordinator {
    private final Context context;
    private final BookingPresentationFormatter formatter;

    public ClientSupportCoordinator(Context context, BookingPresentationFormatter formatter) {
        this.context = context.getApplicationContext();
        this.formatter = formatter;
    }

    public ClientSupportScreenModel createScreenModel(
            User currentUser,
            @Nullable AppointmentRequestDetail detail,
            List<ClientSupportRequest> requests,
            boolean firebaseBacked
    ) {
        return new ClientSupportScreenModel(
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(context, firebaseBacked),
                context.getString(R.string.client_support_hero_badge),
                context.getString(R.string.client_support_hero_title),
                context.getString(
                        currentUser.getRole() == UserRole.GUARDIAN
                                ? R.string.client_support_hero_body_guardian
                                : R.string.client_support_hero_body_patient
                ),
                buildRequestSummary(detail),
                buildLatestSummary(requests),
                createRequestCards(requests)
        );
    }

    private String buildRequestSummary(@Nullable AppointmentRequestDetail detail) {
        if (detail == null) {
            return context.getString(R.string.client_support_request_summary_empty);
        }
        AppointmentRequest request = detail.getAppointmentRequest();
        return context.getString(
                R.string.client_support_request_summary_value,
                formatter.toStatusLabel(request.getStatus()),
                request.getHospitalName(),
                request.getDepartmentName(),
                request.getAppointmentAt()
        );
    }

    private String buildLatestSummary(List<ClientSupportRequest> requests) {
        if (requests.isEmpty()) {
            return context.getString(R.string.client_support_latest_empty);
        }
        ClientSupportRequest latestRequest = requests.get(0);
        if (latestRequest.hasUnreadResponse()) {
            return context.getString(
                    R.string.client_support_latest_unread_value,
                    toCategoryText(latestRequest.getCategory()),
                    formatter.formatTimestamp(latestRequest.getRespondedAtMillis())
            );
        }
        if (latestRequest.getStatus() == ClientSupportStatus.ANSWERED
                && !TextUtils.isEmpty(latestRequest.getResponseText())) {
            return context.getString(
                    R.string.client_support_latest_answered_value,
                    toCategoryText(latestRequest.getCategory()),
                    formatter.formatTimestamp(latestRequest.getRespondedAtMillis())
            );
        }
        return context.getString(
                R.string.client_support_latest_value,
                toCategoryText(latestRequest.getCategory()),
                formatter.formatTimestamp(latestRequest.getCreatedAtMillis())
        );
    }

    private List<ClientSupportRequestCardModel> createRequestCards(List<ClientSupportRequest> requests) {
        List<ClientSupportRequestCardModel> cards = new ArrayList<>();
        for (ClientSupportRequest request : requests) {
            boolean answered = request.getStatus() == ClientSupportStatus.ANSWERED
                    && !TextUtils.isEmpty(request.getResponseText());
            boolean unreadResponse = request.hasUnreadResponse();
            cards.add(new ClientSupportRequestCardModel(
                    toCategoryText(request.getCategory()),
                    unreadResponse
                            ? context.getString(R.string.client_support_status_unread_answer)
                            : answered
                            ? context.getString(R.string.client_support_status_answered)
                            : context.getString(R.string.client_support_status_received),
                    unreadResponse
                            ? R.color.bodeul_warning
                            : answered ? R.color.bodeul_soft_green : R.color.bodeul_soft_blue,
                    unreadResponse
                            ? R.color.bodeul_text_primary
                            : answered ? R.color.bodeul_success : R.color.bodeul_primary,
                    request.getTitle(),
                    summarizeText(request.getBody()),
                    formatter.formatTimestamp(request.getCreatedAtMillis()),
                    answered,
                    summarizeText(request.getResponseText()),
                    buildResponseMeta(request)
            ));
        }
        return cards;
    }

    private String buildResponseMeta(ClientSupportRequest request) {
        if (request.getStatus() != ClientSupportStatus.ANSWERED) {
            return "";
        }
        return context.getString(
                request.hasUnreadResponse()
                        ? R.string.client_support_response_meta_unread
                        : R.string.client_support_response_meta,
                formatter.formatTimestamp(request.getRespondedAtMillis()),
                TextUtils.isEmpty(request.getRespondedByName())
                        ? context.getString(R.string.admin_manager_pending)
                        : request.getRespondedByName()
        );
    }

    private String toCategoryText(ClientSupportCategory category) {
        switch (category) {
            case PROGRESS:
                return context.getString(R.string.client_support_category_progress);
            case REPORT:
                return context.getString(R.string.client_support_category_report);
            case SETTLEMENT:
                return context.getString(R.string.client_support_category_settlement);
            case OTHER:
                return context.getString(R.string.client_support_category_other);
            case RESERVATION:
            default:
                return context.getString(R.string.client_support_category_reservation);
        }
    }

    private String summarizeText(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        return value.replace('\n', ' ').replace("  ", " ").trim();
    }
}
