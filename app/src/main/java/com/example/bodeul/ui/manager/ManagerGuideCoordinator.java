package com.example.bodeul.ui.manager;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.ui.booking.BookingMeetingPointCatalog;
import com.example.bodeul.ui.common.HospitalMapPreviewModel;
import com.example.bodeul.util.CompanionLocationDisplayHelper;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.example.bodeul.util.PharmacyProgressDisplayHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 매니저 동행 가이드 화면에 필요한 상태를 화면 모델로 조합한다.
 */
public final class ManagerGuideCoordinator {
    private final Context context;
    private final ManagerGuidePresentationFormatter formatter;

    public ManagerGuideCoordinator(Context context, ManagerGuidePresentationFormatter formatter) {
        this.context = context.getApplicationContext();
        this.formatter = formatter;
    }

    public ManagerGuideScreenModel createScreenModel(@Nullable ManagerDashboard dashboard, boolean isFirebaseBacked) {
        if (dashboard == null) {
            return createEmptyScreenModel(isFirebaseBacked);
        }

        CompanionSession session = dashboard.getSession();
        SessionReport report = dashboard.getSessionReport();
        List<ManagerGuideStageModel> stages = buildStages(dashboard);
        GuideStep focusStep = findFocusStep(dashboard);

        return new ManagerGuideScreenModel(
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(context, isFirebaseBacked),
                context.getString(R.string.guide_progress_title),
                context.getString(R.string.guide_progress_subtitle),
                formatter.toSessionStatusLabel(session.getStatus()),
                context.getString(R.string.guide_hero_title_format, dashboard.getPatient().getName()),
                formatter.buildHeroBody(dashboard),
                formatter.buildHeroNote(dashboard),
                createMapActions(dashboard),
                buildHospitalMapPreviewModel(dashboard),
                stages,
                createFocusModel(focusStep, session),
                CompanionLocationDisplayHelper.buildLiveSharingStatus(context, session),
                CompanionLocationDisplayHelper.buildLocationHistory(context, session, 3),
                session.getLocationSummary(),
                session.getGuardianUpdate(),
                session.getFieldPhotoNote(),
                session.getMedicationNote(),
                session.getPharmacySummary(),
                PharmacyProgressDisplayHelper.buildStepSummary(context, session),
                buildPrescriptionActionLabel(session),
                buildPharmacyActionLabel(session),
                buildMedicationGuidanceActionLabel(session),
                report == null ? "" : report.getSummary(),
                report == null ? "" : report.getTreatmentNotes(),
                report == null ? "" : report.getMedicationName(),
                report == null ? "" : report.getMedicationChangeSummary(),
                report == null ? "" : report.getMedicationScheduleNote(),
                report == null ? "" : report.getNextVisitAt(),
                buildAdvanceButtonLabel(dashboard),
                isAdvanceEnabled(dashboard),
                context.getString(report == null
                        ? R.string.guide_report_submit
                        : R.string.guide_report_update),
                session.isLiveLocationSharingActive(),
                true
        );
    }

    private ManagerGuideScreenModel createEmptyScreenModel(boolean isFirebaseBacked) {
        return new ManagerGuideScreenModel(
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(context, isFirebaseBacked),
                context.getString(R.string.guide_progress_title),
                context.getString(R.string.guide_progress_subtitle),
                context.getString(R.string.guide_status_pending),
                context.getString(R.string.guide_hero_title_empty),
                context.getString(R.string.guide_hero_body_empty),
                context.getString(R.string.guide_hero_note_empty),
                Collections.emptyList(),
                new HospitalMapPreviewModel(Collections.emptyList(), "", ""),
                Collections.emptyList(),
                new ManagerGuideFocusModel(
                        context.getString(R.string.guide_focus_badge_empty),
                        context.getString(R.string.guide_focus_title_empty),
                        context.getString(R.string.guide_focus_body_empty),
                        context.getString(R.string.guide_focus_preview_label),
                        context.getString(R.string.guide_focus_preview_empty),
                        R.drawable.bg_service_thumb_cool
                ),
                context.getString(R.string.live_location_status_inactive_empty),
                context.getString(R.string.live_location_history_empty),
                "",
                "",
                "",
                "",
                "",
                context.getString(R.string.pharmacy_progress_step_summary_pending),
                context.getString(R.string.guide_prescription_mark_collected),
                context.getString(R.string.guide_pharmacy_mark_completed),
                context.getString(R.string.guide_medication_guidance_mark_completed),
                "",
                "",
                "",
                "",
                "",
                "",
                context.getString(R.string.guide_button_waiting),
                false,
                context.getString(R.string.guide_report_submit),
                false,
                false
        );
    }

