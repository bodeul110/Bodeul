package com.example.bodeul.ui.report;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.User;

/**
 * 보호자 진행 화면에서 반복되는 상태 문구를 조합한다.
 */
public final class GuardianReportPresentationFormatter {
    private final Context context;

    public GuardianReportPresentationFormatter(Context context) {
        this.context = context.getApplicationContext();
    }

    public String toStatusLabel(AppointmentStatus status) {
        switch (status) {
            case MATCHED:
                return context.getString(R.string.booking_status_matched);
            case IN_PROGRESS:
                return context.getString(R.string.booking_status_in_progress);
            case COMPLETED:
                return context.getString(R.string.booking_status_completed);
            case CANCELED:
                return context.getString(R.string.booking_status_canceled);
            case REQUESTED:
            default:
                return context.getString(R.string.booking_status_requested);
        }
    }

    public String toSessionStatusLabel(CompanionSession session) {
        switch (session.getStatus()) {
            case READY:
                return context.getString(R.string.guardian_report_session_ready);
            case WAITING:
                return context.getString(R.string.guardian_report_session_waiting);
            case IN_TREATMENT:
                return context.getString(R.string.guardian_report_session_treatment);
            case PAYMENT:
                return context.getString(R.string.guardian_report_session_payment);
            case CANCELED:
                return context.getString(R.string.guardian_report_session_canceled);
            case COMPLETED:
                return context.getString(R.string.guardian_report_session_completed);
            case MEETING:
            default:
                return context.getString(R.string.guardian_report_session_meeting);
        }
    }

    public String buildContactText(String name, String phone, boolean pendingLink) {
        String baseText;
        if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(phone)) {
            baseText = context.getString(R.string.guardian_report_contact_name_phone, name, phone);
        } else if (!TextUtils.isEmpty(name)) {
            baseText = name;
        } else if (!TextUtils.isEmpty(phone)) {
            baseText = phone;
        } else {
            return context.getString(R.string.guardian_report_patient_missing);
        }
        return pendingLink
                ? context.getString(R.string.guardian_report_contact_pending, baseText)
                : baseText;
    }

    public String buildManagerDisplay(@Nullable User manager) {
        if (manager == null) {
            return context.getString(R.string.guardian_report_manager_pending);
        }
        if (TextUtils.isEmpty(manager.getPhone())) {
            return manager.getName();
        }
        return context.getString(
                R.string.guardian_report_contact_name_phone,
                manager.getName(),
                manager.getPhone()
        );
    }

    public String summarize(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        return value.replace('\n', ' ').replace("  ", " ").trim();
    }
}
