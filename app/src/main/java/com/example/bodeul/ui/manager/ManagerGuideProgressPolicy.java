package com.example.bodeul.ui.manager;

import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.SessionStatus;

/**
 * 서버 진행 판정을 우선하고, 이전 데이터 경로에서만 로컬 단계 수를 보조 기준으로 사용한다.
 */
final class ManagerGuideProgressPolicy {
    enum State {
        ADVANCE,
        COMPLETED,
        LAST_STEP,
        GUIDE_NOT_READY,
        CONTRACT_MISMATCH,
        BLOCKED
    }

    private ManagerGuideProgressPolicy() {
    }

    static Decision resolve(CompanionSession session, int totalSteps) {
        if (session.hasServerAdvanceDecision()) {
            if (session.isServerAdvanceAllowed()) {
                return new Decision(true, State.ADVANCE);
            }
            switch (session.getAdvanceBlockedReason()) {
                case "SESSION_TERMINAL":
                    return new Decision(false, State.COMPLETED);
                case "LAST_STEP_REACHED":
                    return new Decision(false, State.LAST_STEP);
                case "GUIDE_NOT_READY":
                    return new Decision(false, State.GUIDE_NOT_READY);
                case "STEP_CONTRACT_MISMATCH":
                    return new Decision(false, State.CONTRACT_MISMATCH);
                default:
                    return new Decision(false, State.BLOCKED);
            }
        }

        if (session.getStatus() == SessionStatus.COMPLETED
                || session.getStatus() == SessionStatus.CANCELED) {
            return new Decision(false, State.COMPLETED);
        }
        if (totalSteps <= 0) {
            return new Decision(false, State.GUIDE_NOT_READY);
        }
        if (session.getCurrentStepOrder() >= totalSteps) {
            return new Decision(false, State.LAST_STEP);
        }
        return new Decision(true, State.ADVANCE);
    }

    static final class Decision {
        private final boolean advanceEnabled;
        private final State state;

        private Decision(boolean advanceEnabled, State state) {
            this.advanceEnabled = advanceEnabled;
            this.state = state;
        }

        boolean isAdvanceEnabled() {
            return advanceEnabled;
        }

        State getState() {
            return state;
        }
    }
}
