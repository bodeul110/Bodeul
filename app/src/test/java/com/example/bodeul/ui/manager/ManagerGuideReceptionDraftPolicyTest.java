package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ManagerGuideReceptionDraftPolicyTest {
    @Test
    public void newSessionClearsPreviousPatientDraft() {
        assertTrue(ManagerGuideReceptionDraftPolicy.shouldClear("session-one", "session-two"));
    }

    @Test
    public void sameSessionKeepsCurrentDraftAcrossRefresh() {
        assertFalse(ManagerGuideReceptionDraftPolicy.shouldClear("session-one", "session-one"));
    }

    @Test
    public void firstBoundSessionStartsWithEmptyDraft() {
        assertTrue(ManagerGuideReceptionDraftPolicy.shouldClear("", "session-one"));
    }
}
