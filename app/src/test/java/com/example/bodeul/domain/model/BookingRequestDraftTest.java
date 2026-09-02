package com.example.bodeul.domain.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BookingRequestDraftTest {
    @Test
    public void builder_withoutPaymentMethodKeepsUnknown() {
        BookingRequestDraft draft = BookingRequestDraft.builder().build();

        assertEquals(BookingPaymentMethod.UNKNOWN, draft.getPaymentMethod());
    }

    @Test
    public void builder_nullPaymentMethodKeepsUnknown() {
        BookingRequestDraft draft = BookingRequestDraft.builder()
                .paymentMethod(null)
                .build();

        assertEquals(BookingPaymentMethod.UNKNOWN, draft.getPaymentMethod());
    }

    @Test
    public void builder_bankTransferKeepsExplicitSelection() {
        BookingRequestDraft draft = BookingRequestDraft.builder()
                .paymentMethod(BookingPaymentMethod.BANK_TRANSFER)
                .build();

        assertEquals(BookingPaymentMethod.BANK_TRANSFER, draft.getPaymentMethod());
    }
}
