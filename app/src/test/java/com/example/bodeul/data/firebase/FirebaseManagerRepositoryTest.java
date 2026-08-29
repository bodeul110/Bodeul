package com.example.bodeul.data.firebase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.ManagerDashboard;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class FirebaseManagerRepositoryTest {

    @Test
    public void managerDocumentResubmissionOnlyUpdatesSubmissionState() {
        Map<String, Object> updates = new HashMap<>();

        FirebaseManagerRepository.putManagerDocumentSubmissionState(updates);

        assertEquals("PENDING_REVIEW", updates.get("managerDocumentStatus"));
        assertNotNull(updates.get("managerDocumentUpdatedAt"));
        assertFalse(updates.containsKey("managerDocumentReviewNote"));
        assertFalse(updates.containsKey("managerDocumentReviewedAt"));
        assertFalse(updates.containsKey("managerDocumentReviewedByName"));
        assertFalse(updates.containsKey("managerDocumentReviewedByAdminUserId"));
        assertFalse(updates.containsKey("managerDocumentHistory"));
        assertFalse(updates.containsKey("managerDocumentLegalHoldUntil"));
    }

    @Test
    public void emptyManagerDocumentSummaryReturnsToNotSubmitted() {
        Map<String, Object> updates = new HashMap<>();

        FirebaseManagerRepository.putManagerDocumentSummaryState(updates, "");

        assertEquals("NOT_SUBMITTED", updates.get("managerDocumentStatus"));
        assertNotNull(updates.get("managerDocumentUpdatedAt"));
        assertFalse(updates.containsKey("managerDocumentReviewNote"));
        assertFalse(updates.containsKey("managerDocumentHistory"));
    }

    @Test
    public void nonEmptyManagerDocumentSummaryRequestsReview() {
        Map<String, Object> updates = new HashMap<>();

        FirebaseManagerRepository.putManagerDocumentSummaryState(updates, "자격 증빙 제출");

        assertEquals("PENDING_REVIEW", updates.get("managerDocumentStatus"));
    }

    @Test
    public void preConsultationConfirmationRequiresCoreApiInsteadOfDirectFirestoreWrite() {
        FirebaseManagerRepository repository = new FirebaseManagerRepository(null);
        AtomicBoolean succeeded = new AtomicBoolean(false);
        AtomicReference<String> error = new AtomicReference<>();

        repository.updatePreConsultationConfirmed(
                "manager-1",
                true,
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        succeeded.set(true);
                    }

                    @Override
                    public void onError(String message) {
                        error.set(message);
                    }
                });

        assertFalse(succeeded.get());
        assertEquals("진료 전 확인 저장에는 Core API 연결이 필요합니다.", error.get());
    }
}
