package com.example.bodeul.ui.manager;

/**
 * 매니저 상세 화면에서 라벨과 값을 한 줄씩 보여줄 때 사용하는 항목 모델이다.
 */
public final class ManagerInfoLineItem {
    private final String labelText;
    private final String valueText;
    private final boolean emphasized;

    public ManagerInfoLineItem(String labelText, String valueText, boolean emphasized) {
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

