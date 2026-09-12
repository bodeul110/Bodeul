package com.example.bodeul.ui.booking;

import android.widget.TextView;

/**
 * 예약 완료 화면의 성공 안내와 예약 상세 정보를 렌더링한다.
 */
public final class BookingCompletionBinder {
    private final TextView textScreenTitle;
    private final TextView textTitle;
    private final TextView textBody;
    private final TextView textHospital;
    private final TextView textDepartment;
    private final TextView textDate;
    private final TextView textTime;
    private final TextView textNote;

    public BookingCompletionBinder(
            TextView textScreenTitle,
            TextView textTitle,
            TextView textBody,
            TextView textHospital,
            TextView textDepartment,
            TextView textDate,
            TextView textTime,
            TextView textNote
    ) {
        this.textScreenTitle = textScreenTitle;
        this.textTitle = textTitle;
        this.textBody = textBody;
        this.textHospital = textHospital;
        this.textDepartment = textDepartment;
        this.textDate = textDate;
        this.textTime = textTime;
        this.textNote = textNote;
    }

    public void bind(BookingCompletionScreenModel screenModel) {
        textScreenTitle.setText(screenModel.getScreenTitle());
        textTitle.setText(screenModel.getTitle());
        textBody.setText(screenModel.getBody());
        textHospital.setText(screenModel.getHospital());
        textDepartment.setText(screenModel.getDepartment());
        textDate.setText(screenModel.getDate());
        textTime.setText(screenModel.getTime());
        textNote.setText(screenModel.getNote());
    }
}
