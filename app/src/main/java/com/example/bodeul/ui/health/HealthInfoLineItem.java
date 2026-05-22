package com.example.bodeul.ui.health;

public final class HealthInfoLineItem {
    private final String labelText;
    private final String valueText;
    private final boolean emphasized;

    public HealthInfoLineItem(String labelText, String valueText, boolean emphasized) {
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
