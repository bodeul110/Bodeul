package com.example.bodeul.domain.model;

/**
 * 관리자 화면에서 매니저별 서류 제출 현황과 검토 상태를 함께 보여주기 위한 모델이다.
 */
public class ManagerDocumentOverview {
    private final User manager;
    private final ManagerHomeProfile profile;

    public ManagerDocumentOverview(User manager, ManagerHomeProfile profile) {
        this.manager = manager;
        this.profile = profile;
    }

    public User getManager() {
        return manager;
    }

    public ManagerHomeProfile getProfile() {
        return profile;
    }
}
