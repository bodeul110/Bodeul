package com.example.bodeul.ui.admin;

/**
 * 관리자 운영 카드 하단 버튼 한 개를 표현한다.
 */
public final class AdminOperationActionModel {
    private final AdminOperationActionType actionType;
    private final String buttonText;

    public AdminOperationActionModel(AdminOperationActionType actionType, String buttonText) {
        this.actionType = actionType;
        this.buttonText = buttonText;
    }

    public AdminOperationActionType getActionType() {
        return actionType;
    }

    public String getButtonText() {
        return buttonText;
    }
}
