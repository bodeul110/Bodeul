package com.example.bodeul.ui.booking;

/**
 * 예약 상세 화면의 버튼 표시 정보와 동작 종류를 묶는다.
 */
public final class BookingStatusActionModel {
    private final BookingStatusActionType actionType;
    private final String labelText;

    public BookingStatusActionModel(BookingStatusActionType actionType, String labelText) {
        this.actionType = actionType;
        this.labelText = labelText;
    }

    public BookingStatusActionType getActionType() {
        return actionType;
    }

    public String getLabelText() {
        return labelText;
    }
}
