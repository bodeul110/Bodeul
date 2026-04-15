package com.example.bodeul.data.mock;

import com.example.bodeul.data.AdminRepository;
import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AdminDashboard;
import com.example.bodeul.domain.model.AdminRequestOverview;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 운영 화면을 목업 데이터에 연결하는 저장소다.
 */
public class MockAdminRepository implements AdminRepository {
    private final MockBodeulRepository repository;

    public MockAdminRepository(MockBodeulRepository repository) {
        this.repository = repository;
    }

    @Override
    public void getAdminDashboard(User currentUser, RepositoryCallback<AdminDashboard> callback) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해주세요.");
            return;
        }
        callback.onSuccess(buildDashboard(currentUser));
    }

    @Override
    public void assignManager(
            User currentUser,
            String requestId,
            String managerUserId,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해주세요.");
            return;
        }

        AppointmentRequest request = findRequest(requestId);
        if (request == null) {
            callback.onError("배정할 요청을 찾지 못했습니다.");
            return;
        }
        if (!hasLinkedParticipants(request)) {
            callback.onError("환자와 보호자 계정 연결이 완료된 요청만 배정할 수 있습니다.");
            return;
        }
        if (repository.getHospitalGuide(request.getHospitalName(), request.getDepartmentName()) == null) {
            callback.onError("해당 병원과 진료과 가이드가 없어 먼저 가이드를 등록해야 합니다.");
            return;
        }
        if (!repository.isManagerAvailable(managerUserId)) {
            callback.onError("선택한 매니저는 현재 다른 동행을 진행 중입니다.");
            return;
        }
        if (repository.assignManagerToRequest(requestId, managerUserId) == null) {
            callback.onError("매니저 배정을 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(buildDashboard(currentUser));
    }

    @Override
    public void saveHospitalGuide(
            User currentUser,
            String hospitalName,
            String departmentName,
            List<String> stepLines,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해주세요.");
            return;
        }
        if (repository.saveHospitalGuide(hospitalName, departmentName, stepLines) == null) {
            callback.onError("병원 가이드를 저장하지 못했습니다. 단계 내용을 다시 확인해주세요.");
            return;
        }
        callback.onSuccess(buildDashboard(currentUser));
    }

    @Override
    public void deleteHospitalGuide(
            User currentUser,
            String guideId,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해주세요.");
            return;
        }
        if (!repository.deleteHospitalGuide(guideId)) {
            callback.onError("삭제할 병원 가이드를 찾지 못했습니다.");
            return;
        }
        callback.onSuccess(buildDashboard(currentUser));
    }

    @Override
    public boolean isFirebaseBacked() {
        return false;
    }

    private AdminDashboard buildDashboard(User currentUser) {
        List<User> availableManagers = new ArrayList<>();
        List<User> busyManagers = new ArrayList<>();
        for (User manager : repository.getUsersByRole(UserRole.MANAGER)) {
            if (repository.isManagerAvailable(manager.getId())) {
                availableManagers.add(manager);
            } else {
                busyManagers.add(manager);
            }
        }

        List<AdminRequestOverview> pendingRequests = new ArrayList<>();
        List<AdminRequestOverview> managedRequests = new ArrayList<>();
        for (AppointmentRequest request : repository.getAppointmentRequests()) {
            AdminRequestOverview overview = new AdminRequestOverview(
                    request,
                    repository.findUserById(request.getPatientUserId()),
                    repository.findUserById(request.getGuardianUserId()),
                    repository.findUserById(request.getManagerUserId()),
                    repository.findSessionByRequestId(request.getId()),
                    repository.getHospitalGuide(request.getHospitalName(), request.getDepartmentName()) != null,
                    hasLinkedParticipants(request)
            );

            if (request.getStatus() == AppointmentStatus.REQUESTED) {
                pendingRequests.add(overview);
            } else {
                managedRequests.add(overview);
            }
        }

        return new AdminDashboard(
                currentUser,
                availableManagers,
                busyManagers,
                pendingRequests,
                managedRequests,
                repository.getHospitalGuides()
        );
    }

    private AppointmentRequest findRequest(String requestId) {
        for (AppointmentRequest request : repository.getAppointmentRequests()) {
            if (request.getId().equals(requestId)) {
                return request;
            }
        }
        return null;
    }

    private boolean hasLinkedParticipants(AppointmentRequest request) {
        return request.getPatientUserId() != null
                && !request.getPatientUserId().trim().isEmpty()
                && request.getGuardianUserId() != null
                && !request.getGuardianUserId().trim().isEmpty();
    }
}
