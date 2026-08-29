package com.example.bodeul.data.firebase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.ManagerDocumentStatus;

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
    public void unchangedPendingSummarySkipsTimestampOnlyWrite() {
        assertFalse(FirebaseManagerRepository.managerDocumentSummaryStateChanged(
                "동일한 제출 요약",
                "PENDING_REVIEW",
                "동일한 제출 요약"
        ));
    }

    @Test
    public void sameRejectedSummaryStillRequestsReview() {
        assertTrue(FirebaseManagerRepository.managerDocumentSummaryStateChanged(
                "동일한 제출 요약",
                "REJECTED",
                "동일한 제출 요약"
        ));
    }

    @Test
    public void initialDraftFileKeepsNotSubmittedStatus() {
        Map<String, Object> updates = new HashMap<>();

        FirebaseManagerRepository.putManagerDocumentDraftState(
                updates,
                ManagerDocumentStatus.NOT_SUBMITTED,
                ""
        );

        assertEquals("NOT_SUBMITTED", updates.get("managerDocumentStatus"));
    }

    @Test
    public void replacingReviewedFileRequestsReview() {
        Map<String, Object> updates = new HashMap<>();

        FirebaseManagerRepository.putManagerDocumentDraftState(
                updates,
                ManagerDocumentStatus.APPROVED,
                "기존 제출 요약"
        );

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
