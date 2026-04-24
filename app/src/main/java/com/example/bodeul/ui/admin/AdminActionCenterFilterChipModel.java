package com.example.bodeul.ui.admin;

/**
 * 관리자 후속 알림 섹션의 필터 칩 한 개를 표현한다.
 */
public final class AdminActionCenterFilterChipModel {
    private final AdminActionCenterFilter filter;
    private final String buttonText;
    private final boolean selected;

    public AdminActionCenterFilterChipModel(
            AdminActionCenterFilter filter,
            String buttonText,
            boolean selected
    ) {
        this.filter = filter;
        this.buttonText = buttonText;
        this.selected = selected;
    }

    public AdminActionCenterFilter getFilter() {
        return filter;
    }

    public String getButtonText() {
        return buttonText;
    }

    public boolean isSelected() {
        return selected;
    }
}
