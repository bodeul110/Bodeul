package com.example.bodeul.data;

import com.example.bodeul.domain.model.BankTransferPayment;

public interface BankTransferPaymentRepository {
    void getPayment(
            String appointmentRequestId,
            RepositoryCallback<BankTransferPayment> callback
    );

    void saveDepositorName(
            String appointmentRequestId,
            String depositorName,
            RepositoryCallback<BankTransferPayment> callback
    );
}
