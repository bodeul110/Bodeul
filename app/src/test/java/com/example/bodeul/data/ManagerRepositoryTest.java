package com.example.bodeul.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.SessionStatus;

import org.junit.Test;

public class ManagerRepositoryTest {
    @Test
    public void matchesAdvanceExpectation_requiresFreshSessionAndStep() {
        CompanionSession session = new CompanionSession(
                "session-1",
                "appointment-1",
                "manager-1",
                2,
                SessionStatus.MEETING,
                "",
                "",
                "",
                "",
                "",
                false);
        session.applyServerGuideProgress("HOSPITAL_ROUTE", true, true, "");

        assertTrue(ManagerRepository.matchesAdvanceExpectation(
                session,
                " session-1 ",
                "HOSPITAL_ROUTE"));
        assertFalse(ManagerRepository.matchesAdvanceExpectation(
                session,
                "session-2",
                "HOSPITAL_ROUTE"));
        assertFalse(ManagerRepository.matchesAdvanceExpectation(
                session,
                "session-1",
                "RECEPTION_QUEUE"));
        assertFalse(ManagerRepository.matchesAdvanceExpectation(
                null,
                "session-1",
                "HOSPITAL_ROUTE"));
    }
}
