package com.example.bodeul.ui.admin;

/**
 * 관리자 운영 날짜 필터 칩 하나를 표현한다.
 */
public final class AdminManagedDateFilterChipModel {
    private final AdminManagedRequestDateFilter filter;
    private final String buttonText;
    private final boolean selected;

    public AdminManagedDateFilterChipModel(
            AdminManagedRequestDateFilter filter,
            String buttonText,
            boolean selected
    ) {
        this.filter = filter;
        this.buttonText = buttonText;
        this.selected = selected;
    }

    public AdminManagedRequestDateFilter getFilter() {
        return filter;
    }

    public String getButtonText() {
        return buttonText;
    }

    public boolean isSelected() {
        return selected;
    }
}
