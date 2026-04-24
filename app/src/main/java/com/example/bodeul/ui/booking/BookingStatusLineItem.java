package com.example.bodeul.ui.booking;

/**
 * 예약 상세 카드에서 라벨과 값을 함께 보여주는 한 줄 항목이다.
 */
public final class BookingStatusLineItem {
    private final String labelText;
    private final String valueText;
    private final boolean emphasized;

    public BookingStatusLineItem(String labelText, String valueText, boolean emphasized) {
        this.labelText = labelText;
        this.valueText = valueText;
        this.emphasized = emphasized;
    }

    public String getLabelText() {
        return labelText;
    }

    public String getValueText() {
        return valueText;
    }

    public boolean isEmphasized() {
        return emphasized;
    }
}
