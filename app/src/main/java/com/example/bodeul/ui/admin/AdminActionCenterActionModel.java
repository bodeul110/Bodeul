package com.example.bodeul.ui.admin;

/**
 * 후속 알림 카드 하단의 버튼 한 개를 표현한다.
 */
public final class AdminActionCenterActionModel {
    private final AdminActionCenterActionType actionType;
    private final String labelText;

    public AdminActionCenterActionModel(
            AdminActionCenterActionType actionType,
            String labelText
    ) {
        this.actionType = actionType;
        this.labelText = labelText;
    }

    public AdminActionCenterActionType getActionType() {
        return actionType;
    }

    public String getLabelText() {
        return labelText;
    }
}
