package com.example.bodeul.ui.manager;

/**
 * 완료 다이얼로그 Fragment 트랜잭션이 반영되기 전 중복 등록을 막는다.
 */
final class ManagerQualificationCompletionDialogGate {
    private boolean requestEnqueued;

    boolean tryEnqueue(boolean completionPending, boolean dialogAlreadyAdded) {
        if (!completionPending) {
            return false;
        }
        if (dialogAlreadyAdded) {
            requestEnqueued = true;
            return false;
        }
        if (requestEnqueued) {
            return false;
        }
        requestEnqueued = true;
        return true;
    }

    void clear() {
        requestEnqueued = false;
    }
}
