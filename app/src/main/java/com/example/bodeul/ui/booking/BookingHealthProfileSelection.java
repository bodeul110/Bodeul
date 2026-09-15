package com.example.bodeul.ui.booking;

import com.example.bodeul.domain.model.BookingMobilitySupport;

/**
 * 건강 정보 선택 화면과 예약 폼 사이에서 전달하는 값이다.
 */
public final class BookingHealthProfileSelection {
    private final String patientConditionSummary;
    private final String medicationSummary;
    private final String specialNotes;
    private final BookingMobilitySupport mobilitySupport;

    public BookingHealthProfileSelection(
            String patientConditionSummary,
            String medicationSummary,
            String specialNotes,
            BookingMobilitySupport mobilitySupport
    ) {
        this.patientConditionSummary = normalize(patientConditionSummary);
        this.medicationSummary = normalize(medicationSummary);
        this.specialNotes = normalize(specialNotes);
        this.mobilitySupport = mobilitySupport == null
                ? BookingMobilitySupport.INDEPENDENT
                : mobilitySupport;
    }

    public String getPatientConditionSummary() {
        return patientConditionSummary;
    }

    public String getMedicationSummary() {
        return medicationSummary;
    }

    public String getSpecialNotes() {
        return specialNotes;
    }

    public BookingMobilitySupport getMobilitySupport() {
        return mobilitySupport;
    }

    public boolean hasRequiredCondition() {
        return !patientConditionSummary.isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
