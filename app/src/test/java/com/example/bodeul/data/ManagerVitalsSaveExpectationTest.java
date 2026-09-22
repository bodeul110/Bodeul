package com.example.bodeul.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.SessionStatus;

import org.junit.Test;

public class ManagerVitalsSaveExpectationTest {
    @Test
    public void sessionReplacementAndStepChangeRejectVitalsWriteBeforeRepositoryMutation() {
        CompanionSession current = new CompanionSession(
                "session-a", "appointment-a", "manager-a", 4,
                SessionStatus.WAITING, "", "", "", "", "", false);
        current.setCurrentStepCode("VITALS_CHECK");

        assertTrue(ManagerRepository.matchesVitalsExpectation(
                current, "session-a", "VITALS_CHECK"));
        assertFalse(ManagerRepository.matchesVitalsExpectation(
                current, "session-b", "VITALS_CHECK"));

        current.setCurrentStepCode("PRE_CONSULTATION");
        assertFalse(ManagerRepository.matchesVitalsExpectation(
                current, "session-a", "VITALS_CHECK"));
    }
}
