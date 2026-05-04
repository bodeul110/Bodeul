package com.example.bodeul.ui.manager;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.bodeul.R;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * 매니저 홈 화면 모델을 실제 레이아웃 뷰에 연결한다.
 */
public final class ManagerHomeDashboardBinder {
    public interface Listener {
        void onQuickActionSelected(ManagerHomeActionType actionType);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final Listener listener;
    private final TextView textManagerMode;
    private final TextView textManagerGreeting;
    private final TextView textManagerSubtitle;
    private final TextView textManagerHeroBadge;
    private final TextView textManagerHeroStatus;
    private final TextView textManagerHeroTitle;
    private final TextView textManagerHeroBody;
    private final MaterialButton buttonManagerHeroPrimary;
    private final LinearLayout managerActionContainer;
    private final TextView textManagerSectionTitle;
    private final TextView textManagerSectionAction;
    private final View managerPromoScroll;
    private final LinearLayout managerPromoContainer;
    private final View cardManagerLiveFeed;
    private final View viewManagerLiveFeedBanner;
    private final TextView textManagerLiveBadge;
    private final TextView textManagerLiveTime;
    private final TextView textManagerLiveTitle;
    private final TextView textManagerLiveSubtitle;
    private final TextView textManagerLiveNote;
    private final TextView textManagerLiveFooter;
    private final ManagerHomeActionCardBinder actionCardBinder;
    private final ManagerHomePromoCardBinder promoCardBinder;

    public ManagerHomeDashboardBinder(
            Context context,
            LayoutInflater inflater,
            Listener listener,
            TextView textManagerMode,
            TextView textManagerGreeting,
            TextView textManagerSubtitle,
            TextView textManagerHeroBadge,
            TextView textManagerHeroStatus,
            TextView textManagerHeroTitle,
            TextView textManagerHeroBody,
            MaterialButton buttonManagerHeroPrimary,
            LinearLayout managerActionContainer,
            TextView textManagerSectionTitle,
            TextView textManagerSectionAction,
            View managerPromoScroll,
            LinearLayout managerPromoContainer,
            View cardManagerLiveFeed,
            View viewManagerLiveFeedBanner,
            TextView textManagerLiveBadge,
            TextView textManagerLiveTime,
            TextView textManagerLiveTitle,
            TextView textManagerLiveSubtitle,
            TextView textManagerLiveNote,
            TextView textManagerLiveFooter
    ) {
        this.context = context;
        this.inflater = inflater;
        this.listener = listener;
        this.textManagerMode = textManagerMode;
        this.textManagerGreeting = textManagerGreeting;
        this.textManagerSubtitle = textManagerSubtitle;
        this.textManagerHeroBadge = textManagerHeroBadge;
        this.textManagerHeroStatus = textManagerHeroStatus;
        this.textManagerHeroTitle = textManagerHeroTitle;
        this.textManagerHeroBody = textManagerHeroBody;
        this.buttonManagerHeroPrimary = buttonManagerHeroPrimary;
        this.managerActionContainer = managerActionContainer;
        this.textManagerSectionTitle = textManagerSectionTitle;
        this.textManagerSectionAction = textManagerSectionAction;
        this.managerPromoScroll = managerPromoScroll;
        this.managerPromoContainer = managerPromoContainer;
        this.cardManagerLiveFeed = cardManagerLiveFeed;
        this.viewManagerLiveFeedBanner = viewManagerLiveFeedBanner;
        this.textManagerLiveBadge = textManagerLiveBadge;
        this.textManagerLiveTime = textManagerLiveTime;
        this.textManagerLiveTitle = textManagerLiveTitle;
        this.textManagerLiveSubtitle = textManagerLiveSubtitle;
        this.textManagerLiveNote = textManagerLiveNote;
        this.textManagerLiveFooter = textManagerLiveFooter;
        actionCardBinder = new ManagerHomeActionCardBinder(context);
        promoCardBinder = new ManagerHomePromoCardBinder(context);
    }

    public void bindScreen(ManagerHomeScreenModel screenModel) {
        bindHeader(screenModel);
        bindHero(screenModel.getHeroModel());
        bindActionCards(screenModel.getActionCards());
        bindSection(screenModel);
        bindFeed(screenModel);
    }

    private void bindHeader(ManagerHomeScreenModel screenModel) {
        EnvironmentModeBadgeHelper.bind(textManagerMode, screenModel.getModeText());
        textManagerGreeting.setText(screenModel.getGreetingText());
        textManagerSubtitle.setText(screenModel.getSubtitleText());
    }

