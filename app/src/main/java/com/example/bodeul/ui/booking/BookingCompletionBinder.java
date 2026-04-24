package com.example.bodeul.ui.booking;

import android.view.View;
import android.widget.TextView;

/**
 * 예약 완료 화면의 정적 요약 카드 렌더링을 맡는다.
 */
public final class BookingCompletionBinder {
    private final TextView textBadge;
    private final TextView textTitle;
    private final TextView textBody;
    private final TextView textRequestId;
    private final TextView textSchedule;
    private final TextView textHospital;
    private final TextView textMeetingPlace;
    private final TextView textOptionSummary;
    private final TextView textPaymentSummary;
    private final View noteContainer;
    private final TextView textNote;

    public BookingCompletionBinder(
            TextView textBadge,
            TextView textTitle,
            TextView textBody,
            TextView textRequestId,
            TextView textSchedule,
            TextView textHospital,
            TextView textMeetingPlace,
            TextView textOptionSummary,
            TextView textPaymentSummary,
            View noteContainer,
            TextView textNote
    ) {
        this.textBadge = textBadge;
        this.textTitle = textTitle;
        this.textBody = textBody;
        this.textRequestId = textRequestId;
        this.textSchedule = textSchedule;
        this.textHospital = textHospital;
        this.textMeetingPlace = textMeetingPlace;
        this.textOptionSummary = textOptionSummary;
        this.textPaymentSummary = textPaymentSummary;
        this.noteContainer = noteContainer;
        this.textNote = textNote;
    }

    public void bind(BookingCompletionScreenModel screenModel) {
        textBadge.setText(screenModel.getBadge());
        textTitle.setText(screenModel.getTitle());
        textBody.setText(screenModel.getBody());
        textRequestId.setText(screenModel.getRequestId());
        textSchedule.setText(screenModel.getSchedule());
        textHospital.setText(screenModel.getHospital());
        textMeetingPlace.setText(screenModel.getMeetingPlace());
        textOptionSummary.setText(screenModel.getOptionSummary());
        textPaymentSummary.setText(screenModel.getPaymentSummary());
        noteContainer.setVisibility(screenModel.isNoteVisible() ? View.VISIBLE : View.GONE);
        textNote.setText(screenModel.getNote());
    }
}
