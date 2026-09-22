package com.example.bodeul.ui.manager;

import java.util.Objects;

/** 다른 동행 세션의 접수 입력이 현재 환자 안내에 섞이지 않도록 한다. */
final class ManagerGuideReceptionDraftPolicy {
    private ManagerGuideReceptionDraftPolicy() {
    }

    static boolean shouldClear(String boundSessionId, String currentSessionId) {
        return !Objects.equals(boundSessionId, currentSessionId);
    }
}
