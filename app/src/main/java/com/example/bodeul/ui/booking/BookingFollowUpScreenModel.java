package com.example.bodeul.ui.booking;

import java.util.Collections;
import java.util.List;

/**
 * 종료 후 후기·정산·SOS 화면의 표시 데이터를 전달한다.
 */
public final class BookingFollowUpScreenModel {
    private final String modeText;
    private final String heroBadgeText;
    private final String heroTitleText;
    private final String heroBodyText;
    private final String reviewTitleText;
    private final String reviewBodyText;
    private final List<BookingFollowUpRatingOptionModel> ratingOptions;
    private final String reviewSummaryText;
    private final String reviewSavedStateText;
    private final String reviewButtonText;
    private final boolean reviewButtonEnabled;
    private final List<BookingStatusLineItem> settlementLines;
    private final String settlementSavedStateText;
    private final String settlementConfirmButtonText;
    private final boolean settlementConfirmButtonEnabled;
    private final String settlementHelpButtonText;
    private final boolean settlementHelpButtonEnabled;
    private final String emergencyTitleText;
    private final String emergencyBodyText;
    private final List<BookingStatusLineItem> emergencyLines;
    private final String emergencySavedStateText;
    private final boolean managerCallEnabled;

    public BookingFollowUpScreenModel(
            String modeText,
            String heroBadgeText,
            String heroTitleText,
            String heroBodyText,
            String reviewTitleText,
            String reviewBodyText,
            List<BookingFollowUpRatingOptionModel> ratingOptions,
            String reviewSummaryText,
            String reviewSavedStateText,
            String reviewButtonText,
            boolean reviewButtonEnabled,
            List<BookingStatusLineItem> settlementLines,
            String settlementSavedStateText,
            String settlementConfirmButtonText,
            boolean settlementConfirmButtonEnabled,
            String settlementHelpButtonText,
            boolean settlementHelpButtonEnabled,
            String emergencyTitleText,
            String emergencyBodyText,
            List<BookingStatusLineItem> emergencyLines,
            String emergencySavedStateText,
            boolean managerCallEnabled
    ) {
        this.modeText = modeText;
        this.heroBadgeText = heroBadgeText;
        this.heroTitleText = heroTitleText;
        this.heroBodyText = heroBodyText;
        this.reviewTitleText = reviewTitleText;
        this.reviewBodyText = reviewBodyText;
        this.ratingOptions = Collections.unmodifiableList(ratingOptions);
        this.reviewSummaryText = reviewSummaryText;
        this.reviewSavedStateText = reviewSavedStateText;
        this.reviewButtonText = reviewButtonText;
        this.reviewButtonEnabled = reviewButtonEnabled;
        this.settlementLines = Collections.unmodifiableList(settlementLines);
        this.settlementSavedStateText = settlementSavedStateText;
        this.settlementConfirmButtonText = settlementConfirmButtonText;
        this.settlementConfirmButtonEnabled = settlementConfirmButtonEnabled;
        this.settlementHelpButtonText = settlementHelpButtonText;
        this.settlementHelpButtonEnabled = settlementHelpButtonEnabled;
        this.emergencyTitleText = emergencyTitleText;
        this.emergencyBodyText = emergencyBodyText;
        this.emergencyLines = Collections.unmodifiableList(emergencyLines);
        this.emergencySavedStateText = emergencySavedStateText;
        this.managerCallEnabled = managerCallEnabled;
    }

    public String getModeText() {
        return modeText;
    }

    public String getHeroBadgeText() {
        return heroBadgeText;
    }

    public String getHeroTitleText() {
        return heroTitleText;
    }

    public String getHeroBodyText() {
        return heroBodyText;
    }

    public String getReviewTitleText() {
        return reviewTitleText;
    }

    public String getReviewBodyText() {
        return reviewBodyText;
    }

    public List<BookingFollowUpRatingOptionModel> getRatingOptions() {
        return ratingOptions;
    }

    public String getReviewSummaryText() {
        return reviewSummaryText;
    }

    public String getReviewSavedStateText() {
        return reviewSavedStateText;
    }

    public String getReviewButtonText() {
        return reviewButtonText;
    }

    public boolean isReviewButtonEnabled() {
        return reviewButtonEnabled;
    }

    public List<BookingStatusLineItem> getSettlementLines() {
        return settlementLines;
    }

    public String getSettlementSavedStateText() {
        return settlementSavedStateText;
    }

    public String getSettlementConfirmButtonText() {
        return settlementConfirmButtonText;
    }

    public boolean isSettlementConfirmButtonEnabled() {
        return settlementConfirmButtonEnabled;
    }

    public String getSettlementHelpButtonText() {
        return settlementHelpButtonText;
    }

    public boolean isSettlementHelpButtonEnabled() {
        return settlementHelpButtonEnabled;
    }

    public String getEmergencyTitleText() {
        return emergencyTitleText;
    }

    public String getEmergencyBodyText() {
        return emergencyBodyText;
    }

    public List<BookingStatusLineItem> getEmergencyLines() {
        return emergencyLines;
    }

    public String getEmergencySavedStateText() {
        return emergencySavedStateText;
    }

    public boolean isManagerCallEnabled() {
        return managerCallEnabled;
    }
}
