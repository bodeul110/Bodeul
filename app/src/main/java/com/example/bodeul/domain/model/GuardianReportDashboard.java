package com.example.bodeul.domain.model;

import java.util.List;

/**
 * 보호자 리포트 화면에 필요한 사용자 정보와 요청 목록을 함께 전달한다.
 */
public class GuardianReportDashboard {
    private final User guardian;
    private final List<GuardianReportEntry> entries;

    public GuardianReportDashboard(User guardian, List<GuardianReportEntry> entries) {
        this.guardian = guardian;
        this.entries = entries;
    }

    public User getGuardian() {
        return guardian;
    }

    public List<GuardianReportEntry> getEntries() {
        return entries;
    }
}
