package com.bodeul.core.appointment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface AppointmentPaymentRepository {

    Optional<BankTransferPaymentRecord> findForPatient(UUID appointmentId, UUID patientUserId);

    Optional<BankTransferPaymentRecord> setDepositor(
            UUID appointmentId,
            UUID patientUserId,
            UUID operationId,
            long expectedPaymentVersion,
            String depositorName);

    record BankTransferPaymentRecord(
            UUID appointmentId,
            String paymentMethodCode,
            String paymentStatusCode,
            int expectedAmount,
            String depositorName,
            Instant paymentDueAt,
            Integer receivedAmount,
            Instant confirmedAt,
            Instant refundRequestedAt,
            Instant refundedAt,
            long paymentVersion) {
    }
}
