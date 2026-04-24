package com.example.bodeul.ui.admin;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.ColorRes;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AdminRequestOverview;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.User;

import java.util.List;

/**
 * 관리자 요청 카드와 필터에서 반복되는 문구 규칙을 담당한다.
 */
public final class AdminRequestPresentationFormatter {
    private final Context context;

    public AdminRequestPresentationFormatter(Context context) {
        this.context = context.getApplicationContext();
    }

    public String buildRequestTitle(AdminRequestOverview overview) {
        return context.getString(
                R.string.admin_request_title,
                overview.getAppointmentRequest().getHospitalName(),
                overview.getAppointmentRequest().getDepartmentName()
        );
    }

    public String buildParticipantsText(AdminRequestOverview overview) {
        return context.getString(
                R.string.admin_request_participants,
                buildParticipantDisplay(
                        overview.getPatient(),
                        overview.getAppointmentRequest().getPatientName(),
                        overview.getAppointmentRequest().getPatientPhone(),
                        R.string.admin_participant_patient_missing
                ),
                buildParticipantDisplay(
                        overview.getGuardian(),
                        overview.getAppointmentRequest().getGuardianName(),
                        overview.getAppointmentRequest().getGuardianPhone(),
                        R.string.admin_participant_guardian_missing
                )
        );
    }

    public String buildScheduleText(AdminRequestOverview overview) {
        return context.getString(
                R.string.admin_request_schedule,
                overview.getAppointmentRequest().getAppointmentAt(),
                overview.getAppointmentRequest().getMeetingPlace()
        );
    }

    public String buildManagerText(AdminRequestOverview overview) {
        return context.getString(
                R.string.admin_request_manager,
                overview.getManager() == null
                        ? context.getString(R.string.admin_manager_pending)
                        : overview.getManager().getName()
        );
    }

    public String buildProgressText(AdminRequestOverview overview) {
        CompanionSession session = overview.getSession();
        if (session == null) {
            return toStatusLabel(overview.getAppointmentRequest().getStatus());
        }
        return context.getString(
                R.string.admin_session_progress_value,
                session.getCurrentStepOrder(),
                toSessionStatusLabel(session)
        );
    }

    public String buildDetailToggleText(boolean expanded) {
        return context.getString(expanded
                ? R.string.admin_request_detail_hide
                : R.string.admin_request_detail_show);
    }

    public String buildDetailPanelText(AdminRequestOverview overview) {
        AppointmentStatus requestStatus = overview.getAppointmentRequest().getStatus();
        CompanionSession session = overview.getSession();
        String sessionId = session == null
                ? context.getString(R.string.admin_request_detail_missing)
                : session.getId();
        String sessionStatus = session == null
                ? context.getString(R.string.admin_request_detail_missing)
                : toSessionStatusLabel(session);
        String stepText = session == null
                ? context.getString(R.string.admin_request_detail_missing)
                : context.getString(R.string.admin_request_detail_step_value, session.getCurrentStepOrder());
        String guardianUpdate = session == null || TextUtils.isEmpty(session.getGuardianUpdate())
                ? context.getString(R.string.admin_request_detail_missing)
                : session.getGuardianUpdate();
        String medicationNote = session == null || TextUtils.isEmpty(session.getMedicationNote())
                ? context.getString(R.string.admin_request_detail_missing)
                : session.getMedicationNote();
        String patientLink = buildLinkStateText(
                overview.getAppointmentRequest().getPatientUserId(),
                overview.getAppointmentRequest().getPatientEmail()
        );
        String guardianLink = buildLinkStateText(
                overview.getAppointmentRequest().getGuardianUserId(),
                overview.getAppointmentRequest().getGuardianEmail()
        );
        String managerLink = buildLinkStateText(
                overview.getAppointmentRequest().getManagerUserId(),
                overview.getManager() == null ? "" : overview.getManager().getEmail()
        );

        return context.getString(
                R.string.admin_request_detail_panel,
                overview.getAppointmentRequest().getId(),
                toStatusLabel(requestStatus),
                sessionId,
                sessionStatus,
                stepText,
                patientLink,
                guardianLink,
                managerLink,
                guardianUpdate,
                medicationNote
        );
    }

    public String buildNoteText(AdminRequestOverview overview) {
        return overview.getAppointmentRequest().getSpecialNotes();
    }

