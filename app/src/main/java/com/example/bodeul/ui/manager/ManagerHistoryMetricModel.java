package com.example.bodeul.ui.manager;

/**
 * 과거 동행 이력 상단의 운영 지표 카드 한 개를 표현한다.
 */
public final class ManagerHistoryMetricModel {
    private final String labelText;
    private final String valueText;
    private final String helperText;
    private final ManagerHistoryBadgeTone tone;

    public ManagerHistoryMetricModel(
            String labelText,
            String valueText,
            String helperText,
            ManagerHistoryBadgeTone tone
    ) {
        this.labelText = labelText;
        this.valueText = valueText;
        this.helperText = helperText;
        this.tone = tone;
    }

    public String getLabelText() {
        return labelText;
    }

    public String getValueText() {
        return valueText;
    }

    public String getHelperText() {
        return helperText;
    }

    public ManagerHistoryBadgeTone getTone() {
        return tone;
    }
}
