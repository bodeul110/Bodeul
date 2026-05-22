package com.example.bodeul.ui.health;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
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
            boolean isFirebaseBacked
    ) {
        AppointmentRequest request = detail.getAppointmentRequest();
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
                context.getString(R.string.health_info_profile_section),
                context.getString(R.string.health_info_request_section),
                createProfileLines(detail),
                createRequestLines(detail),
                context.getString(R.string.health_info_action_open_booking_status)
        );
    }

    private List<HealthInfoLineItem> createProfileLines(AppointmentRequestDetail detail) {
        AppointmentRequest request = detail.getAppointmentRequest();
        List<HealthInfoLineItem> items = new ArrayList<>();
        items.add(new HealthInfoLineItem(
                context.getString(R.string.health_info_line_patient),
                resolvePatientName(detail),
                true
        ));
        addLine(items, R.string.health_info_line_guardian, resolveGuardianName(detail), false);
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
}
