package com.example.bodeul.domain.model;

/**
 * 매니저 홈에서 빠르게 확인하고 수정하는 서류 / 일정 요약 정보다.
 */
public class ManagerHomeProfile {
    private final String documentSummary;
    private final String availabilitySummary;

    public ManagerHomeProfile(String documentSummary, String availabilitySummary) {
        this.documentSummary = documentSummary == null ? "" : documentSummary;
        this.availabilitySummary = availabilitySummary == null ? "" : availabilitySummary;
    }

    public String getDocumentSummary() {
        return documentSummary;
    }

    public String getAvailabilitySummary() {
        return availabilitySummary;
    }
}