    private void bindHero(ManagerHomeHeroModel heroModel) {
        textManagerHeroBadge.setText(heroModel.getBadgeText());
        textManagerHeroStatus.setText(heroModel.getStatusText());
        textManagerHeroStatus.setVisibility(TextUtils.isEmpty(heroModel.getStatusText()) ? View.GONE : View.VISIBLE);
        textManagerHeroTitle.setText(heroModel.getTitleText());
        textManagerHeroBody.setText(heroModel.getBodyText());
        buttonManagerHeroPrimary.setText(heroModel.getActionText());
        buttonManagerHeroPrimary.setEnabled(heroModel.isActionEnabled());
        buttonManagerHeroPrimary.setAlpha(heroModel.isActionEnabled() ? 1f : 0.5f);
    }

    private void bindActionCards(List<ManagerHomeActionCardModel> actionCards) {
        managerActionContainer.removeAllViews();
        for (int index = 0; index < actionCards.size(); index += 2) {
            LinearLayout row = createActionRow();
            addActionCard(row, actionCards.get(index), true);
            if (index + 1 < actionCards.size()) {
                addActionCard(row, actionCards.get(index + 1), false);
            }
            managerActionContainer.addView(row);
        }
    }

    private LinearLayout createActionRow() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        if (managerActionContainer.getChildCount() > 0) {
            rowParams.topMargin = dp(16);
        }
        row.setLayoutParams(rowParams);
        return row;
    }

    private void addActionCard(LinearLayout row, ManagerHomeActionCardModel actionCardModel, boolean isStartCard) {
        View itemView = inflater.inflate(R.layout.item_manager_home_action_card, row, false);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) itemView.getLayoutParams();
        if (isStartCard) {
            layoutParams.setMarginEnd(dp(8));
        } else {
            layoutParams.setMarginStart(dp(8));
        }
        itemView.setLayoutParams(layoutParams);
        actionCardBinder.bind(itemView, actionCardModel);
        itemView.setOnClickListener(view -> listener.onQuickActionSelected(actionCardModel.getActionType()));
        row.addView(itemView);
    }

    private void bindSection(ManagerHomeScreenModel screenModel) {
        textManagerSectionTitle.setText(screenModel.getSectionTitleText());
        textManagerSectionAction.setText(screenModel.getSectionActionText());
        textManagerSectionAction.setVisibility(screenModel.isSectionActionVisible() ? View.VISIBLE : View.GONE);
    }

    private void bindFeed(ManagerHomeScreenModel screenModel) {
        ManagerHomeLiveFeedModel liveFeedModel = screenModel.getLiveFeedModel();
        if (liveFeedModel != null) {
            managerPromoScroll.setVisibility(View.GONE);
            cardManagerLiveFeed.setVisibility(View.VISIBLE);
            bindLiveFeed(liveFeedModel);
            return;
        }

        cardManagerLiveFeed.setVisibility(View.GONE);
        managerPromoScroll.setVisibility(View.VISIBLE);
        bindPromoCards(screenModel.getPromoCards());
    }

    private void bindPromoCards(List<ManagerHomePromoCardModel> promoCards) {
        managerPromoContainer.removeAllViews();
        for (int index = 0; index < promoCards.size(); index++) {
            View itemView = inflater.inflate(R.layout.item_manager_home_promo_card, managerPromoContainer, false);
            ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            if (index < promoCards.size() - 1) {
                layoutParams.setMarginEnd(dp(14));
            }
            itemView.setLayoutParams(layoutParams);
            promoCardBinder.bind(itemView, promoCards.get(index));
            managerPromoContainer.addView(itemView);
        }
    }

    private void bindLiveFeed(ManagerHomeLiveFeedModel liveFeedModel) {
        viewManagerLiveFeedBanner.setBackgroundResource(liveFeedModel.getBannerBackgroundResId());
        textManagerLiveBadge.setText(liveFeedModel.getBadgeText());
        textManagerLiveTime.setText(liveFeedModel.getTimeText());
        textManagerLiveTitle.setText(liveFeedModel.getTitleText());
        textManagerLiveSubtitle.setText(liveFeedModel.getSubtitleText());
        textManagerLiveNote.setText(liveFeedModel.getNoteText());
        textManagerLiveFooter.setText(liveFeedModel.getFooterText());
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }
}
