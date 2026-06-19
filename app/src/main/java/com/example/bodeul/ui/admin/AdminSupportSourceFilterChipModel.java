package com.example.bodeul.ui.admin;

public final class AdminSupportSourceFilterChipModel {
    private final AdminSupportSourceFilter filter;
    private final String buttonText;
    private final boolean selected;

    public AdminSupportSourceFilterChipModel(
            AdminSupportSourceFilter filter,
            String buttonText,
            boolean selected
    ) {
        this.filter = filter;
        this.buttonText = buttonText;
        this.selected = selected;
    }

    public AdminSupportSourceFilter getFilter() {
        return filter;
    }

    public String getButtonText() {
        return buttonText;
    }

    public boolean isSelected() {
        return selected;
    }
}
