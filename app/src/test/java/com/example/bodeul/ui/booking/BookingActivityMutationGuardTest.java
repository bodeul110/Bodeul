package com.example.bodeul.ui.booking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.UserRole;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class BookingActivityMutationGuardTest {
    @Test
    public void guardianCreateUpdateAndCancelNeverInvokeRepositoryBoundary() {
        AtomicInteger invocationCount = new AtomicInteger();

        for (BookingMutationPolicy.Operation operation : BookingMutationPolicy.Operation.values()) {
            boolean invoked = BookingMutationPolicy.runIfAllowed(
                    UserRole.GUARDIAN,
                    operation,
                    invocationCount::incrementAndGet);
            assertFalse(invoked);
        }

        assertEquals(0, invocationCount.get());
    }

    @Test
    public void guardianEditSubmissionIsRejectedAtSubmissionGuard() {
        assertFalse(BookingMutationPolicy.isAllowed(
                UserRole.GUARDIAN,
                BookingMutationPolicy.Operation.UPDATE));
    }

    @Test
    public void patientMutationStillInvokesRepositoryBoundary() {
        AtomicInteger invocationCount = new AtomicInteger();

        boolean invoked = BookingMutationPolicy.runIfAllowed(
                UserRole.PATIENT,
                BookingMutationPolicy.Operation.UPDATE,
                invocationCount::incrementAndGet);

        assertTrue(invoked);
        assertEquals(1, invocationCount.get());
    }
}
