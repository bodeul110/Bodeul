package com.example.bodeul.domain.model;

import java.util.List;

/**
 * 관리자 화면에서 사용하는 매칭 현황, 매니저 상태, 가이드 목록을 묶는다.
 */
public class AdminDashboard {
    private final User admin;
    private final List<User> availableManagers;
    private final List<User> busyManagers;
    private final List<ManagerDocumentOverview> managerDocumentOverviews;
    private final List<AdminRequestOverview> pendingRequests;
    private final List<AdminRequestOverview> managedRequests;
    private final List<AdminRequestActionOverview> requestActionOverviews;
    private final List<AdminActionNotification> actionNotifications;
    private final List<AdminAuditLogEntry> auditLogs;
    private final List<AdminActionDeliveryRecord> actionDeliveries;
    private final AdminActionOverview actionOverview;
    private final List<SupportInquiry> supportInquiries;
    private final List<ClientSupportRequest> clientSupportRequests;
    private final List<HospitalGuide> hospitalGuides;

    public AdminDashboard(
            User admin,
            List<User> availableManagers,
            List<User> busyManagers,
            List<ManagerDocumentOverview> managerDocumentOverviews,
            List<AdminRequestOverview> pendingRequests,
            List<AdminRequestOverview> managedRequests,
            List<AdminRequestActionOverview> requestActionOverviews,
            List<AdminActionNotification> actionNotifications,
            List<AdminAuditLogEntry> auditLogs,
            List<AdminActionDeliveryRecord> actionDeliveries,
            AdminActionOverview actionOverview,
            List<SupportInquiry> supportInquiries,
            List<ClientSupportRequest> clientSupportRequests,
            List<HospitalGuide> hospitalGuides
    ) {
        this.admin = admin;
        this.availableManagers = availableManagers;
        this.busyManagers = busyManagers;
        this.managerDocumentOverviews = managerDocumentOverviews;
        this.pendingRequests = pendingRequests;
        this.managedRequests = managedRequests;
        this.requestActionOverviews = requestActionOverviews;
        this.actionNotifications = actionNotifications;
        this.auditLogs = auditLogs;
        this.actionDeliveries = actionDeliveries;
        this.actionOverview = actionOverview;
        this.supportInquiries = supportInquiries;
        this.clientSupportRequests = clientSupportRequests;
        this.hospitalGuides = hospitalGuides;
    }

    public User getAdmin() {
        return admin;
    }

    public List<User> getAvailableManagers() {
        return availableManagers;
    }

    public List<User> getBusyManagers() {
        return busyManagers;
    }

    public List<ManagerDocumentOverview> getManagerDocumentOverviews() {
        return managerDocumentOverviews;
    }

    public List<AdminRequestOverview> getPendingRequests() {
        return pendingRequests;
    }

    public List<AdminRequestOverview> getManagedRequests() {
        return managedRequests;
    }

    public List<AdminRequestActionOverview> getRequestActionOverviews() {
        return requestActionOverviews;
    }

    public List<AdminActionNotification> getActionNotifications() {
        return actionNotifications;
    }

    public List<AdminAuditLogEntry> getAuditLogs() {
        return auditLogs;
    }

    public List<AdminActionDeliveryRecord> getActionDeliveries() {
        return actionDeliveries;
    }

    public AdminActionOverview getActionOverview() {
        return actionOverview;
    }

    public List<SupportInquiry> getSupportInquiries() {
        return supportInquiries;
    }

    public List<ClientSupportRequest> getClientSupportRequests() {
        return clientSupportRequests;
    }

    public List<HospitalGuide> getHospitalGuides() {
        return hospitalGuides;
    }
}
