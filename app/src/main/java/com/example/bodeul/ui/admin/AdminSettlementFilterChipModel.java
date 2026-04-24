package com.example.bodeul.ui.admin;

/**
 * 정산 확인 필터 버튼 하나를 표현한다.
 */
public final class AdminSettlementFilterChipModel {
    private final AdminSettlementFilter filter;
    private final String buttonText;
    private final boolean selected;

    public AdminSettlementFilterChipModel(
            AdminSettlementFilter filter,
            String buttonText,
            boolean selected
    ) {
        this.filter = filter;
        this.buttonText = buttonText;
        this.selected = selected;
    }

    public AdminSettlementFilter getFilter() {
        return filter;
    }

    public String getButtonText() {
        return buttonText;
    }

    public boolean isSelected() {
        return selected;
    }
}
