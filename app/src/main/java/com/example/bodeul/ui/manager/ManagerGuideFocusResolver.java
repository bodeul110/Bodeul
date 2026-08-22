package com.example.bodeul.ui.manager;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.SessionStatus;

import java.util.List;

/**
 * 현재 단계 코드를 우선해 재조회된 snapshot의 포커스 단계를 복구한다.
 */
final class ManagerGuideFocusResolver {
    private ManagerGuideFocusResolver() {
    }

    @Nullable
    static GuideStep resolve(List<GuideStep> steps, CompanionSession session) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        String currentStepCode = session.getCurrentStepCode();
        if (!currentStepCode.isEmpty()) {
            for (GuideStep step : steps) {
                if (currentStepCode.equals(step.getCode())) {
                    return step;
                }
            }
        }

        int currentOrder = Math.max(1, session.getCurrentStepOrder());
        if (session.getStatus() == SessionStatus.COMPLETED) {
            currentOrder = Math.min(currentOrder, steps.size());
        }
        for (GuideStep step : steps) {
            if (step.getOrder() == currentOrder) {
                return step;
            }
        }
        return steps.get(steps.size() - 1);
    }
}
