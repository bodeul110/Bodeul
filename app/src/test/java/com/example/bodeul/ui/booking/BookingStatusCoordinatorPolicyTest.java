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

    @Test
    public void inProgressAction_hidesLegacyLocationWhenDisabledAndKeepsDevelopmentOptIn() {
        assertTrue(
                BookingStatusCoordinator.resolveInProgressPrimaryActionType(false)
                        == BookingStatusActionType.REFRESH);
        assertTrue(
                BookingStatusCoordinator.resolveInProgressPrimaryActionType(true)
                        == BookingStatusActionType.OPEN_LIVE_TRACKING);
        assertFalse(BookingStatusCoordinator.shouldShowInProgressRefreshSecondary(
                UserRole.PATIENT,
                false));
        assertTrue(BookingStatusCoordinator.shouldShowInProgressRefreshSecondary(
                UserRole.PATIENT,
                true));
        assertFalse(BookingStatusCoordinator.shouldShowInProgressRefreshSecondary(
                UserRole.GUARDIAN,
                true));
    }
}
