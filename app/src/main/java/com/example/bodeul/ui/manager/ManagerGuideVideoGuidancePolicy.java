package com.example.bodeul.ui.manager;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.GuideStep;

/**
 * 길안내 영상 자산이 실제 재생 계약으로 연결되기 전의 안전한 표시 상태를 결정한다.
 */
final class ManagerGuideVideoGuidancePolicy {
    enum State {
        HIDDEN,
        FALLBACK_ONLY,
        ASSET_REGISTERED
    }

    static final class Result {
        private final State state;
        private final String fallbackText;

        private Result(State state, String fallbackText) {
            this.state = state;
            this.fallbackText = fallbackText;
        }

        State getState() {
            return state;
        }

        String getFallbackText() {
            return fallbackText;
        }

        boolean isVisible() {
            return state != State.HIDDEN;
        }

        boolean hasRegisteredAsset() {
            return state == State.ASSET_REGISTERED;
        }
    }

    private ManagerGuideVideoGuidancePolicy() {
    }

    static Result resolve(@Nullable GuideStep step) {
        if (step == null || !ManagerGuideStepRegistry.isHospitalRoute(step.getCode())) {
            return new Result(State.HIDDEN, "");
        }

        return new Result(
                step.hasCompleteVideoGuidanceMetadata()
                        ? State.ASSET_REGISTERED
                        : State.FALLBACK_ONLY,
                step.getVideoFallbackText());
    }
}
