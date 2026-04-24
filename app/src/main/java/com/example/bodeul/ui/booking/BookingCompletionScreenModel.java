package com.example.bodeul.ui.booking;

/**
 * 예약 완료 화면에 필요한 문구 묶음이다.
 */
public final class BookingCompletionScreenModel {
    private final String badge;
    private final String title;
    private final String body;
    private final String requestId;
    private final String schedule;
    private final String hospital;
    private final String meetingPlace;
    private final String optionSummary;
    private final String paymentSummary;
    private final String note;
    private final boolean noteVisible;

    public BookingCompletionScreenModel(
            String badge,
            String title,
            String body,
            String requestId,
            String schedule,
            String hospital,
            String meetingPlace,
            String optionSummary,
            String paymentSummary,
            String note,
            boolean noteVisible
    ) {
        this.badge = badge;
        this.title = title;
        this.body = body;
        this.requestId = requestId;
        this.schedule = schedule;
        this.hospital = hospital;
        this.meetingPlace = meetingPlace;
        this.optionSummary = optionSummary;
        this.paymentSummary = paymentSummary;
        this.note = note;
        this.noteVisible = noteVisible;
    }

    public String getBadge() {
        return badge;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getSchedule() {
        return schedule;
    }

    public String getHospital() {
        return hospital;
    }

    public String getMeetingPlace() {
        return meetingPlace;
    }

    public String getOptionSummary() {
        return optionSummary;
    }

    public String getPaymentSummary() {
        return paymentSummary;
    }

    public String getNote() {
        return note;
    }

    public boolean isNoteVisible() {
        return noteVisible;
    }
}
