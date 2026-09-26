package com.example.bodeul.debug;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ManagerGuidePreviewRepositoryTest {
    private static final List<String> EXPECTED_CODES = List.of(
            "MEETING_CONFIRMATION",
            "HOSPITAL_ROUTE",
            "RECEPTION_QUEUE",
            "VITALS_CHECK",
            "PRE_CONSULTATION",
            "CONSULTATION_SUPPORT",
            "CONSULTATION_SUMMARY",
            "PAYMENT_EVIDENCE",
            "PHARMACY_ROUTE",
            "PRESCRIPTION_DOCUMENTS",
            "MEDICATION_CONFIRMATION",
            "CARE_COMPLETION",
            "MANAGER_JOURNAL");
    private static final List<String> EXPECTED_TITLES = List.of(
            "상봉 확인",
            "병원 이동",
            "접수와 대기",
            "기초 측정",
            "진료 전 확인",
            "진료 동행",
            "진료 요약",
            "수납 증빙",
            "약국 이동",
            "처방 자료",
            "복약 확인",
            "동행 종료",
            "매니저 일지");

    @Test
    public void catalog_matchesThirteenStepContract() {
        List<GuideStep> steps = ManagerGuidePreviewCatalog.steps();

        assertEquals(13, steps.size());
        for (int index = 0; index < steps.size(); index++) {
            GuideStep step = steps.get(index);
            assertEquals(index + 1, step.getOrder());
            assertEquals(EXPECTED_CODES.get(index), step.getCode());
            assertEquals(EXPECTED_TITLES.get(index), step.getTitle());
            assertFalse(step.getDescription().trim().isEmpty());
        }
    }

    @Test
    public void selectedStep_usesLocalCodedGuideAndServerDecision() {
        ManagerGuidePreviewRepository repository =
                new ManagerGuidePreviewRepository("VITALS_CHECK");

        ManagerDashboard dashboard = dashboard(repository);

        assertEquals(13, dashboard.getHospitalGuide().getSteps().size());
        assertEquals(4, dashboard.getSession().getCurrentStepOrder());
        assertEquals("VITALS_CHECK", dashboard.getSession().getCurrentStepCode());
        assertTrue(dashboard.getSession().hasServerAdvanceDecision());
        assertTrue(dashboard.getSession().isServerAdvanceAllowed());
    }

    @Test
    public void careCompletionAdvance_opensJournalCompletionState() {
        ManagerGuidePreviewRepository repository =
                new ManagerGuidePreviewRepository("CARE_COMPLETION");
        ManagerDashboard current = dashboard(repository);
        AtomicReference<ManagerDashboard> advanced = new AtomicReference<>();

        repository.advanceCurrentStep(
                ManagerGuidePreviewRepository.MANAGER_ID,
                current.getSession().getId(),
                current.getSession().getCurrentStepCode(),
                callback(advanced));

        ManagerDashboard result = advanced.get();
        assertNotNull(result);
        assertEquals(13, result.getSession().getCurrentStepOrder());
        assertEquals("MANAGER_JOURNAL", result.getSession().getCurrentStepCode());
        assertEquals(SessionStatus.CARE_ENDED, result.getSession().getStatus());
        assertFalse(result.getSession().isServerAdvanceAllowed());
        assertEquals(
                "CARE_ENDED_PENDING_COMPLETION",
                result.getSession().getAdvanceBlockedReason());
    }

    @Test
    public void journalSelection_startsInCareEndedCompletionState() {
        ManagerGuidePreviewRepository repository =
                new ManagerGuidePreviewRepository("MANAGER_JOURNAL");

        ManagerDashboard dashboard = dashboard(repository);

        assertEquals(13, dashboard.getSession().getCurrentStepOrder());
        assertEquals("MANAGER_JOURNAL", dashboard.getSession().getCurrentStepCode());
        assertEquals(SessionStatus.CARE_ENDED, dashboard.getSession().getStatus());
        assertTrue(dashboard.getSession().hasServerAdvanceDecision());
        assertFalse(dashboard.getSession().isServerAdvanceAllowed());
        assertEquals(
                "CARE_ENDED_PENDING_COMPLETION",
                dashboard.getSession().getAdvanceBlockedReason());
    }

    @Test
    public void preConsultation_requiresConfirmationBeforeAdvance() {
        ManagerGuidePreviewRepository repository =
                new ManagerGuidePreviewRepository("PRE_CONSULTATION");
        ManagerDashboard initial = dashboard(repository);
        assertFalse(initial.getSession().isServerAdvanceAllowed());
        assertEquals("STEP_INPUT_REQUIRED", initial.getSession().getAdvanceBlockedReason());
        AtomicReference<ManagerDashboard> updated = new AtomicReference<>();

        repository.updatePreConsultationConfirmed(
                ManagerGuidePreviewRepository.MANAGER_ID,
                true,
                callback(updated));

        assertNotNull(updated.get());
        assertTrue(updated.get().getSession().isPreConsultationConfirmed());
        assertTrue(updated.get().getSession().isServerAdvanceAllowed());
        assertEquals("", updated.get().getSession().getAdvanceBlockedReason());
    }

    @Test
    public void journalSubmission_returnsLocalReportWithoutActiveSessionReload() {
        ManagerGuidePreviewRepository repository =
                new ManagerGuidePreviewRepository("MANAGER_JOURNAL");
        AtomicReference<SessionReport> submitted = new AtomicReference<>();

        repository.submitSessionReport(
                ManagerGuidePreviewRepository.MANAGER_ID,
                "동행 요약",
                "진료 내용",
                "복약 메모",
                "테스트 약",
                "변경 없음",
                "조식 후 복용",
                null,
                "",
                "2026-10-01",
                new RepositoryCallback<SessionReport>() {
                    @Override
                    public void onSuccess(SessionReport result) {
                        submitted.set(result);
                    }

                    @Override
                    public void onError(String message) {
                        throw new AssertionError(message);
                    }
                });

        assertNotNull(submitted.get());
        assertEquals("debug-preview-report", submitted.get().getId());
        assertEquals("동행 요약", submitted.get().getSummary());
    }

    private ManagerDashboard dashboard(ManagerGuidePreviewRepository repository) {
        AtomicReference<ManagerDashboard> result = new AtomicReference<>();
        repository.getManagerDashboard(
                ManagerGuidePreviewRepository.MANAGER_ID,
                callback(result));
        assertNotNull(result.get());
        return result.get();
    }

    private RepositoryCallback<ManagerDashboard> callback(
            AtomicReference<ManagerDashboard> result
    ) {
        return new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard dashboard) {
                result.set(dashboard);
            }

            @Override
            public void onError(String message) {
                throw new AssertionError(message);
            }
        };
    }
}
