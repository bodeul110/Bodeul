package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.ManagerDocumentStatus;

import org.junit.Test;

public class ManagerHomePresentationFormatterTest {

    @Test
    public void pendingReviewDoesNotExposePreviousReviewDecision() {
        assertFalse(ManagerHomePresentationFormatter.shouldShowReviewDecision(
                ManagerDocumentStatus.PENDING_REVIEW
        ));
        assertFalse(ManagerHomePresentationFormatter.shouldShowReviewDecision(
                ManagerDocumentStatus.NOT_SUBMITTED
        ));
        assertTrue(ManagerHomePresentationFormatter.shouldShowReviewDecision(
                ManagerDocumentStatus.APPROVED
        ));
        assertTrue(ManagerHomePresentationFormatter.shouldShowReviewDecision(
                ManagerDocumentStatus.REJECTED
        ));
    }
}
