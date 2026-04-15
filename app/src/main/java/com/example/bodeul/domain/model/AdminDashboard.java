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
    private final List<HospitalGuide> hospitalGuides;

    public AdminDashboard(
            User admin,
            List<User> availableManagers,
            List<User> busyManagers,
            List<ManagerDocumentOverview> managerDocumentOverviews,
            List<AdminRequestOverview> pendingRequests,
            List<AdminRequestOverview> managedRequests,
            List<HospitalGuide> hospitalGuides
    ) {
        this.admin = admin;
        this.availableManagers = availableManagers;
        this.busyManagers = busyManagers;
        this.managerDocumentOverviews = managerDocumentOverviews;
        this.pendingRequests = pendingRequests;
        this.managedRequests = managedRequests;
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

    public List<HospitalGuide> getHospitalGuides() {
        return hospitalGuides;
    }
}
