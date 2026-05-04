package com.example.bodeul.ui.manager;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
                stages,
                createFocusModel(focusStep, session),
                session.getLocationSummary(),
                session.getGuardianUpdate(),
                session.getFieldPhotoNote(),
                session.getMedicationNote(),
                report == null ? "" : report.getSummary(),
                report == null ? "" : report.getTreatmentNotes(),
                report == null ? "" : report.getNextVisitAt(),
                buildAdvanceButtonLabel(dashboard),
                isAdvanceEnabled(dashboard),
                context.getString(report == null
                        ? R.string.guide_report_submit
                        : R.string.guide_report_update),
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
                new ManagerGuideFocusModel(
                        context.getString(R.string.guide_focus_badge_empty),
                        context.getString(R.string.guide_focus_title_empty),
                        context.getString(R.string.guide_focus_body_empty),
                        context.getString(R.string.guide_focus_preview_label),
                        context.getString(R.string.guide_focus_preview_empty),
                        R.drawable.bg_service_thumb_cool
                ),
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                context.getString(R.string.guide_button_waiting),
                false,
                context.getString(R.string.guide_report_submit),
                false
        );
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
}
