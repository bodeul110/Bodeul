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
    private final String medicationName;
    private final String medicationChangeSummary;
    private final String medicationScheduleNote;
    private final MedicationComparisonDecision medicationComparisonDecision;
    private final String medicationComparisonNote;
    private final String nextVisitAt;

    public SessionReport(
            String id,
            String sessionId,
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String medicationName,
            String medicationChangeSummary,
            String medicationScheduleNote,
            MedicationComparisonDecision medicationComparisonDecision,
            String medicationComparisonNote,
            String nextVisitAt
    ) {
        this.id = id;
        this.sessionId = sessionId;
        this.summary = summary;
        this.treatmentNotes = treatmentNotes;
        this.medicationNotes = medicationNotes;
        this.medicationName = medicationName;
        this.medicationChangeSummary = medicationChangeSummary;
        this.medicationScheduleNote = medicationScheduleNote;
        this.medicationComparisonDecision = medicationComparisonDecision;
        this.medicationComparisonNote = medicationComparisonNote;
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

    public String getMedicationName() {
        return medicationName;
    }

    public String getMedicationChangeSummary() {
        return medicationChangeSummary;
    }

    public String getMedicationScheduleNote() {
        return medicationScheduleNote;
    }

    public MedicationComparisonDecision getMedicationComparisonDecision() {
        return medicationComparisonDecision;
    }

    public String getMedicationComparisonNote() {
        return medicationComparisonNote;
    }

    public String getNextVisitAt() {
        return nextVisitAt;
    }
}
