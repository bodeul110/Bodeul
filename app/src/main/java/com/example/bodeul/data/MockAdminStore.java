package com.example.bodeul.data;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.AdminActionContract;
import com.example.bodeul.domain.model.AdminActionDeliveryRecord;
import com.example.bodeul.domain.model.AdminActionNotification;
import com.example.bodeul.domain.model.AdminActionOverview;
import com.example.bodeul.domain.model.AdminAuditLogEntry;
import com.example.bodeul.domain.model.AdminDashboard;
import com.example.bodeul.domain.model.AdminEmergencyIssueStatus;
import com.example.bodeul.domain.model.AdminRequestOverview;
import com.example.bodeul.domain.model.AdminSettlementStatus;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.ClientSupportRequest;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerDocumentStatus;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import java.util.ArrayList;
import java.util.List;

public final class MockAdminStore {
    private final MockBodeulRepository repository;
    private final MockSupportStore supportStore;

    public MockAdminStore(MockBodeulRepository repository) {
        this.repository = repository;
        this.supportStore = new MockSupportStore(repository);
    }

    @Nullable
    public AdminDashboard getAdminDashboard(User currentUser) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            return null;
        }

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

        List<SupportInquiry> supportInquiries = supportStore.getAllSupportInquiries();
        List<ClientSupportRequest> clientSupportRequests = supportStore.getAllClientSupportRequests();
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

    @Nullable
    public AdminDashboard assignManager(User currentUser, String requestId, String managerUserId) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            return null;
        }

        AppointmentRequest request = findRequest(requestId);
        if (request == null) {
            return null;
        }
        if (!hasLinkedParticipants(request)) {
            return null;
        }
        if (repository.getHospitalGuide(request.getHospitalName(), request.getDepartmentName()) == null) {
            return null;
        }
        if (!repository.isManagerAvailable(managerUserId)) {
            return null;
        }
        if (repository.assignManagerToRequest(requestId, managerUserId) == null) {
            return null;
        }
        return getAdminDashboard(currentUser);
    }

    @Nullable
    public AdminDashboard saveHospitalGuide(
            User currentUser,
            String hospitalName,
            String departmentName,
            List<String> stepLines
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            return null;
        }
        if (repository.saveHospitalGuide(hospitalName, departmentName, stepLines) == null) {
            return null;
        }
        return getAdminDashboard(currentUser);
    }

    @Nullable
    public AdminDashboard deleteHospitalGuide(User currentUser, String guideId) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            return null;
        }
        if (!repository.deleteHospitalGuide(guideId)) {
            return null;
        }
        return getAdminDashboard(currentUser);
    }

    @Nullable
    public AdminDashboard reviewManagerDocument(
            User currentUser,
            String managerUserId,
            ManagerDocumentStatus status,
            String reviewNote
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            return null;
        }
        if (status != ManagerDocumentStatus.APPROVED && status != ManagerDocumentStatus.REJECTED) {
            return null;
        }
        if (repository.reviewManagerDocument(managerUserId, status, reviewNote, currentUser.getName()) == null) {
            return null;
        }
        return getAdminDashboard(currentUser);
    }

    @Nullable
    public AdminDashboard saveSettlementRecord(
            User currentUser,
            String requestId,
            AdminSettlementStatus status,
            String note
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            return null;
        }
        if (repository.saveSettlementRecord(requestId, status, note, currentUser.getName()) == null) {
            return null;
        }
        return getAdminDashboard(currentUser);
    }

    @Nullable
    public AdminDashboard saveEmergencyIssue(
            User currentUser,
            String requestId,
            AdminEmergencyIssueStatus status,
            String note
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            return null;
        }
        if (repository.saveEmergencyIssue(requestId, status, note, currentUser.getName()) == null) {
            return null;
        }
        return getAdminDashboard(currentUser);
    }

    @Nullable
    public AdminDashboard respondSupportInquiry(User currentUser, String inquiryId, String response) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            return null;
        }
        if (supportStore.respondSupportInquiry(inquiryId, response, currentUser.getName()) == null) {
            return null;
        }
        return getAdminDashboard(currentUser);
    }

    @Nullable
    public AdminDashboard respondClientSupportRequest(
            User currentUser,
            String supportRequestId,
            String response
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            return null;
        }
        if (supportStore.respondClientSupportRequest(
                supportRequestId,
                response,
                currentUser.getName()
        ) == null) {
            return null;
        }
        return getAdminDashboard(currentUser);
    }

    @Nullable
    public AdminDashboard markActionNotificationRead(User currentUser, String notificationId) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            return null;
        }
        if (repository.markAdminActionNotificationRead(notificationId) == null) {
            return null;
        }
        return getAdminDashboard(currentUser);
    }

    @Nullable
    public AdminDashboard updateActionNotificationResolved(
            User currentUser,
            String notificationId,
            boolean resolved
    ) {
        if (currentUser.getRole() != UserRole.ADMIN) {
            return null;
        }
        if (repository.updateAdminActionNotificationResolved(
                notificationId,
                resolved,
                currentUser.getName()
        ) == null) {
            return null;
        }
        return getAdminDashboard(currentUser);
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
