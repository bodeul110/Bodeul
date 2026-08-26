package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.SessionStatus;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ManagerGuideCoordinatorPolicyTest {
    @Test
    public void progressPolicy_usesServerAdvanceDecision() {
        CompanionSession session = createSession(2);
        session.applyServerGuideProgress(
                "RECEPTION_QUEUE",
                true,
                false,
                "STEP_CONTRACT_MISMATCH");

        ManagerGuideProgressPolicy.Decision decision =
                ManagerGuideProgressPolicy.resolve(session, 13);

        assertFalse(decision.isAdvanceEnabled());
        assertEquals(
                ManagerGuideProgressPolicy.State.CONTRACT_MISMATCH,
                decision.getState());
    }

    @Test
    public void progressPolicy_allowsEntryBeforeFirstStepWhenServerAllowsIt() {
        CompanionSession session = createSession(0);
        session.applyServerGuideProgress("", true, true, "");

        ManagerGuideProgressPolicy.Decision decision =
                ManagerGuideProgressPolicy.resolve(session, 1);

        assertTrue(decision.isAdvanceEnabled());
        assertEquals(ManagerGuideProgressPolicy.State.ADVANCE, decision.getState());
    }

    @Test
    public void progressPolicy_marksEmptyGuideAsPreparing() {
        CompanionSession session = createSession(0);
        session.applyServerGuideProgress("", true, false, "GUIDE_NOT_READY");

        ManagerGuideProgressPolicy.Decision decision =
                ManagerGuideProgressPolicy.resolve(session, 0);

        assertFalse(decision.isAdvanceEnabled());
        assertEquals(
                ManagerGuideProgressPolicy.State.GUIDE_NOT_READY,
                decision.getState());
    }

    @Test
    public void focusResolver_restoresUnknownCurrentCodeAfterReload() {
        GuideStep first = new GuideStep("MEETING_CONFIRMATION", 1, "상봉 확인", "설명 1");
        GuideStep extension = new GuideStep(
                "UNLISTED_EXTENSION",
                2,
                "병원별 추가 단계",
                "추가 안내");
        CompanionSession restored = createSession(1);
        restored.applyServerGuideProgress("UNLISTED_EXTENSION", true, true, "");

        GuideStep result = ManagerGuideFocusResolver.resolve(
                Arrays.asList(first, extension),
                restored);

        assertSame(extension, result);
    }

    @Test
    public void focusResolver_returnsNullForEmptySnapshot() {
        assertEquals(
                null,
                ManagerGuideFocusResolver.resolve(
                        Collections.emptyList(),
                        createSession(0)));
    }

    @Test
    public void pharmacyRouteAction_usesCurrentStepCodeInsteadOfOrderFallback() {
        CompanionSession pharmacyRoute = createSession(9);
        pharmacyRoute.applyServerGuideProgress("PHARMACY_ROUTE", true, true, "");
        assertTrue(ManagerGuideCoordinator.shouldShowPharmacyRouteAction(pharmacyRoute));

        CompanionSession unknownAtSameOrder = createSession(9);
        unknownAtSameOrder.applyServerGuideProgress("UNLISTED_EXTENSION", true, true, "");
        assertFalse(ManagerGuideCoordinator.shouldShowPharmacyRouteAction(unknownAtSameOrder));
    }

    @Test
    public void reportCompletionAction_requiresValidatedLastStepDecision() {
        GuideStep journal = new GuideStep(
                "MANAGER_JOURNAL",
                13,
                "매니저 일지",
                "동행 기록을 마무리합니다.");

        CompanionSession lastStep = createSession(13);
        lastStep.applyServerGuideProgress(
                "MANAGER_JOURNAL",
                true,
                false,
                "LAST_STEP_REACHED");
        ManagerGuideProgressPolicy.Decision lastStepDecision =
                ManagerGuideProgressPolicy.resolve(lastStep, 13);

        CompanionSession contractMismatch = createSession(13);
        contractMismatch.applyServerGuideProgress(
                "",
                true,
                false,
                "STEP_CONTRACT_MISMATCH");
        ManagerGuideProgressPolicy.Decision mismatchDecision =
                ManagerGuideProgressPolicy.resolve(contractMismatch, 13);

        assertTrue(ManagerGuideCoordinator.isPrimaryActionEnabled(journal, lastStepDecision));
        assertTrue(ManagerGuideCoordinator.isStepInputEnabled(lastStepDecision));
        assertFalse(ManagerGuideCoordinator.isPrimaryActionEnabled(journal, mismatchDecision));
        assertFalse(ManagerGuideCoordinator.isStepInputEnabled(mismatchDecision));
    }

    private CompanionSession createSession(int currentStepOrder) {
        return new CompanionSession(
                "session-id",
                "appointment-id",
                "manager-id",
                currentStepOrder,
                SessionStatus.MEETING,
                "",
                "",
                "",
                "",
                "",
                false);
    }
}
