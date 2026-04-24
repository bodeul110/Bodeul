package com.example.bodeul.ui.report;

/**
 * 보호자 진행 카드 안에서 라벨과 값을 한 줄로 보여주기 위한 모델이다.
 */
public final class GuardianReportLineItem {
    private final String labelText;
    private final String valueText;
    private final boolean emphasized;

    public GuardianReportLineItem(String labelText, String valueText, boolean emphasized) {
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
