package com.example.bodeul.data.mock;

import com.example.bodeul.data.AdminRepository;
import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AdminActionContract;
import com.example.bodeul.domain.model.AdminActionDeliveryRecord;
import com.example.bodeul.domain.model.AdminActionNotification;
import com.example.bodeul.domain.model.AdminActionOverview;
import com.example.bodeul.domain.model.AdminAuditLogEntry;
import com.example.bodeul.domain.model.AdminDashboard;
import com.example.bodeul.domain.model.AdminEmergencyIssueStatus;
import com.example.bodeul.domain.model.AdminSettlementStatus;
import com.example.bodeul.domain.model.AdminRequestOverview;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.ClientSupportRequest;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerDocumentStatus;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.SupportInquiry;
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
    public void reviewManagerDocument(
            User currentUser,
            String managerUserId,
            ManagerDocumentStatus status,
            String reviewNote,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해 주세요.");
            return;
        }
        if (status != ManagerDocumentStatus.APPROVED && status != ManagerDocumentStatus.REJECTED) {
            callback.onError("서류 검토 상태가 올바르지 않습니다.");
            return;
        }
        if (repository.reviewManagerDocument(
                managerUserId,
                status,
                reviewNote,
                currentUser.getName()
        ) == null) {
            callback.onError("매니저 서류 검토 상태를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(buildDashboard(currentUser));
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
            callback.onError("愿由ъ옄 怨꾩젙?쇰줈 ?묎렐??二쇱꽭??");
            return;
        }
        if (repository.saveSettlementRecord(requestId, status, note, currentUser.getName()) == null) {
            callback.onError("정산 후속 상태를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(buildDashboard(currentUser));
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
            callback.onError("愿由ъ옄 怨꾩젙?쇰줈 ?묎렐??二쇱꽭??");
            return;
        }
        if (repository.saveEmergencyIssue(requestId, status, note, currentUser.getName()) == null) {
            callback.onError("긴급 이슈 대응 상태를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(buildDashboard(currentUser));
    }

    @Override
    public void respondSupportInquiry(
            User currentUser,
            String inquiryId,
            String response,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("愿由ъ옄 怨꾩젙?쇰줈 ?묎렐??二쇱꽭??");
            return;
        }
        if (repository.respondSupportInquiry(inquiryId, response, currentUser.getName()) == null) {
            callback.onError("문의 응답을 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(buildDashboard(currentUser));
    }

    @Override
    public void respondClientSupportRequest(
            User currentUser,
            String supportRequestId,
            String response,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해 주세요.");
            return;
        }
        if (repository.respondClientSupportRequest(supportRequestId, response, currentUser.getName()) == null) {
            callback.onError("사용자 문의 답변을 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(buildDashboard(currentUser));
    }

    @Override
    public void markActionNotificationRead(
            User currentUser,
            String notificationId,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해 주세요.");
            return;
        }
        if (repository.markAdminActionNotificationRead(notificationId) == null) {
            callback.onError("읽음 처리할 알림을 찾지 못했습니다.");
            return;
        }
        callback.onSuccess(buildDashboard(currentUser));
    }

    @Override
    public void updateActionNotificationResolved(
            User currentUser,
            String notificationId,
            boolean resolved,
            RepositoryCallback<AdminDashboard> callback
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            callback.onError("관리자 계정으로 접근해 주세요.");
            return;
        }
        if (repository.updateAdminActionNotificationResolved(
                notificationId,
                resolved,
                currentUser.getName()
        ) == null) {
            callback.onError("상태를 바꿀 알림을 찾지 못했습니다.");
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
        List<ManagerDocumentOverview> managerDocumentOverviews = new ArrayList<>();
        for (User manager : repository.getUsersByRole(UserRole.MANAGER)) {
            if (repository.isManagerAvailable(manager.getId())) {
                availableManagers.add(manager);
            } else {
                busyManagers.add(manager);
            }
            ManagerHomeProfile profile = repository.getManagerHomeProfile(manager.getId());
            if (profile == null) {
                profile = new ManagerHomeProfile("", "");
            }
            managerDocumentOverviews.add(new ManagerDocumentOverview(
                    manager,
                    profile,
                    repository.getManagerDocumentHistory(manager.getId())
            ));
        }

        List<AdminRequestOverview> pendingRequests = new ArrayList<>();
        List<AdminRequestOverview> managedRequests = new ArrayList<>();
        for (AppointmentRequest request : repository.getAppointmentRequests()) {
            CompanionSession session = repository.findSessionByRequestId(request.getId());
            AdminRequestOverview overview = new AdminRequestOverview(
                    request,
                    repository.findUserById(request.getPatientUserId()),
                    repository.findUserById(request.getGuardianUserId()),
                    repository.findUserById(request.getManagerUserId()),
                    session,
                    session == null ? null : repository.getSessionReport(session.getId()),
                    repository.getHospitalGuide(request.getHospitalName(), request.getDepartmentName()) != null,
                    hasLinkedParticipants(request)
            );

            if (request.getStatus() == AppointmentStatus.REQUESTED) {
                pendingRequests.add(overview);
            } else {
                managedRequests.add(overview);
            }
        }

        List<SupportInquiry> supportInquiries = repository.getSupportInquiries();
        List<ClientSupportRequest> clientSupportRequests = repository.getClientSupportRequests();
        List<AdminActionNotification> actionNotifications = repository.getAdminActionNotifications();
        List<AdminAuditLogEntry> auditLogs = repository.getAdminAuditLogs();
        List<AdminActionDeliveryRecord> actionDeliveries = repository.getAdminActionDeliveries();
        AdminActionOverview actionOverview = AdminActionContract.createOverview(
                actionNotifications,
                auditLogs,
                actionDeliveries
        );
        return new AdminDashboard(
                currentUser,
                availableManagers,
                busyManagers,
                managerDocumentOverviews,
                pendingRequests,
                managedRequests,
                repository.getAdminRequestActionOverviews(),
                actionNotifications,
                auditLogs,
                actionDeliveries,
                actionOverview,
                supportInquiries,
                clientSupportRequests,
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