    public String resolveBlockingReason(AdminRequestOverview overview, List<User> availableManagers) {
        if (!overview.hasLinkedParticipants()) {
            if (hasParticipantInfo(
                    overview.getPatient(),
                    overview.getAppointmentRequest().getPatientName(),
                    overview.getAppointmentRequest().getPatientPhone(),
                    overview.getAppointmentRequest().getPatientEmail()
            ) && hasParticipantInfo(
                    overview.getGuardian(),
                    overview.getAppointmentRequest().getGuardianName(),
                    overview.getAppointmentRequest().getGuardianPhone(),
                    overview.getAppointmentRequest().getGuardianEmail()
            )) {
                return context.getString(R.string.admin_request_block_pending_link);
            }
            return context.getString(R.string.admin_request_block_missing_participants);
        }
        if (!overview.hasGuide()) {
            return context.getString(R.string.admin_request_block_missing_guide);
        }
        if (availableManagers.isEmpty()) {
            return context.getString(R.string.admin_request_block_no_manager);
        }
        return "";
    }

    public String buildAssignActionText(User manager) {
        return context.getString(R.string.admin_assign_button, manager.getName());
    }

    public String toManagedFilterLabel(AdminManagedRequestFilter filter) {
        switch (filter) {
            case MATCHED:
                return context.getString(R.string.admin_managed_filter_matched);
            case IN_PROGRESS:
                return context.getString(R.string.admin_managed_filter_in_progress);
            case COMPLETED:
                return context.getString(R.string.admin_managed_filter_completed);
            case CANCELED:
                return context.getString(R.string.admin_managed_filter_canceled);
            case ALL:
            default:
                return context.getString(R.string.admin_managed_filter_all);
        }
    }

    public String toManagedDateFilterLabel(AdminManagedRequestDateFilter filter) {
        switch (filter) {
            case TODAY:
                return context.getString(R.string.admin_managed_date_filter_today);
            case UPCOMING:
                return context.getString(R.string.admin_managed_date_filter_upcoming);
            case PAST:
                return context.getString(R.string.admin_managed_date_filter_past);
            case ALL:
            default:
                return context.getString(R.string.admin_managed_date_filter_all);
        }
    }

    public String buildManagedSummary(
            int matchedCount,
            int inProgressCount,
            int completedCount,
            int canceledCount,
            AdminManagedRequestFilter selectedFilter,
            AdminManagedRequestDateFilter selectedDateFilter,
            int visibleCount,
            int totalCount
    ) {
        return context.getString(
                R.string.admin_managed_summary,
                matchedCount,
                inProgressCount,
                completedCount,
                canceledCount,
                toManagedFilterLabel(selectedFilter),
                toManagedDateFilterLabel(selectedDateFilter),
                visibleCount,
                totalCount
        );
    }

    @ColorRes
    public int getStatusBackgroundColorResId(AppointmentStatus status) {
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

    @ColorRes
    public int getStatusTextColorResId(AppointmentStatus status) {
        switch (status) {
            case CANCELED:
            case REQUESTED:
                return R.color.bodeul_text_primary;
            case MATCHED:
            case IN_PROGRESS:
            case COMPLETED:
            default:
                return R.color.white;
        }
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

    private String buildParticipantDisplay(
            User user,
            String snapshotName,
            String snapshotPhone,
            int missingResId
    ) {
        if (user != null) {
            return buildParticipantValue(user.getName(), user.getPhone(), false);
        }
        if (TextUtils.isEmpty(snapshotName) && TextUtils.isEmpty(snapshotPhone)) {
            return context.getString(missingResId);
        }
        return buildParticipantValue(snapshotName, snapshotPhone, true);
    }

    private String buildParticipantValue(String name, String phone, boolean pendingLink) {
        String baseText;
        if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(phone)) {
            baseText = context.getString(R.string.admin_participant_value_name_phone, name, phone);
        } else if (!TextUtils.isEmpty(name)) {
            baseText = name;
        } else if (!TextUtils.isEmpty(phone)) {
            baseText = phone;
        } else {
            baseText = context.getString(R.string.admin_participant_value_unknown);
        }
        return pendingLink
                ? context.getString(R.string.admin_participant_value_pending_link, baseText)
                : baseText;
    }

    private boolean hasParticipantInfo(User user, String name, String phone, String email) {
        if (user != null) {
            return true;
        }
        return !TextUtils.isEmpty(name) || !TextUtils.isEmpty(phone) || !TextUtils.isEmpty(email);
    }

    private String buildLinkStateText(String linkedUserId, String email) {
        if (!TextUtils.isEmpty(linkedUserId)) {
            if (!TextUtils.isEmpty(email)) {
                return context.getString(R.string.admin_request_detail_linked_email, linkedUserId, email);
            }
            return context.getString(R.string.admin_request_detail_linked, linkedUserId);
        }
        if (!TextUtils.isEmpty(email)) {
            return context.getString(R.string.admin_request_detail_pending_email, email);
        }
        return context.getString(R.string.admin_request_detail_missing);
    }
}
