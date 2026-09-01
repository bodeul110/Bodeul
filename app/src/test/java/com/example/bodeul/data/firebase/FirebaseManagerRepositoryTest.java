package com.example.bodeul.data.firebase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.ManagerDocumentFileType;
import com.example.bodeul.domain.model.ManagerDocumentStatus;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
    public void nursingLicenseReplacementRemovesOtherQualificationReferences() {
        Map<String, Object> updates = new HashMap<>();

        FirebaseManagerRepository.putCanonicalQualificationReplacement(
                updates,
                ManagerDocumentFileType.NURSING_LICENSE
        );

        assertTrue(updates.containsKey("managerDocumentFiles.license"));
        assertTrue(updates.containsKey("managerDocumentFilePaths.license"));
        assertTrue(updates.containsKey("managerLicenseStoragePath"));
        assertTrue(updates.containsKey("managerDocumentFiles.healthCertificate"));
        assertTrue(updates.containsKey("managerDocumentFilePaths.healthCertificate"));
        assertTrue(updates.containsKey("managerHealthCertificateStoragePath"));
        assertFalse(updates.containsKey("managerDocumentFiles.nursingLicense"));
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

    @Test
    public void freshAdvanceStateRequiresSameStepAndSessionLinks() {
        assertTrue(FirebaseManagerRepository.matchesFreshAdvanceState(
                2L,
                "WAITING",
                "manager-1",
                "appointment-1",
                2,
                "WAITING",
                "manager-1",
                "appointment-1"
        ));
        assertFalse(FirebaseManagerRepository.matchesFreshAdvanceState(
                3L,
                "IN_TREATMENT",
                "manager-1",
                "appointment-1",
                2,
                "WAITING",
                "manager-1",
                "appointment-1"
        ));
        assertFalse(FirebaseManagerRepository.matchesFreshAdvanceState(
                2L,
                "WAITING",
                "manager-2",
                "appointment-1",
                2,
                "WAITING",
                "manager-1",
                "appointment-1"
        ));
    }

    @Test
    public void firebaseDashboardResolvesStepCodeFromGuideOrder() {
        assertEquals("HOSPITAL_ROUTE", FirebaseManagerRepository.resolveCurrentStepCode(
                Arrays.asList(
                        new GuideStep("MEETING_CONFIRMATION", 1, "상봉", ""),
                        new GuideStep("HOSPITAL_ROUTE", 2, "길안내", "")
                ),
                2
        ));
        assertEquals("", FirebaseManagerRepository.resolveCurrentStepCode(
                Arrays.asList(new GuideStep("MEETING_CONFIRMATION", 1, "상봉", "")),
                3
        ));
    }

    @Test
    public void firestoreGuideParserReadsVideoContractAndKeepsLegacyCompatible() {
        Map<String, Object> videoStep = new HashMap<>();
        videoStep.put("code", "HOSPITAL_ROUTE");
        videoStep.put("order", 2L);
        videoStep.put("title", "길안내");
        videoStep.put("description", "진료과로 이동합니다.");
        videoStep.put("videoAssetId", "hospital-route-main");
        videoStep.put("videoAssetVersion", "v1");
        videoStep.put("videoFallbackText", "카카오맵 경로를 확인해 주세요.");

        Map<String, Object> legacyStep = new HashMap<>();
        legacyStep.put("order", 3L);
        legacyStep.put("title", "접수");
        legacyStep.put("description", "접수를 진행합니다.");

        List<GuideStep> steps = FirebaseManagerRepository.toGuideSteps(
                Arrays.asList(videoStep, legacyStep));

        assertEquals(2, steps.size());
        assertEquals("HOSPITAL_ROUTE", steps.get(0).getCode());
        assertEquals("hospital-route-main", steps.get(0).getVideoAssetId());
        assertEquals("v1", steps.get(0).getVideoAssetVersion());
        assertEquals("카카오맵 경로를 확인해 주세요.", steps.get(0).getVideoFallbackText());
        assertEquals("", steps.get(1).getCode());
        assertFalse(steps.get(1).hasVideoAssetMetadata());
    }
}
