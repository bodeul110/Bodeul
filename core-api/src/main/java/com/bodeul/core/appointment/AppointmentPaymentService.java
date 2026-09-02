package com.bodeul.core.appointment;

import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;

public interface AppointmentPaymentService {

    BankTransferPaymentView getPayment(
            AppUserRepository.AppUser appUser,
            UUID appointmentId);

    BankTransferPaymentView setDepositor(
            AppUserRepository.AppUser appUser,
            UUID appointmentId,
            SetDepositorCommand command);

    record SetDepositorCommand(
            UUID operationId,
            long paymentVersion,
            String depositorName) {
    }

    record BankTransferPaymentView(
            UUID appointmentId,
            String paymentMethodCode,
            String paymentStatusCode,
            int expectedAmount,
            String depositorName,
            String paymentDueAt,
            Integer receivedAmount,
            String confirmedAt,
            String refundRequestedAt,
            String refundedAt,
            long paymentVersion,
            boolean instructionAvailable) {
    }
}
