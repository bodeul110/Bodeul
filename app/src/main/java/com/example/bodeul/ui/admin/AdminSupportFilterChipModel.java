package com.example.bodeul.ui.admin;

/**
 * 관리자 문의 응답 섹션의 출처 필터 버튼 모델이다.
 */
public final class AdminSupportFilterChipModel {
    private final AdminSupportFilter filter;
    private final String buttonText;
    private final boolean selected;

    public AdminSupportFilterChipModel(
            AdminSupportFilter filter,
            String buttonText,
            boolean selected
    ) {
        this.filter = filter;
        this.buttonText = buttonText;
        this.selected = selected;
    }

    public AdminSupportFilter getFilter() {
        return filter;
    }

    public String getButtonText() {
        return buttonText;
    }

    public boolean isSelected() {
        return selected;
    }
}
