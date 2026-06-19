package com.example.bodeul.ui.admin;

public final class AdminSupportStatusFilterChipModel {
    private final AdminSupportStatusFilter filter;
    private final String buttonText;
    private final boolean selected;

    public AdminSupportStatusFilterChipModel(
            AdminSupportStatusFilter filter,
            String buttonText,
            boolean selected
    ) {
        this.filter = filter;
        this.buttonText = buttonText;
        this.selected = selected;
    }

    public AdminSupportStatusFilter getFilter() {
        return filter;
    }

    public String getButtonText() {
        return buttonText;
    }

    public boolean isSelected() {
        return selected;
    }
}
