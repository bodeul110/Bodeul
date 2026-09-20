package com.example.bodeul.ui.manager;

import androidx.annotation.Nullable;

/** 서버가 메모 원문을 반환하는 동행 종료 직전까지만 목록을 노출한다. */
final class ManagerGuideMemoSummaryDisplayPolicy {
    private ManagerGuideMemoSummaryDisplayPolicy() {
    }

    static boolean shouldShow(@Nullable ManagerGuidePrimaryAction primaryAction) {
        return primaryAction == ManagerGuidePrimaryAction.END_CARE;
    }
}
