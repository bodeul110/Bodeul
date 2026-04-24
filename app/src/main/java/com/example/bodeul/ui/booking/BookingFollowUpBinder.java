package com.example.bodeul.ui.booking;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * 종료 후 후기·정산·SOS 화면 모델을 실제 뷰에 바인딩한다.
 */
public final class BookingFollowUpBinder {
    private final Context context;
    private final LayoutInflater inflater;
    private final TextView textMode;
    private final TextView textHeroBadge;
    private final TextView textHeroTitle;
    private final TextView textHeroBody;
    private final TextView textReviewTitle;
    private final TextView textReviewBody;
    private final LinearLayout reviewOptionContainer;
    private final TextView textReviewSummary;
    private final TextView textReviewSavedState;
    private final MaterialButton buttonReviewSave;
    private final LinearLayout settlementContainer;
    private final TextView textSettlementSavedState;
    private final MaterialButton buttonSettlementConfirm;
    private final MaterialButton buttonSettlementHelp;
    private final TextView textEmergencyTitle;
    private final TextView textEmergencyBody;
    private final LinearLayout emergencyContainer;
    private final TextView textEmergencySavedState;
    private final MaterialButton buttonCallManager;
    private final BookingFollowUpRatingOptionBinder ratingOptionBinder;

    public BookingFollowUpBinder(
            Context context,
            LayoutInflater inflater,
            TextView textMode,
            TextView textHeroBadge,
            TextView textHeroTitle,
            TextView textHeroBody,
            TextView textReviewTitle,
            TextView textReviewBody,
            LinearLayout reviewOptionContainer,
            TextView textReviewSummary,
            TextView textReviewSavedState,
            MaterialButton buttonReviewSave,
            LinearLayout settlementContainer,
            TextView textSettlementSavedState,
            MaterialButton buttonSettlementConfirm,
            MaterialButton buttonSettlementHelp,
            TextView textEmergencyTitle,
            TextView textEmergencyBody,
            LinearLayout emergencyContainer,
            TextView textEmergencySavedState,
            MaterialButton buttonCallManager
    ) {
        this.context = context;
        this.inflater = inflater;
        this.textMode = textMode;
        this.textHeroBadge = textHeroBadge;
        this.textHeroTitle = textHeroTitle;
        this.textHeroBody = textHeroBody;
        this.textReviewTitle = textReviewTitle;
        this.textReviewBody = textReviewBody;
        this.reviewOptionContainer = reviewOptionContainer;
        this.textReviewSummary = textReviewSummary;
        this.textReviewSavedState = textReviewSavedState;
        this.buttonReviewSave = buttonReviewSave;
        this.settlementContainer = settlementContainer;
        this.textSettlementSavedState = textSettlementSavedState;
        this.buttonSettlementConfirm = buttonSettlementConfirm;
        this.buttonSettlementHelp = buttonSettlementHelp;
        this.textEmergencyTitle = textEmergencyTitle;
        this.textEmergencyBody = textEmergencyBody;
        this.emergencyContainer = emergencyContainer;
        this.textEmergencySavedState = textEmergencySavedState;
        this.buttonCallManager = buttonCallManager;
        this.ratingOptionBinder = new BookingFollowUpRatingOptionBinder(context);
    }

    public void bindScreen(
            BookingFollowUpScreenModel screenModel,
            BookingFollowUpRatingOptionBinder.Listener listener
    ) {
        textMode.setText(screenModel.getModeText());
        textHeroBadge.setText(screenModel.getHeroBadgeText());
        textHeroTitle.setText(screenModel.getHeroTitleText());
        textHeroBody.setText(screenModel.getHeroBodyText());
        textReviewTitle.setText(screenModel.getReviewTitleText());
        textReviewBody.setText(screenModel.getReviewBodyText());
        textReviewSummary.setText(screenModel.getReviewSummaryText());
        textReviewSavedState.setText(screenModel.getReviewSavedStateText());
        buttonReviewSave.setText(screenModel.getReviewButtonText());
        buttonReviewSave.setEnabled(screenModel.isReviewButtonEnabled());
        buttonCallManager.setEnabled(screenModel.isManagerCallEnabled());
        bindRatingOptions(screenModel.getRatingOptions(), listener);
        bindLines(settlementContainer, screenModel.getSettlementLines());
        textSettlementSavedState.setText(screenModel.getSettlementSavedStateText());
        buttonSettlementConfirm.setText(screenModel.getSettlementConfirmButtonText());
        buttonSettlementConfirm.setEnabled(screenModel.isSettlementConfirmButtonEnabled());
        buttonSettlementHelp.setText(screenModel.getSettlementHelpButtonText());
        buttonSettlementHelp.setEnabled(screenModel.isSettlementHelpButtonEnabled());
        textEmergencyTitle.setText(screenModel.getEmergencyTitleText());
        textEmergencyBody.setText(screenModel.getEmergencyBodyText());
        bindLines(emergencyContainer, screenModel.getEmergencyLines());
        textEmergencySavedState.setText(screenModel.getEmergencySavedStateText());
    }

    private void bindRatingOptions(
            List<BookingFollowUpRatingOptionModel> items,
            BookingFollowUpRatingOptionBinder.Listener listener
    ) {
        reviewOptionContainer.removeAllViews();
        for (BookingFollowUpRatingOptionModel item : items) {
            View itemView = inflater.inflate(
                    R.layout.item_booking_follow_up_rating,
                    reviewOptionContainer,
                    false
            );
            ratingOptionBinder.bind(itemView, item, listener);
            reviewOptionContainer.addView(itemView);
        }
    }

    private void bindLines(LinearLayout container, List<BookingStatusLineItem> items) {
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
            BookingStatusLineItem item = items.get(index);
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
