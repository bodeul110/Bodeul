package com.example.bodeul.ui.admin;

/**
 * 관리자 운영 카드 안의 라벨/값 한 줄을 표현한다.
 */
public final class AdminOperationLineItem {
    private final String labelText;
    private final String valueText;
    private final boolean emphasized;

    public AdminOperationLineItem(String labelText, String valueText, boolean emphasized) {
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
