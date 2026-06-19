package com.example.bodeul.ui.health;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.ClientSupportCategory;
import com.example.bodeul.domain.model.ClientSupportRequest;
import com.example.bodeul.domain.model.ClientSupportStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.booking.BookingPresentationFormatter;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;

import java.util.ArrayList;
import java.util.List;

public final class HealthInfoCoordinator {
    private final Context context;
    private final BookingPresentationFormatter formatter;

    public HealthInfoCoordinator(Context context, BookingPresentationFormatter formatter) {
        this.context = context.getApplicationContext();
        this.formatter = formatter;
    }

    public HealthInfoScreenModel createScreenModel(
            User currentUser,
            AppointmentRequestDetail detail,
            List<AppointmentRequest> requests,
            List<ClientSupportRequest> supportRequests,
            boolean isFirebaseBacked
    ) {
        AppointmentRequest request = detail.getAppointmentRequest();
        HealthInfoPrimaryActionType primaryActionType = resolvePrimaryActionType(currentUser, request);
        return new HealthInfoScreenModel(
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(context, isFirebaseBacked),
                context.getString(R.string.health_info_title),
                context.getString(
                        currentUser.getRole() == UserRole.GUARDIAN
                                ? R.string.health_info_subtitle_guardian
                                : R.string.health_info_subtitle_patient
                ),
                formatter.toStatusLabel(request.getStatus()),
                context.getString(R.string.health_info_hero_title, resolvePatientName(detail)),
                context.getString(
                        R.string.health_info_hero_body,
                        request.getHospitalName(),
                        request.getDepartmentName(),
                        request.getAppointmentAt()
                ),
                context.getString(R.string.health_info_service_section),
                context.getString(
                        currentUser.getRole() == UserRole.GUARDIAN
                                ? R.string.health_info_service_section_helper_guardian
                                : R.string.health_info_service_section_helper_patient
                ),
                createServiceLines(currentUser, request, requests),
                context.getString(R.string.health_info_action_open_booking),
                context.getString(R.string.health_info_action_open_progress),
                currentUser.getRole() == UserRole.GUARDIAN
                        ? context.getString(R.string.health_info_action_open_guardian_report)
                        : null,
                context.getString(R.string.health_info_action_open_support),
                context.getString(R.string.health_info_account_section),
                context.getString(R.string.health_info_account_section_helper),
                context.getString(R.string.health_info_profile_section),
                context.getString(R.string.health_info_profile_section_helper),
                context.getString(R.string.health_info_request_section),
                context.getString(R.string.health_info_request_section_helper),
                context.getString(R.string.health_info_history_section),
                context.getString(R.string.health_info_history_section_helper),
                context.getString(R.string.health_info_action_open_history),
                context.getString(R.string.health_info_support_section),
                context.getString(R.string.health_info_support_section_helper),
                createAccountLines(currentUser, detail),
                createProfileLines(detail),
                createRequestLines(detail),
                createHistoryLines(requests),
                createSupportLines(supportRequests),
                primaryActionType,
                resolvePrimaryActionLabel(currentUser, request, primaryActionType)
        );
    }

