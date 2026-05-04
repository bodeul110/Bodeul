package com.example.bodeul.ui.report;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButton;

/**
 * 보호자 진행 화면 모델을 실제 뷰에 렌더링한다.
 */
public final class GuardianReportDashboardBinder {
    private final Context context;
    private final LayoutInflater inflater;
    private final GuardianReportEntryCardBinder entryCardBinder;
    private final TextView textMode;
    private final TextView textGreeting;
    private final TextView textSummary;
    private final TextView textHighlightStatus;
    private final TextView textHighlightTitle;
    private final TextView textHighlightBody;
    private final MaterialButton buttonHighlightAction;
    private final LinearLayout entryContainer;

    public GuardianReportDashboardBinder(
            Context context,
            LayoutInflater inflater,
            GuardianReportEntryCardBinder entryCardBinder,
            TextView textMode,
            TextView textGreeting,
            TextView textSummary,
            TextView textHighlightStatus,
            TextView textHighlightTitle,
            TextView textHighlightBody,
            MaterialButton buttonHighlightAction,
            LinearLayout entryContainer
    ) {
        this.context = context.getApplicationContext();
        this.inflater = inflater;
        this.entryCardBinder = entryCardBinder;
        this.textMode = textMode;
        this.textGreeting = textGreeting;
        this.textSummary = textSummary;
        this.textHighlightStatus = textHighlightStatus;
        this.textHighlightTitle = textHighlightTitle;
        this.textHighlightBody = textHighlightBody;
        this.buttonHighlightAction = buttonHighlightAction;
        this.entryContainer = entryContainer;
    }

    public void bindScreen(GuardianReportScreenModel screenModel, GuardianReportEntryCardBinder.Listener listener) {
        EnvironmentModeBadgeHelper.bind(textMode, screenModel.getModeText());
        textGreeting.setText(screenModel.getGreetingText());
        textSummary.setText(screenModel.getSummaryText());
        bindHighlight(screenModel.getHighlightModel(), listener);
        bindEntries(screenModel);
    }

    private void bindHighlight(GuardianReportHighlightModel highlightModel, GuardianReportEntryCardBinder.Listener listener) {
        textHighlightStatus.setText(toStatusLabel(highlightModel.getStatus()));
        tintStatusBadge(textHighlightStatus, highlightModel.getStatus());
        textHighlightTitle.setText(highlightModel.getTitleText());
        textHighlightBody.setText(highlightModel.getBodyText());

        if (highlightModel.getRequestId() == null || highlightModel.getActionLabelText() == null) {
            buttonHighlightAction.setVisibility(View.GONE);
            buttonHighlightAction.setOnClickListener(null);
            return;
        }

        buttonHighlightAction.setVisibility(View.VISIBLE);
        buttonHighlightAction.setText(highlightModel.getActionLabelText());
        buttonHighlightAction.setOnClickListener(view -> listener.onOpenRequestDetail(highlightModel.getRequestId()));
    }

    private void bindEntries(GuardianReportScreenModel screenModel) {
        entryContainer.removeAllViews();
        if (!screenModel.hasEntries()) {
            View emptyPanel = inflater.inflate(R.layout.include_state_panel, entryContainer, false);
            StatePanelHelper.show(
                    emptyPanel,
                    StatePanelHelper.Tone.INFO,
                    context.getString(R.string.state_badge_notice),
                    context.getString(R.string.guardian_report_empty_title),
                    context.getString(R.string.guardian_report_list_empty),
                    null,
                    null,
                    null,
                    null
            );
            entryContainer.addView(emptyPanel);
            return;
        }

        for (GuardianReportEntryCardModel model : screenModel.getEntryCards()) {
            View itemView = inflater.inflate(R.layout.item_guardian_report, entryContainer, false);
            entryCardBinder.bind(itemView, model);
            entryContainer.addView(itemView);
        }
    }

    private void tintStatusBadge(TextView textView, AppointmentStatus status) {
        int backgroundColor;
        int textColor;
        switch (status) {
            case MATCHED:
                backgroundColor = R.color.bodeul_primary;
                textColor = R.color.white;
                break;
            case IN_PROGRESS:
            case COMPLETED:
                backgroundColor = R.color.bodeul_success;
                textColor = R.color.white;
                break;
            case CANCELED:
                backgroundColor = R.color.bodeul_surface_alt;
                textColor = R.color.bodeul_text_primary;
                break;
            case REQUESTED:
            default:
                backgroundColor = R.color.bodeul_warning;
                textColor = R.color.bodeul_text_primary;
                break;
        }

        textView.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, backgroundColor)));
        textView.setTextColor(ContextCompat.getColor(context, textColor));
    }

    private String toStatusLabel(AppointmentStatus status) {
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
}
