package com.example.bodeul.ui.admin;

/**
 * 요청 카드에서 표시하는 매니저 배정 액션을 표현한다.
 */
public final class AdminRequestAssignActionModel {
    private final String managerUserId;
    private final String buttonText;

    public AdminRequestAssignActionModel(String managerUserId, String buttonText) {
        this.managerUserId = managerUserId;
        this.buttonText = buttonText;
    }

    public String getManagerUserId() {
        return managerUserId;
    }

    public String getButtonText() {
        return buttonText;
    }
}
