package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertEquals;

import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;

import org.junit.Test;

public class ManagerHistoryCoordinatorPolicyTest {
    @Test
    public void managerHistory_usesOwnJournalWhenReportIsRedacted() {
        CompanionSession session = session();
        session.applyCompletionState(
                1L,
                "환자 인계 완료",
                "READY",
                1,
                "",
                2L,
                null);

        assertEquals(
                "환자 인계 완료",
                ManagerHistoryCoordinator.resolveManagerVisibleSummary(
                        detail(session, null))
        );
    }

    @Test
    public void managerHistory_prefersAvailableReportInLegacyRepository() {
        SessionReport report = new SessionReport(
                "report-id",
                "session-id",
                "기존 리포트 요약",
                "",
                "",
                "",
                "",
                "",
                null,
                "",
                "");

        assertEquals(
                "기존 리포트 요약",
                ManagerHistoryCoordinator.resolveManagerVisibleSummary(
                        detail(session(), report))
        );
    }

    private AppointmentRequestDetail detail(
            CompanionSession session,
            SessionReport report
    ) {
        AppointmentRequest appointment = new AppointmentRequest(
                "appointment-id",
                "patient-id",
                "guardian-id",
                "검증 병원",
                "내과",
                "2026-08-29T01:00:00Z",
                "로비",
                "",
                AppointmentStatus.COMPLETED,
                "manager-id");
        return new AppointmentRequestDetail(
                appointment,
                null,
                null,
                null,
                session,
                report,
                null);
    }

    private CompanionSession session() {
        return new CompanionSession(
                "session-id",
                "appointment-id",
                "manager-id",
                13,
                SessionStatus.COMPLETED,
                "",
                "",
                "",
                "",
                "",
                true);
    }
}
