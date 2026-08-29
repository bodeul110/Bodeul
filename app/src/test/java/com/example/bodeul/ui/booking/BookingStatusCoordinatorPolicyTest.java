package com.example.bodeul.ui.booking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.UserRole;

import org.junit.Test;

public class BookingStatusCoordinatorPolicyTest {
    @Test
    public void guardianNeverReceivesEditOrCancelStatusActions() {
        assertFalse(BookingMutationPolicy.isStatusActionAllowed(
                UserRole.GUARDIAN, BookingStatusActionType.EDIT));
        assertFalse(BookingMutationPolicy.isStatusActionAllowed(
                UserRole.GUARDIAN, BookingStatusActionType.CANCEL));
        assertTrue(BookingMutationPolicy.isStatusActionAllowed(
                UserRole.GUARDIAN, BookingStatusActionType.REFRESH));
        assertTrue(BookingMutationPolicy.isStatusActionAllowed(
                UserRole.GUARDIAN, BookingStatusActionType.OPEN_REPORT));
    }

    @Test
    public void patientKeepsEditAndCancelStatusActions() {
        assertTrue(BookingMutationPolicy.isStatusActionAllowed(
                UserRole.PATIENT, BookingStatusActionType.EDIT));
        assertTrue(BookingMutationPolicy.isStatusActionAllowed(
                UserRole.PATIENT, BookingStatusActionType.CANCEL));
    }
}
