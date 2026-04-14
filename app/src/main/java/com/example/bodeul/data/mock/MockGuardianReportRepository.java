package com.example.bodeul.data.mock;

import com.example.bodeul.data.GuardianReportRepository;
import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuardianReportDashboard;
import com.example.bodeul.domain.model.GuardianReportEntry;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import java.util.ArrayList;
import java.util.List;

/**
 * 보호자 진행 현황 화면을 목업 데이터에 연결하는 저장소다.
 */
public class MockGuardianReportRepository implements GuardianReportRepository {
    private final MockBodeulRepository repository;

    public MockGuardianReportRepository(MockBodeulRepository repository) {
        this.repository = repository;
    }

    @Override
    public void getGuardianDashboard(User currentUser, RepositoryCallback<GuardianReportDashboard> callback) {
        if (currentUser.getRole() != UserRole.GUARDIAN) {
            callback.onError("보호자 계정으로 접근해주세요.");
            return;
        }

        List<AppointmentRequest> requests = repository.getAppointmentRequestsForUser(
                currentUser.getId(),
                currentUser.getRole()
        );
        List<GuardianReportEntry> entries = new ArrayList<>();
        for (AppointmentRequest request : requests) {
            CompanionSession session = repository.findSessionByRequestId(request.getId());
            SessionReport report = session == null ? null : repository.getSessionReport(session.getId());
            HospitalGuide guide = repository.getHospitalGuide(
                    request.getHospitalName(),
                    request.getDepartmentName()
            );
            entries.add(new GuardianReportEntry(
                    request,
                    repository.findUserById(request.getPatientUserId()),
                    repository.findUserById(request.getManagerUserId()),
                    session,
                    report,
                    guide
            ));
        }

        callback.onSuccess(new GuardianReportDashboard(currentUser, entries));
    }

    @Override
    public boolean isFirebaseBacked() {
        return false;
    }
}
