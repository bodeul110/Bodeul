package com.example.bodeul.ui.booking;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 사용자 예약 이력 화면에 필요한 표시 모델을 만든다.
 */
public final class ClientBookingHistoryCoordinator {
    private final Context context;
    private final BookingPresentationFormatter formatter;

    public ClientBookingHistoryCoordinator(Context context, BookingPresentationFormatter formatter) {
        this.context = context.getApplicationContext();
        this.formatter = formatter;
    }

    public ClientBookingHistoryScreenModel createScreenModel(
            User currentUser,
            List<AppointmentRequest> requests,
            boolean firebaseBacked
    ) {
        return new ClientBookingHistoryScreenModel(
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(context, firebaseBacked),
                context.getString(R.string.client_booking_history_title),
                context.getString(
                        currentUser.getRole() == UserRole.GUARDIAN
                                ? R.string.client_booking_history_subtitle_guardian
                                : R.string.client_booking_history_subtitle_patient
                ),
                context.getString(R.string.client_booking_history_badge, requests.size()),
                context.getString(R.string.client_booking_history_hero_title, currentUser.getName()),
                buildHeroBody(requests),
                context.getString(R.string.client_booking_history_list_title),
                context.getString(R.string.client_booking_history_list_helper),
                context.getString(R.string.client_booking_history_action_manage),
                createEntryModels(currentUser, requests)
        );
    }

    private String buildHeroBody(List<AppointmentRequest> requests) {
        int activeCount = 0;
        int completedCount = 0;
        int canceledCount = 0;
        for (AppointmentRequest request : requests) {
            if (request.getStatus() == AppointmentStatus.COMPLETED) {
                completedCount++;
            } else if (request.getStatus() == AppointmentStatus.CANCELED) {
                canceledCount++;
            } else {
                activeCount++;
            }
        }
        return context.getString(
                R.string.client_booking_history_hero_body,
                activeCount,
                completedCount,
                canceledCount
        );
    }

    private List<ClientBookingHistoryEntryModel> createEntryModels(User currentUser, List<AppointmentRequest> requests) {
        List<ClientBookingHistoryEntryModel> items = new ArrayList<>();
        for (AppointmentRequest request : requests) {
            items.add(new ClientBookingHistoryEntryModel(
                    request.getId(),
                    formatter.toStatusLabel(request.getStatus()),
                    resolveStatusBackgroundColor(request.getStatus()),
                    resolveStatusTextColor(request.getStatus()),
                    resolveStatusStrokeColor(request.getStatus()),
                    context.getString(R.string.booking_request_title, request.getHospitalName(), request.getDepartmentName()),
                    context.getString(R.string.booking_request_detail, request.getAppointmentAt()),
                    context.getString(R.string.booking_request_place, request.getMeetingPlace()),
                    context.getString(
                            R.string.booking_request_linked,
                            formatter.toCounterpartRoleLabel(currentUser.getRole()),
                            formatter.buildLinkedParticipantLine(request, currentUser.getRole())
                    ),
                    buildOptionLine(request),
                    buildProfileLine(request),
                    buildPriceLine(request),
                    TextUtils.isEmpty(request.getSpecialNotes())
                            ? ""
                            : context.getString(R.string.booking_request_note, request.getSpecialNotes())
            ));
        }
        return items;
    }

    private String buildOptionLine(AppointmentRequest request) {
        if (TextUtils.isEmpty(request.getTripTypeCode()) && TextUtils.isEmpty(request.getManagerGenderPreferenceCode())) {
            return "";
        }
        return context.getString(
                R.string.booking_request_option,
                formatter.toTripTypeLabel(request.getTripTypeCode()),
                formatter.toManagerGenderPreferenceLabel(request.getManagerGenderPreferenceCode())
        );
    }

    private String buildProfileLine(AppointmentRequest request) {
        if (TextUtils.isEmpty(request.getPatientConditionSummary())
                && TextUtils.isEmpty(request.getMedicationSummary())
                && TextUtils.isEmpty(request.getMobilitySupportCode())) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(request.getPatientConditionSummary())) {
            builder.append(context.getString(
                    R.string.booking_request_profile_condition,
                    request.getPatientConditionSummary()
            ));
        }
        if (!TextUtils.isEmpty(request.getMedicationSummary())) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(context.getString(
                    R.string.booking_request_profile_medication,
                    request.getMedicationSummary()
            ));
        }
        if (!TextUtils.isEmpty(request.getMobilitySupportCode())) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(context.getString(
                    R.string.booking_request_profile_mobility,
                    formatter.toMobilityLabel(request.getMobilitySupportCode())
            ));
        }
        return builder.toString();
    }

    private String buildPriceLine(AppointmentRequest request) {
        if (request.getFinalPrice() <= 0) {
            return "";
        }
        return context.getString(
                R.string.booking_request_price,
                formatter.formatPrice(request.getFinalPrice()),
                formatter.toPaymentMethodLabel(request.getPaymentMethodCode()),
                formatter.toCouponLabel(request.getCouponCode())
        );
    }

    private int resolveStatusBackgroundColor(AppointmentStatus status) {
        switch (status) {
            case MATCHED:
                return R.color.bodeul_primary;
            case IN_PROGRESS:
            case COMPLETED:
                return R.color.bodeul_success;
            case CANCELED:
                return R.color.bodeul_surface_alt;
            case REQUESTED:
            default:
                return R.color.bodeul_warning;
        }
    }

    private int resolveStatusTextColor(AppointmentStatus status) {
        switch (status) {
            case REQUESTED:
            case CANCELED:
                return R.color.bodeul_text_primary;
            case MATCHED:
            case IN_PROGRESS:
            case COMPLETED:
            default:
                return R.color.white;
        }
    }

    private int resolveStatusStrokeColor(AppointmentStatus status) {
        switch (status) {
            case MATCHED:
                return R.color.bodeul_primary;
            case IN_PROGRESS:
            case COMPLETED:
                return R.color.bodeul_success;
            case CANCELED:
                return R.color.bodeul_outline;
            case REQUESTED:
            default:
                return R.color.bodeul_warning;
        }
    }
}
