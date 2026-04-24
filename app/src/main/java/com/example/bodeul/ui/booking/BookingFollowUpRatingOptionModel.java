package com.example.bodeul.ui.booking;

import com.example.bodeul.domain.model.AppointmentFollowUpReviewRating;

/**
 * 후기 선택 카드 한 항목의 표시 데이터를 담는다.
 */
public final class BookingFollowUpRatingOptionModel {
    private final AppointmentFollowUpReviewRating rating;
    private final String titleText;
    private final String bodyText;
    private final boolean selected;

    public BookingFollowUpRatingOptionModel(
            AppointmentFollowUpReviewRating rating,
            String titleText,
            String bodyText,
            boolean selected
    ) {
        this.rating = rating;
        this.titleText = titleText;
        this.bodyText = bodyText;
        this.selected = selected;
    }

    public AppointmentFollowUpReviewRating getRating() {
        return rating;
    }

    public String getTitleText() {
        return titleText;
    }

    public String getBodyText() {
        return bodyText;
    }

    public boolean isSelected() {
        return selected;
    }
}
