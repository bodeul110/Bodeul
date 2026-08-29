package com.example.bodeul.domain.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AppointmentPublicCodeModelTest {

    @Test
    public void publicCodeIsStoredAsServerDisplayValue() {
        AppointmentRequest request = new AppointmentRequest(
                "appointment-id",
                "patient-id",
                "guardian-id",
                "서울대학교병원",
                "내과",
                "2026-12-20 10:30",
                "본관 1층",
                "",
                AppointmentStatus.REQUESTED,
                "");

        request.setPublicCode("  BD-ABC123  ");

        assertEquals("BD-ABC123", request.getPublicCode());
    }
}
