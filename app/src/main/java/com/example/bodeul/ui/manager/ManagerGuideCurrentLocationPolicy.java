package com.example.bodeul.ui.manager;

import androidx.annotation.Nullable;

/** 상봉 단계에서만 기기 현재 위치 확인 기능을 노출한다. */
final class ManagerGuideCurrentLocationPolicy {
    private static final String MEETING_STEP_CODE = "MEETING_CONFIRMATION";

    private ManagerGuideCurrentLocationPolicy() {
    }

    static boolean isAvailableFor(@Nullable String stepCode) {
        return stepCode != null && MEETING_STEP_CODE.equals(stepCode.trim());
    }
}
