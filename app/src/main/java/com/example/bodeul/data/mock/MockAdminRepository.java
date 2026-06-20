package com.example.bodeul.data.mock;

import com.example.bodeul.data.AdminRepository;
import com.example.bodeul.data.MockAdminStore;
import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AdminDashboard;
import com.example.bodeul.domain.model.AdminEmergencyIssueStatus;
import com.example.bodeul.domain.model.AdminSettlementStatus;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.ManagerDocumentStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import java.util.List;

/**
 * 관리자 운영 화면을 목업 데이터에 연결하는 저장소다.
 */
public class MockAdminRepository implements AdminRepository {
    private final MockBodeulRepository repository;
    private final MockAdminStore adminStore;

    public MockAdminRepository(MockBodeulRepository repository) {
        this.repository = repository;
        this.adminStore = new MockAdminStore(repository);
    }

    @Override
    public void getAdminDashboard(User currentUser, RepositoryCallback<AdminDashboard> callback) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해주세요.");
            return;
        }
        AdminDashboard dashboard = adminStore.getAdminDashboard(currentUser);
        if (dashboard == null) {
            callback.onError("관리자 화면 정보를 불러오지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
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
        AdminDashboard dashboard = adminStore.assignManager(currentUser, requestId, managerUserId);
        if (dashboard == null) {
            callback.onError("매니저 배정을 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
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
        AdminDashboard dashboard = adminStore.saveHospitalGuide(
                currentUser,
                hospitalName,
                departmentName,
                stepLines
        );
        if (dashboard == null) {
            callback.onError("병원 가이드를 저장하지 못했습니다. 단계 내용을 다시 확인해주세요.");
            return;
        }
        callback.onSuccess(dashboard);
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
        AdminDashboard dashboard = adminStore.deleteHospitalGuide(currentUser, guideId);
        if (dashboard == null) {
            callback.onError("삭제할 병원 가이드를 찾지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void reviewManagerDocument(
            User currentUser,
            String managerUserId,
            ManagerDocumentStatus status,
            String reviewNote,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해주세요.");
            return;
        }
        if (status != ManagerDocumentStatus.APPROVED && status != ManagerDocumentStatus.REJECTED) {
            callback.onError("서류 검토 상태가 올바르지 않습니다.");
            return;
        }
        AdminDashboard dashboard = adminStore.reviewManagerDocument(
                currentUser,
                managerUserId,
                status,
                reviewNote
        );
        if (dashboard == null) {
            callback.onError("매니저 서류 검토 상태를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void saveSettlementRecord(
            User currentUser,
            String requestId,
            AdminSettlementStatus status,
            String note,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해주세요.");
            return;
        }
        AdminDashboard dashboard = adminStore.saveSettlementRecord(currentUser, requestId, status, note);
        if (dashboard == null) {
            callback.onError("정산 후속 상태를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void saveEmergencyIssue(
            User currentUser,
            String requestId,
            AdminEmergencyIssueStatus status,
            String note,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해주세요.");
            return;
        }
        AdminDashboard dashboard = adminStore.saveEmergencyIssue(currentUser, requestId, status, note);
        if (dashboard == null) {
            callback.onError("긴급 이슈 대응 상태를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void respondSupportInquiry(
            User currentUser,
            String inquiryId,
            String response,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해주세요.");
            return;
        }
        AdminDashboard dashboard = adminStore.respondSupportInquiry(currentUser, inquiryId, response);
        if (dashboard == null) {
            callback.onError("문의 응답을 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void respondClientSupportRequest(
            User currentUser,
            String supportRequestId,
            String response,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해주세요.");
            return;
        }
        AdminDashboard dashboard = adminStore.respondClientSupportRequest(
                currentUser,
                supportRequestId,
                response
        );
        if (dashboard == null) {
            callback.onError("사용자 문의 응답을 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void markActionNotificationRead(
            User currentUser,
            String notificationId,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해주세요.");
            return;
        }
        AdminDashboard dashboard = adminStore.markActionNotificationRead(currentUser, notificationId);
        if (dashboard == null) {
            callback.onError("읽음 처리할 알림을 찾지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void updateActionNotificationResolved(
            User currentUser,
            String notificationId,
            boolean resolved,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해주세요.");
            return;
        }
        AdminDashboard dashboard = adminStore.updateActionNotificationResolved(
                currentUser,
                notificationId,
                resolved
        );
        if (dashboard == null) {
            callback.onError("상태를 바꿀 알림을 찾지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public boolean isFirebaseBacked() {
        return false;
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