    private List<ManagerGuideMapActionModel> createMapActions(ManagerDashboard dashboard) {
        String hospitalName = dashboard.getAppointmentRequest().getHospitalName();
        String departmentName = dashboard.getAppointmentRequest().getDepartmentName();
        String meetingPlace = dashboard.getAppointmentRequest().getMeetingPlace();
        CompanionSession session = dashboard.getSession();
        if (TextUtils.isEmpty(meetingPlace)) {
            meetingPlace = context.getString(R.string.guide_map_default_meeting_place, hospitalName);
        }

        List<ManagerGuideMapActionModel> actions = new ArrayList<>();
        if (!TextUtils.isEmpty(session.getLocationSummary()) || session.hasSharedLocationCoordinates()) {
            actions.add(new ManagerGuideMapActionModel(
                    context.getString(R.string.guide_map_action_shared_title),
                    buildSharedLocationBody(session, meetingPlace),
                    context.getString(R.string.guide_map_action_shared_button),
                    TextUtils.isEmpty(session.getLocationSummary())
                            ? hospitalName + " " + meetingPlace
                            : session.getLocationSummary(),
                    buildSharedLocationDirectUrl(session)
            ));
        }
        actions.add(new ManagerGuideMapActionModel(
                context.getString(R.string.guide_map_action_hospital_title),
                context.getString(R.string.guide_map_action_hospital_body, hospitalName, departmentName),
                context.getString(R.string.guide_map_action_hospital_button),
                hospitalName + " " + departmentName + " 안내 지도",
                resolveHospitalFallbackUrl(hospitalName)
        ));
        actions.add(new ManagerGuideMapActionModel(
                context.getString(R.string.guide_map_action_meeting_title),
                context.getString(R.string.guide_map_action_meeting_body, meetingPlace),
                context.getString(R.string.guide_map_action_meeting_button),
                hospitalName + " " + meetingPlace,
                null
        ));
        actions.add(new ManagerGuideMapActionModel(
                context.getString(R.string.guide_map_action_pharmacy_title),
                context.getString(R.string.guide_map_action_pharmacy_body, hospitalName),
                context.getString(R.string.guide_map_action_pharmacy_button),
                hospitalName + " 인근 약국",
                null
        ));
        return actions;
    }

    private HospitalMapPreviewModel buildHospitalMapPreviewModel(ManagerDashboard dashboard) {
        List<com.example.bodeul.domain.model.BookingMeetingPointOption> pointOptions =
                BookingMeetingPointCatalog.createPointOptions(
                        context,
                        dashboard.getAppointmentRequest().getHospitalName(),
                        dashboard.getAppointmentRequest().getDepartmentName()
                );
        String selectedPointId = BookingMeetingPointCatalog.resolveSelectedPointId(
                pointOptions,
                dashboard.getAppointmentRequest().getMeetingPlace()
        );
        String highlightedPointId = BookingMeetingPointCatalog.POINT_ID_PHARMACY.equals(selectedPointId)
                ? ""
                : BookingMeetingPointCatalog.POINT_ID_PHARMACY;
        return new HospitalMapPreviewModel(pointOptions, selectedPointId, highlightedPointId);
    }

    @Nullable
    private String resolveHospitalFallbackUrl(String hospitalName) {
        if (TextUtils.isEmpty(hospitalName)) {
            return null;
        }
        if (hospitalName.contains("서울대") || hospitalName.contains("서울대학교병원")) {
            return "https://www.snuh.org/intro/locations/map.do";
        }
        return null;
    }

    private List<ManagerGuideStageModel> buildStages(ManagerDashboard dashboard) {
        List<ManagerGuideStageModel> items = new ArrayList<>();
        CompanionSession session = dashboard.getSession();
        int totalSteps = dashboard.getHospitalGuide().getSteps().size();
        for (GuideStep step : dashboard.getHospitalGuide().getSteps()) {
            ManagerGuideStageState state = resolveStageState(session, step, totalSteps);
            items.add(new ManagerGuideStageModel(
                    step.getOrder(),
                    step.getTitle(),
                    formatter.summarize(step.getDescription()),
                    formatter.toStageStateLabel(state),
                    state
            ));
        }
        return items;
    }