    private List<HealthInfoLineItem> createServiceLines(
            User currentUser,
            AppointmentRequest primaryRequest,
            @Nullable List<AppointmentRequest> requests
    ) {
        List<HealthInfoLineItem> items = new ArrayList<>();
        int totalCount = requests == null ? 0 : requests.size();
        int activeCount = 0;
        int completedCount = 0;
        int canceledCount = 0;
        if (requests != null) {
            for (AppointmentRequest request : requests) {
                if (request.getStatus() == AppointmentStatus.COMPLETED) {
                    completedCount++;
                } else if (request.getStatus() == AppointmentStatus.CANCELED) {
                    canceledCount++;
                } else {
                    activeCount++;
                }
            }
        }
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_service_total),
                context.getString(R.string.health_info_service_total_value, totalCount),
                true
        ));
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_service_primary_status),
                formatter.toStatusLabel(primaryRequest.getStatus()),
                false
        ));
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_service_active),
                context.getString(R.string.health_info_service_active_value, activeCount),
                false
        ));
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_service_completed),
                context.getString(R.string.health_info_service_completed_value, completedCount),
                false
        ));
        if (canceledCount > 0) {
            items.add(new HealthInfoLineItem(
                    context.getString(R.string.health_info_line_service_canceled),
                    context.getString(R.string.health_info_service_canceled_value, canceledCount),
                    false
            ));
        }
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_service_next_action),
                resolveNextActionLabel(currentUser, primaryRequest),
                false
        ));
        return items;
    }

    private String resolveNextActionLabel(User currentUser, AppointmentRequest primaryRequest) {
        if (currentUser.getRole() == UserRole.GUARDIAN) {
            return context.getString(R.string.health_info_service_next_action_guardian_report);
        }
        if (primaryRequest.getStatus() == AppointmentStatus.COMPLETED) {
            return context.getString(R.string.health_info_service_next_action_booking_status);
        }
        return context.getString(R.string.health_info_service_next_action_progress);
    }

    private HealthInfoPrimaryActionType resolvePrimaryActionType(User currentUser, AppointmentRequest primaryRequest) {
        if (currentUser.getRole() == UserRole.GUARDIAN) {
            return HealthInfoPrimaryActionType.OPEN_GUARDIAN_REPORT;
        }
        if (primaryRequest.getStatus() == AppointmentStatus.COMPLETED) {
            return HealthInfoPrimaryActionType.OPEN_BOOKING_STATUS;
        }
        return HealthInfoPrimaryActionType.OPEN_BOOKING_STATUS;
    }

    private String resolvePrimaryActionLabel(
            User currentUser,
            AppointmentRequest primaryRequest,
            HealthInfoPrimaryActionType actionType
    ) {
        switch (actionType) {
            case OPEN_GUARDIAN_REPORT:
                return context.getString(R.string.health_info_action_open_guardian_report);
            case OPEN_BOOKING_STATUS:
                if (currentUser.getRole() != UserRole.GUARDIAN
                        && primaryRequest.getStatus() == AppointmentStatus.COMPLETED) {
                    return context.getString(R.string.health_info_service_next_action_booking_status);
                }
                return context.getString(R.string.health_info_action_open_progress);
            case OPEN_BOOKING:
            default:
                return context.getString(R.string.health_info_action_open_booking);
        }
    }

    private List<HealthInfoLineItem> createAccountLines(User currentUser, AppointmentRequestDetail detail) {
        List<HealthInfoLineItem> items = new ArrayList<>();
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_account_name),
                fallbackValue(currentUser.getName(), R.string.manager_profile_contact_missing),
                true
        ));
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_account_role),
                toRoleLabel(currentUser.getRole()),
                false
        ));
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_account_email),
                fallbackValue(currentUser.getEmail(), R.string.manager_profile_contact_missing),
                false
        ));
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_account_phone),
                fallbackValue(currentUser.getPhone(), R.string.manager_profile_contact_missing),
                false
        ));
        if (currentUser.getRole() == UserRole.GUARDIAN) {
            addLine(items, R.string.health_info_line_patient, resolvePatientName(detail), false);
        } else {
            addLine(items, R.string.health_info_line_guardian, resolveGuardianName(detail), false);
        }
        return items;
    }

    private List<HealthInfoLineItem> createProfileLines(AppointmentRequestDetail detail) {
        AppointmentRequest request = detail.getAppointmentRequest();
        List<HealthInfoLineItem> items = new ArrayList<>();
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_condition),
                fallbackValue(request.getPatientConditionSummary(), R.string.health_info_value_empty),
                false
        ));
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_medication),
                fallbackValue(request.getMedicationSummary(), R.string.health_info_value_empty),
                false
        ));
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_mobility),
                formatter.toMobilityLabel(request.getMobilitySupportCode()),
                false
        ));
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_trip),
                formatter.toTripTypeLabel(request.getTripTypeCode()),
                false
        ));
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_manager_preference),
                formatter.toManagerGenderPreferenceLabel(request.getManagerGenderPreferenceCode()),
                false
        ));
        return items;
    }

    private List<HealthInfoLineItem> createRequestLines(AppointmentRequestDetail detail) {
        AppointmentRequest request = detail.getAppointmentRequest();
        List<HealthInfoLineItem> items = new ArrayList<>();
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_status),
                formatter.toStatusLabel(request.getStatus()),
                true
        ));
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_hospital),
                context.getString(
                        R.string.health_info_hospital_value,
                        request.getHospitalName(),
                        request.getDepartmentName()
                ),
                false
        ));
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_schedule),
                request.getAppointmentAt(),
                false
        ));
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_meeting_place),
                fallbackValue(request.getMeetingPlace(), R.string.booking_status_place_missing),
                false
        ));
        addLine(items, R.string.health_info_line_special_note, request.getSpecialNotes(), false);
        return items;
    }

    private List<HealthInfoLineItem> createHistoryLines(@Nullable List<AppointmentRequest> requests) {
        List<HealthInfoLineItem> items = new ArrayList<>();
        if (requests == null || requests.isEmpty()) {
            items.add(new HealthInfoLineItem(
                    context.getString(R.string.health_info_line_history_summary),
                    context.getString(R.string.health_info_history_empty),
                    false
            ));
            return items;
        }

        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_history_summary),
                context.getString(R.string.health_info_history_total_value, requests.size()),
                true
        ));

        int limit = Math.min(requests.size(), 3);
        for (int index = 0; index < limit; index++) {
            AppointmentRequest request = requests.get(index);
            items.add(new HealthInfoLineItem(
                    context.getString(R.string.health_info_line_history_entry, index + 1),
                    context.getString(
                            R.string.health_info_history_entry_value,
                            request.getHospitalName(),
                            request.getDepartmentName(),
                            request.getAppointmentAt(),
                            formatter.toStatusLabel(request.getStatus())
                    ),
                    index == 0
            ));
        }
        return items;
    }

    private List<HealthInfoLineItem> createSupportLines(List<ClientSupportRequest> supportRequests) {
        List<HealthInfoLineItem> items = new ArrayList<>();
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_support_total),
                context.getString(R.string.health_info_support_total_value, supportRequests.size()),
                true
        ));
        if (supportRequests.isEmpty()) {
            items.add(new HealthInfoLineItem(
                    context.getString(R.string.health_info_line_support_latest_status),
                    context.getString(R.string.health_info_support_empty),
                    false
            ));
            return items;
        }

        ClientSupportRequest latestRequest = supportRequests.get(0);
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_support_latest_status),
                latestRequest.getStatus() == ClientSupportStatus.ANSWERED
                        ? context.getString(R.string.client_support_status_answered)
                        : context.getString(R.string.client_support_status_received),
                false
        ));
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_support_latest_category),
                toSupportCategoryText(latestRequest.getCategory()),
                false
        ));
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_support_latest_title),
                fallbackValue(latestRequest.getTitle(), R.string.health_info_support_empty),
                false
        ));
        if (latestRequest.getStatus() == ClientSupportStatus.ANSWERED
                && !TextUtils.isEmpty(latestRequest.getResponseText())) {
            items.add(new HealthInfoLineItem(
                    context.getString(R.string.health_info_line_support_latest_response),
                    summarizeSupportText(latestRequest.getResponseText()),
                    false
            ));
        }
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_support_latest_time),
                latestRequest.getStatus() == ClientSupportStatus.ANSWERED
                        ? formatter.formatTimestamp(latestRequest.getRespondedAtMillis())
                        : formatter.formatTimestamp(latestRequest.getCreatedAtMillis()),
                false
        ));
        return items;
    }

    private void addLine(List<HealthInfoLineItem> items, int labelResId, @Nullable String value, boolean emphasized) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        items.add(new HealthInfoLineItem(context.getString(labelResId), value, emphasized));
    }

    private String resolvePatientName(AppointmentRequestDetail detail) {
        if (detail.getPatient() != null && !TextUtils.isEmpty(detail.getPatient().getName())) {
            return detail.getPatient().getName();
        }
        return fallbackValue(detail.getAppointmentRequest().getPatientName(), R.string.health_info_value_unknown_patient);
    }

    private String resolveGuardianName(AppointmentRequestDetail detail) {
        if (detail.getGuardian() != null && !TextUtils.isEmpty(detail.getGuardian().getName())) {
            return detail.getGuardian().getName();
        }
        return detail.getAppointmentRequest().getGuardianName();
    }

    private String fallbackValue(@Nullable String value, int fallbackResId) {
        return TextUtils.isEmpty(value) ? context.getString(fallbackResId) : value;
    }

    private String toRoleLabel(UserRole role) {
        switch (role) {
            case GUARDIAN:
                return context.getString(R.string.login_role_guardian);
            case PATIENT:
            default:
                return context.getString(R.string.login_role_patient);
        }
    }

    private String toSupportCategoryText(ClientSupportCategory category) {
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

    private String summarizeSupportText(String value) {
        String normalized = value.replace('\n', ' ').replace("  ", " ").trim();
        if (normalized.length() <= 42) {
            return normalized;
        }
        return normalized.substring(0, 42) + "…";
    }
}
