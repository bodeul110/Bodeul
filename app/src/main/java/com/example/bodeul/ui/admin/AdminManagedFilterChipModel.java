package com.example.bodeul.ui.admin;

/**
 * 관리자 운영 상태 필터 칩 하나를 표현한다.
 */
public final class AdminManagedFilterChipModel {
    private final AdminManagedRequestFilter filter;
    private final String buttonText;
    private final boolean selected;

    public AdminManagedFilterChipModel(
            AdminManagedRequestFilter filter,
            String buttonText,
            boolean selected
    ) {
        this.filter = filter;
        this.buttonText = buttonText;
        this.selected = selected;
    }

    public AdminManagedRequestFilter getFilter() {
        return filter;
    }

    public String getButtonText() {
        return buttonText;
    }

    public boolean isSelected() {
        return selected;
    }
}
