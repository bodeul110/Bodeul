package com.example.bodeul.domain.model;

/**
 * 동행 종료 후 보호자에게 전달할 진료 요약 정보를 담는다.
 */
public class SessionReport {
    // 어떤 세션에 대한 리포트인지 식별하기 위한 정보다.
    private final String id;
    private final String sessionId;

    // 보호자에게 전달할 핵심 요약과 진료 상세 메모다.
    private final String summary;
    private final String treatmentNotes;
    private final String medicationNotes;
    private final String nextVisitAt;

    public SessionReport(
            String id,
            String sessionId,
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String nextVisitAt
    ) {
        this.id = id;
        this.sessionId = sessionId;
        this.summary = summary;
        this.treatmentNotes = treatmentNotes;
        this.medicationNotes = medicationNotes;
        this.nextVisitAt = nextVisitAt;
    }

    public String getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getSummary() {
        return summary;
    }

    public String getTreatmentNotes() {
        return treatmentNotes;
    }

    public String getMedicationNotes() {
        return medicationNotes;
    }

    public String getNextVisitAt() {
        return nextVisitAt;
    }
}