    private ManagerGuideFocusModel createFocusModel(GuideStep focusStep, CompanionSession session) {
        return new ManagerGuideFocusModel(
                context.getString(R.string.guide_focus_badge_format, focusStep.getOrder()),
                context.getString(R.string.guide_focus_title_format, focusStep.getOrder(), focusStep.getTitle()),
                focusStep.getDescription(),
                context.getString(R.string.guide_focus_preview_label),
                formatter.buildFocusPreviewBody(focusStep, session),
                formatter.resolveFocusPreviewBackground(focusStep)
        );
    }

    private GuideStep findFocusStep(ManagerDashboard dashboard) {
        List<GuideStep> steps = dashboard.getHospitalGuide().getSteps();
        if (steps.isEmpty()) {
            return new GuideStep(
                    1,
                    context.getString(R.string.guide_steps_title),
                    context.getString(R.string.guide_empty_steps)
            );
        }

        int currentOrder = Math.max(1, dashboard.getSession().getCurrentStepOrder());
        if (dashboard.getSession().getStatus() == SessionStatus.COMPLETED) {
            currentOrder = Math.min(currentOrder, steps.size());
        }
        for (GuideStep step : steps) {
            if (step.getOrder() == currentOrder) {
                return step;
            }
        }
        return steps.get(steps.size() - 1);
    }

    private ManagerGuideStageState resolveStageState(CompanionSession session, GuideStep step, int totalSteps) {
        if (session.getStatus() == SessionStatus.COMPLETED) {
            return step.getOrder() <= Math.min(session.getCurrentStepOrder(), totalSteps)
                    ? ManagerGuideStageState.COMPLETED
                    : ManagerGuideStageState.UPCOMING;
        }
        if (step.getOrder() < session.getCurrentStepOrder()) {
            return ManagerGuideStageState.COMPLETED;
        }
        if (step.getOrder() == session.getCurrentStepOrder()) {
            return ManagerGuideStageState.ACTIVE;
        }
        return ManagerGuideStageState.UPCOMING;
    }

    private String buildAdvanceButtonLabel(ManagerDashboard dashboard) {
        CompanionSession session = dashboard.getSession();
        int totalSteps = dashboard.getHospitalGuide().getSteps().size();
        if (session.getStatus() == SessionStatus.COMPLETED) {
            return context.getString(R.string.guide_button_done);
        }
        if (session.getCurrentStepOrder() >= totalSteps) {
            return context.getString(R.string.guide_button_last);
        }
        return context.getString(R.string.guide_button_next);
    }

    private boolean isAdvanceEnabled(ManagerDashboard dashboard) {
        CompanionSession session = dashboard.getSession();
        int totalSteps = dashboard.getHospitalGuide().getSteps().size();
        return session.getStatus() != SessionStatus.COMPLETED
                && session.getCurrentStepOrder() < totalSteps;
    }

    private String buildPharmacyActionLabel(CompanionSession session) {
        return context.getString(session.isPharmacyCompleted()
                ? R.string.guide_pharmacy_mark_incomplete
                : R.string.guide_pharmacy_mark_completed);
    }

    private String buildPrescriptionActionLabel(CompanionSession session) {
        return context.getString(session.isPrescriptionCollected()
                ? R.string.guide_prescription_mark_pending
                : R.string.guide_prescription_mark_collected);
    }

    private String buildMedicationGuidanceActionLabel(CompanionSession session) {
        return context.getString(session.isMedicationGuidanceCompleted()
                ? R.string.guide_medication_guidance_mark_pending
                : R.string.guide_medication_guidance_mark_completed);
    }

    private String buildSharedLocationBody(CompanionSession session, String fallbackPlace) {
        String locationText = TextUtils.isEmpty(session.getLocationSummary())
                ? fallbackPlace
                : session.getLocationSummary();
        if (session.getSharedLocationUpdatedAtMillis() <= 0L) {
            return context.getString(R.string.guide_map_action_shared_body, locationText);
        }
        return context.getString(
                R.string.guide_map_action_shared_body_with_time,
                locationText,
                CompanionLocationDisplayHelper.formatSharedLocationTime(
                        session.getSharedLocationUpdatedAtMillis()
                )
        );
    }

    @Nullable
    private String buildSharedLocationDirectUrl(CompanionSession session) {
        if (!session.hasSharedLocationCoordinates()) {
            return null;
        }
        return String.format(
                Locale.US,
                "kakaomap://look?p=%1$.6f,%2$.6f",
                session.getSharedLatitude(),
                session.getSharedLongitude()
        );
    }
}
