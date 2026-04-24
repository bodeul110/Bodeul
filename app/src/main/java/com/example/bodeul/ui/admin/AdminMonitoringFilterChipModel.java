package com.example.bodeul.ui.admin;

/**
 * 실시간 운영 모니터링 필터 버튼 하나를 표현한다.
 */
public final class AdminMonitoringFilterChipModel {
    private final AdminMonitoringFilter filter;
    private final String buttonText;
    private final boolean selected;

    public AdminMonitoringFilterChipModel(
            AdminMonitoringFilter filter,
            String buttonText,
            boolean selected
    ) {
        this.filter = filter;
        this.buttonText = buttonText;
        this.selected = selected;
    }

    public AdminMonitoringFilter getFilter() {
        return filter;
    }

    public String getButtonText() {
        return buttonText;
    }

    public boolean isSelected() {
        return selected;
    }
}
