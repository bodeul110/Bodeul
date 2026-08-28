package com.example.bodeul.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * MVP 결제 시뮬레이션이 실제 승인 정보로 저장되지 않는지 검증한다.
 */
public class BookingPaymentApprovalTest {
    @Test
    public void empty_isNotCompleted() {
        BookingPaymentApproval approval = BookingPaymentApproval.empty();

        assertFalse(approval.isCompleted());
        assertEquals(BookingPaymentStatus.PENDING, approval.getStatus());
    }

    @Test
    public void simulated_isCompletedWithoutApprovalEvidence() {
        BookingPaymentApproval approval = BookingPaymentApproval.simulated(
                "MVP 카드 결제 시뮬레이션"
        );

        assertTrue(approval.isCompleted());
        assertEquals(BookingPaymentStatus.PENDING, approval.getStatus());
        assertEquals("MVP 카드 결제 시뮬레이션", approval.getProviderLabel());
        assertEquals("", approval.getApprovalCode());
        assertEquals("", approval.getApprovedAt());
    }

    @Test
    public void authorized_keepsLegacyApprovalEvidence() {
        BookingPaymentApproval approval = BookingPaymentApproval.authorized(
                "기존 승인 수단",
                "LEGACY-CODE",
                "2026-08-28 22:30"
        );

        assertTrue(approval.isCompleted());
        assertEquals(BookingPaymentStatus.AUTHORIZED, approval.getStatus());
        assertEquals("LEGACY-CODE", approval.getApprovalCode());
        assertEquals("2026-08-28 22:30", approval.getApprovedAt());
    }
}
