package com.example.bodeul.ui.manager;

/** 보호자에게 공유할 접수 정보를 빈 값이나 비현실적인 숫자로 전송하지 않도록 검증한다. */
final class ManagerGuideReceptionInputPolicy {
    private ManagerGuideReceptionInputPolicy() {
    }

    static boolean isValidQueue(String value) {
        return value != null && !value.trim().isEmpty() && value.trim().length() <= 20;
    }

    /** 1~999분만 허용하고 그 외 값은 0으로 돌려준다. */
    static int parseWaitMinutes(String value) {
        if (value == null) return 0;
        try {
            int minutes = Integer.parseInt(value.trim());
            return minutes >= 1 && minutes <= 999 ? minutes : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
