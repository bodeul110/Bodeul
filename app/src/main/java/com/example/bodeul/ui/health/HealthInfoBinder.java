package com.example.bodeul.ui.health;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public final class HealthInfoBinder {
    private final Context context;
    private final LayoutInflater inflater;
    private final TextView textMode;
    private final TextView textTitle;
    private final TextView textSubtitle;
    private final TextView textHeroBadge;
    private final TextView textHeroTitle;
    private final TextView textHeroBody;
    private final MaterialButton buttonServiceTab;
    private final MaterialButton buttonProfileTab;
    private final MaterialButton buttonSupportTab;
    private final MaterialCardView serviceCard;
    private final MaterialCardView accountCard;
    private final MaterialCardView profileCard;
    private final MaterialCardView requestCard;
    private final MaterialCardView historyCard;
    private final MaterialCardView supportCard;
    private final TextView textServiceSectionTitle;
    private final TextView textServiceSectionHelper;
    private final LinearLayout serviceLineContainer;
    private final TextView textAccountSectionTitle;
    private final TextView textAccountSectionHelper;
    private final TextView textProfileSectionTitle;
    private final TextView textProfileSectionHelper;
    private final TextView textRequestSectionTitle;
    private final TextView textRequestSectionHelper;
    private final TextView textHistorySectionTitle;
    private final TextView textHistorySectionHelper;
    private final TextView textSupportSectionTitle;
    private final TextView textSupportSectionHelper;
    private final LinearLayout accountLineContainer;
    private final LinearLayout profileLineContainer;
    private final LinearLayout requestLineContainer;
    private final LinearLayout historyLineContainer;
    private final LinearLayout supportLineContainer;
    private final MaterialButton buttonHistory;
    private final MaterialButton buttonBooking;
    private final MaterialButton buttonBookingStatus;
    private final MaterialButton buttonGuardianReport;
    private final MaterialButton buttonSupport;
    private final MaterialButton buttonPrimary;

    public HealthInfoBinder(
            Context context,
            LayoutInflater inflater,
            TextView textMode,
            TextView textTitle,
            TextView textSubtitle,
            TextView textHeroBadge,
            TextView textHeroTitle,
            TextView textHeroBody,
            MaterialButton buttonServiceTab,
            MaterialButton buttonProfileTab,
            MaterialButton buttonSupportTab,
            MaterialCardView serviceCard,
            MaterialCardView accountCard,
            MaterialCardView profileCard,
            MaterialCardView requestCard,
            MaterialCardView historyCard,
            MaterialCardView supportCard,
            TextView textServiceSectionTitle,
            TextView textServiceSectionHelper,
            LinearLayout serviceLineContainer,
            TextView textAccountSectionTitle,
            TextView textAccountSectionHelper,
            TextView textProfileSectionTitle,
            TextView textProfileSectionHelper,
            TextView textRequestSectionTitle,
            TextView textRequestSectionHelper,
            TextView textHistorySectionTitle,
            TextView textHistorySectionHelper,
            TextView textSupportSectionTitle,
            TextView textSupportSectionHelper,
            LinearLayout accountLineContainer,
            LinearLayout profileLineContainer,
            LinearLayout requestLineContainer,
            LinearLayout historyLineContainer,
            LinearLayout supportLineContainer,
            MaterialButton buttonHistory,
            MaterialButton buttonBooking,
            MaterialButton buttonBookingStatus,
            MaterialButton buttonGuardianReport,
            MaterialButton buttonSupport,
            MaterialButton buttonPrimary
    ) {
        this.context = context;
        this.inflater = inflater;
        this.textMode = textMode;
        this.textTitle = textTitle;
        this.textSubtitle = textSubtitle;
        this.textHeroBadge = textHeroBadge;
        this.textHeroTitle = textHeroTitle;
        this.textHeroBody = textHeroBody;
        this.buttonServiceTab = buttonServiceTab;
        this.buttonProfileTab = buttonProfileTab;
        this.buttonSupportTab = buttonSupportTab;
        this.serviceCard = serviceCard;
        this.accountCard = accountCard;
        this.profileCard = profileCard;
        this.requestCard = requestCard;
        this.historyCard = historyCard;
        this.supportCard = supportCard;
        this.textServiceSectionTitle = textServiceSectionTitle;
        this.textServiceSectionHelper = textServiceSectionHelper;
        this.serviceLineContainer = serviceLineContainer;
        this.textAccountSectionTitle = textAccountSectionTitle;
        this.textAccountSectionHelper = textAccountSectionHelper;
        this.textProfileSectionTitle = textProfileSectionTitle;
        this.textProfileSectionHelper = textProfileSectionHelper;
        this.textRequestSectionTitle = textRequestSectionTitle;
        this.textRequestSectionHelper = textRequestSectionHelper;
        this.textHistorySectionTitle = textHistorySectionTitle;
        this.textHistorySectionHelper = textHistorySectionHelper;
        this.textSupportSectionTitle = textSupportSectionTitle;
        this.textSupportSectionHelper = textSupportSectionHelper;
        this.accountLineContainer = accountLineContainer;
        this.profileLineContainer = profileLineContainer;
        this.requestLineContainer = requestLineContainer;
        this.historyLineContainer = historyLineContainer;
        this.supportLineContainer = supportLineContainer;
        this.buttonHistory = buttonHistory;
        this.buttonBooking = buttonBooking;
        this.buttonBookingStatus = buttonBookingStatus;
        this.buttonGuardianReport = buttonGuardianReport;
        this.buttonSupport = buttonSupport;
        this.buttonPrimary = buttonPrimary;
    }

    public void bindScreen(HealthInfoScreenModel screenModel, HealthInfoTab selectedTab) {
        EnvironmentModeBadgeHelper.bind(textMode, screenModel.getModeLabel());
        textTitle.setText(screenModel.getTitle());
        textSubtitle.setText(screenModel.getSubtitle());
        textHeroBadge.setText(screenModel.getHeroBadge());
        textHeroTitle.setText(screenModel.getHeroTitle());
        textHeroBody.setText(screenModel.getHeroBody());
        buttonServiceTab.setText(screenModel.getServiceTabLabel());
        buttonProfileTab.setText(screenModel.getProfileTabLabel());
        buttonSupportTab.setText(screenModel.getSupportTabLabel());
        textServiceSectionTitle.setText(screenModel.getServiceSectionTitle());
        textServiceSectionHelper.setText(screenModel.getServiceSectionHelper());
        bindLines(serviceLineContainer, screenModel.getServiceLines());
        textAccountSectionTitle.setText(screenModel.getAccountSectionTitle());
        textAccountSectionHelper.setText(screenModel.getAccountSectionHelper());
        textProfileSectionTitle.setText(screenModel.getProfileSectionTitle());
        textProfileSectionHelper.setText(screenModel.getProfileSectionHelper());
        textRequestSectionTitle.setText(screenModel.getRequestSectionTitle());
        textRequestSectionHelper.setText(screenModel.getRequestSectionHelper());
        textHistorySectionTitle.setText(screenModel.getHistorySectionTitle());
        textHistorySectionHelper.setText(screenModel.getHistorySectionHelper());
        textSupportSectionTitle.setText(screenModel.getSupportSectionTitle());
        textSupportSectionHelper.setText(screenModel.getSupportSectionHelper());
        buttonHistory.setText(screenModel.getHistoryActionLabel());
        buttonBooking.setText(screenModel.getBookingActionLabel());
        buttonBookingStatus.setText(screenModel.getBookingStatusActionLabel());
        bindOptionalButton(buttonGuardianReport, screenModel.getGuardianReportActionLabel());
        bindOptionalButton(buttonSupport, screenModel.getSupportActionLabel());
        buttonPrimary.setText(screenModel.getPrimaryActionLabel());
        bindLines(accountLineContainer, screenModel.getAccountLines());
        bindLines(profileLineContainer, screenModel.getProfileLines());
        bindLines(requestLineContainer, screenModel.getRequestLines());
        bindLines(historyLineContainer, screenModel.getHistoryLines());
        bindLines(supportLineContainer, screenModel.getSupportLines());
        bindSelectedTab(selectedTab);
    }

    private void bindSelectedTab(HealthInfoTab selectedTab) {
        bindTabButton(buttonServiceTab, selectedTab == HealthInfoTab.SERVICE);
        bindTabButton(buttonProfileTab, selectedTab == HealthInfoTab.PROFILE);
        bindTabButton(buttonSupportTab, selectedTab == HealthInfoTab.SUPPORT);

        serviceCard.setVisibility(selectedTab == HealthInfoTab.SERVICE ? View.VISIBLE : View.GONE);
        requestCard.setVisibility(selectedTab == HealthInfoTab.SERVICE ? View.VISIBLE : View.GONE);
        historyCard.setVisibility(selectedTab == HealthInfoTab.SERVICE ? View.VISIBLE : View.GONE);
        accountCard.setVisibility(selectedTab == HealthInfoTab.PROFILE ? View.VISIBLE : View.GONE);
        profileCard.setVisibility(selectedTab == HealthInfoTab.PROFILE ? View.VISIBLE : View.GONE);
        supportCard.setVisibility(selectedTab == HealthInfoTab.SUPPORT ? View.VISIBLE : View.GONE);
    }

    private void bindTabButton(MaterialButton button, boolean selected) {
        int backgroundColor = ContextCompat.getColor(
                context,
                selected ? R.color.bodeul_primary : R.color.bodeul_surface_alt
        );
        int textColor = ContextCompat.getColor(
                context,
                selected ? R.color.white : R.color.bodeul_text_primary
        );
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(backgroundColor));
        button.setTextColor(textColor);
    }

    private void bindOptionalButton(MaterialButton button, @Nullable String label) {
        if (label == null || label.trim().isEmpty()) {
            button.setVisibility(View.GONE);
            return;
        }
        button.setVisibility(View.VISIBLE);
        button.setText(label);
    }

    private void bindLines(LinearLayout container, List<HealthInfoLineItem> items) {
        container.removeAllViews();
        for (int index = 0; index < items.size(); index++) {
            View itemView = inflater.inflate(R.layout.item_booking_status_line, container, false);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            if (index > 0) {
                params.topMargin = dp(10);
            }
            itemView.setLayoutParams(params);

            TextView labelView = itemView.findViewById(R.id.textBookingStatusLineLabel);
            TextView valueView = itemView.findViewById(R.id.textBookingStatusLineValue);
            HealthInfoLineItem item = items.get(index);
            labelView.setText(item.getLabelText());
            valueView.setText(item.getValueText());
            valueView.setTextColor(ContextCompat.getColor(
                    context,
                    item.isEmphasized() ? R.color.bodeul_text_primary : R.color.bodeul_text_secondary
            ));
            valueView.setTextSize(item.isEmphasized() ? 15f : 14f);
            container.addView(itemView);
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
