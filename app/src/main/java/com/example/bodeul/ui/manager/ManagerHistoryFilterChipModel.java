package com.example.bodeul.ui.manager;

/**
 * 과거 동행 이력 필터 버튼 하나를 표현한다.
 */
public final class ManagerHistoryFilterChipModel {
    private final ManagerHistoryFilter filter;
    private final String buttonText;
    private final boolean selected;

    public ManagerHistoryFilterChipModel(
            ManagerHistoryFilter filter,
            String buttonText,
            boolean selected
    ) {
        this.filter = filter;
        this.buttonText = buttonText;
        this.selected = selected;
    }

    public ManagerHistoryFilter getFilter() {
        return filter;
    }

    public String getButtonText() {
        return buttonText;
    }

    public boolean isSelected() {
        return selected;
    }
}
