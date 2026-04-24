package com.example.bodeul.ui.manager;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.User;

/**
 * 동행 가이드 화면에서 반복되는 표시 문구 조합을 담당한다.
 */
public final class ManagerGuidePresentationFormatter {
    private final Context context;

    public ManagerGuidePresentationFormatter(Context context) {
        this.context = context.getApplicationContext();
    }

    public String toSessionStatusLabel(SessionStatus status) {
        switch (status) {
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

    public String toStageStateLabel(ManagerGuideStageState state) {
        switch (state) {
            case COMPLETED:
                return context.getString(R.string.guide_status_completed);
            case ACTIVE:
                return context.getString(R.string.guide_status_active);
            case UPCOMING:
            default:
                return context.getString(R.string.guide_status_pending);
        }
    }

    public String summarize(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        return value.replace('\n', ' ').replace("  ", " ").trim();
    }

    public String buildGuardianContact(User guardian) {
        if (guardian == null) {
            return "";
        }
        if (TextUtils.isEmpty(guardian.getPhone())) {
            return guardian.getName();
        }
        return context.getString(
                R.string.booking_status_contact_name_phone,
                guardian.getName(),
                guardian.getPhone()
        );
    }

    public String buildHeroBody(ManagerDashboard dashboard) {
        String place = TextUtils.isEmpty(dashboard.getAppointmentRequest().getMeetingPlace())
                ? context.getString(R.string.booking_status_place_missing)
                : dashboard.getAppointmentRequest().getMeetingPlace();
        return context.getString(
                R.string.guide_hero_body_format,
                dashboard.getAppointmentRequest().getHospitalName(),
                dashboard.getAppointmentRequest().getDepartmentName(),
                dashboard.getAppointmentRequest().getAppointmentAt(),
                place,
                buildGuardianContact(dashboard.getGuardian())
        );
    }

    public String buildHeroNote(ManagerDashboard dashboard) {
        String note = summarize(dashboard.getAppointmentRequest().getSpecialNotes());
        if (TextUtils.isEmpty(note)) {
            return context.getString(R.string.guide_hero_note_empty);
        }
        return context.getString(R.string.guide_hero_note_format, note);
    }

    public String buildFocusPreviewBody(GuideStep step, CompanionSession session) {
        String summary;
        switch (step.getOrder()) {
            case 1:
                summary = firstNonEmpty(session.getLocationSummary(), session.getGuardianUpdate());
                return context.getString(R.string.guide_focus_preview_meeting, fallback(summary));
            case 2:
            case 3:
                summary = firstNonEmpty(session.getFieldPhotoNote(), session.getLocationSummary());
                return context.getString(R.string.guide_focus_preview_document, fallback(summary));
            case 4:
            case 5:
                summary = firstNonEmpty(session.getGuardianUpdate(), session.getFieldPhotoNote());
                return context.getString(R.string.guide_focus_preview_treatment, fallback(summary));
            case 6:
                summary = firstNonEmpty(session.getMedicationNote(), session.getFieldPhotoNote());
                return context.getString(R.string.guide_focus_preview_medication, fallback(summary));
            default:
                summary = firstNonEmpty(session.getGuardianUpdate(), session.getMedicationNote());
                return context.getString(R.string.guide_focus_preview_finish, fallback(summary));
        }
    }

    public int resolveFocusPreviewBackground(GuideStep step) {
        if (step.getOrder() == 1 || step.getOrder() == 4 || step.getOrder() == 7) {
            return R.drawable.bg_service_thumb_cool;
        }
        return R.drawable.bg_service_thumb_warm;
    }

    private String fallback(String value) {
        if (TextUtils.isEmpty(value)) {
            return context.getString(R.string.guide_focus_preview_empty_value);
        }
        return value;
    }

    private String firstNonEmpty(String first, String second) {
        if (!TextUtils.isEmpty(first)) {
            return summarize(first);
        }
        return summarize(second);
    }
}
