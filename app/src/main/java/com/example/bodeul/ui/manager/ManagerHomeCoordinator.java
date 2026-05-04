package com.example.bodeul.ui.manager;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 매니저 홈에 필요한 도메인 데이터를 피그마 기준 화면 모델로 조합한다.
 */
public final class ManagerHomeCoordinator {
    private final Context context;
    private final ManagerHomePresentationFormatter formatter;

    public ManagerHomeCoordinator(Context context, ManagerHomePresentationFormatter formatter) {
        this.context = context.getApplicationContext();
        this.formatter = formatter;
    }

    public ManagerHomeScreenModel createScreenModel(
            User currentUser,
            ManagerHomeProfile profile,
            @Nullable ManagerDashboard dashboard,
            boolean isFirebaseBacked
    ) {
        ManagerHomeHeroModel heroModel = dashboard == null
                ? createWaitingHeroModel()
                : createActiveHeroModel(dashboard);

        return new ManagerHomeScreenModel(
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(context, isFirebaseBacked),
                context.getString(R.string.manager_home_greeting, currentUser.getName()),
                context.getString(dashboard == null
                        ? R.string.manager_home_subtitle
                        : R.string.manager_home_subtitle_active),
                heroModel,
                createActionCards(profile),
                context.getString(dashboard == null
                        ? R.string.manager_home_service_title
                        : R.string.manager_home_live_section_title),
                context.getString(R.string.manager_home_service_more),
                dashboard == null,
                dashboard == null ? createPromoCards() : Collections.emptyList(),
                dashboard == null ? null : createLiveFeedModel(dashboard),
                dashboard != null
        );
    }

    private ManagerHomeHeroModel createWaitingHeroModel() {
        return new ManagerHomeHeroModel(
                context.getString(R.string.manager_home_waiting_badge),
                context.getString(R.string.manager_home_waiting_status),
                context.getString(R.string.manager_home_waiting_title),
                context.getString(R.string.manager_home_waiting_body),
                context.getString(R.string.manager_home_waiting_button),
                true
        );
    }

    private ManagerHomeHeroModel createActiveHeroModel(ManagerDashboard dashboard) {
        int totalSteps = dashboard.getHospitalGuide().getSteps().size();
        return new ManagerHomeHeroModel(
                context.getString(R.string.manager_home_card_eyebrow),
                formatter.toSessionStatusLabel(dashboard.getSession().getStatus()),
                context.getString(
                        R.string.manager_home_card_title,
                        dashboard.getSession().getCurrentStepOrder(),
                        totalSteps
                ),
                context.getString(
                        R.string.manager_home_card_body,
                        dashboard.getPatient().getName(),
                        dashboard.getAppointmentRequest().getHospitalName()
                ),
                context.getString(R.string.manager_home_card_button),
                true
        );
    }

    private List<ManagerHomeActionCardModel> createActionCards(ManagerHomeProfile profile) {
        List<ManagerHomeActionCardModel> actionCards = new ArrayList<>();
        actionCards.add(new ManagerHomeActionCardModel(
                ManagerHomeActionType.DOCUMENT,
                context.getString(R.string.manager_action_docs_short),
                R.drawable.bg_badge_blue,
                R.color.bodeul_primary,
                context.getString(R.string.manager_action_docs),
                formatter.buildActionDescription(profile.getDocumentSummary(), R.string.manager_action_docs_desc),
                formatter.buildDocumentStatusText(profile)
        ));
        actionCards.add(new ManagerHomeActionCardModel(
                ManagerHomeActionType.SCHEDULE,
                context.getString(R.string.manager_action_schedule_short),
                R.drawable.bg_badge_yellow,
                R.color.bodeul_text_primary,
                context.getString(R.string.manager_action_schedule),
                formatter.buildActionDescription(profile.getAvailabilitySummary(), R.string.manager_action_schedule_desc),
                ""
        ));
        actionCards.add(new ManagerHomeActionCardModel(
                ManagerHomeActionType.HISTORY,
                context.getString(R.string.manager_action_history_short),
                R.drawable.bg_badge_purple,
                R.color.bodeul_primary,
                context.getString(R.string.manager_action_history),
                context.getString(R.string.manager_action_history_desc),
                ""
        ));
        actionCards.add(new ManagerHomeActionCardModel(
                ManagerHomeActionType.SUPPORT,
                context.getString(R.string.manager_action_support_short),
                R.drawable.bg_badge_green,
                R.color.bodeul_success,
                context.getString(R.string.manager_action_support),
                context.getString(R.string.manager_action_support_desc),
                ""
        ));
        return actionCards;
    }

    private List<ManagerHomePromoCardModel> createPromoCards() {
        List<ManagerHomePromoCardModel> promoCards = new ArrayList<>();
        promoCards.add(new ManagerHomePromoCardModel(
                R.drawable.bg_service_thumb_warm,
                context.getString(R.string.manager_home_card_waiting),
                R.drawable.bg_badge_blue,
                R.color.bodeul_primary,
                context.getString(R.string.manager_home_service_card_title),
                context.getString(R.string.manager_home_service_card_body)
        ));
        promoCards.add(new ManagerHomePromoCardModel(
                R.drawable.bg_service_thumb_cool,
                context.getString(R.string.guide_actions_title),
                R.drawable.bg_badge_green,
                R.color.bodeul_success,
                context.getString(R.string.manager_home_service_card_secondary_title),
                context.getString(R.string.manager_home_service_card_secondary_body)
        ));
        return promoCards;
    }

    private ManagerHomeLiveFeedModel createLiveFeedModel(ManagerDashboard dashboard) {
        return new ManagerHomeLiveFeedModel(
                R.drawable.bg_service_thumb_warm,
                formatter.toSessionStatusLabel(dashboard.getSession().getStatus()),
                dashboard.getAppointmentRequest().getAppointmentAt(),
                context.getString(
                        R.string.manager_home_live_title,
                        dashboard.getPatient().getName()
                ),
                formatter.buildLiveFeedSubtitle(dashboard),
                formatter.buildLiveFeedNote(dashboard),
                formatter.buildLiveFeedFooter(dashboard)
        );
    }
}
