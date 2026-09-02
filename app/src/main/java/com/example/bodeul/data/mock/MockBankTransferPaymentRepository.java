package com.example.bodeul.data.mock;

import android.os.Handler;
import android.os.Looper;

import com.example.bodeul.data.BankTransferPaymentRepository;
import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.BankTransferPayment;
import com.example.bodeul.domain.model.BookingPaymentMethod;
import com.example.bodeul.domain.model.BookingPaymentStatus;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 실제 계좌정보 없이 무통장입금 입력 흐름만 확인하는 데모 저장소다.
 */
public final class MockBankTransferPaymentRepository
        implements BankTransferPaymentRepository {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final MockBodeulRepository mockRepository;
    private final ConcurrentHashMap<String, BankTransferPayment> payments =
            new ConcurrentHashMap<>();

    public MockBankTransferPaymentRepository(MockBodeulRepository mockRepository) {
        this.mockRepository = mockRepository;
    }

    @Override
    public void getPayment(
            String appointmentRequestId,
            RepositoryCallback<BankTransferPayment> callback
    ) {
        String normalizedId = normalize(appointmentRequestId);
        BankTransferPayment payment = payments.get(normalizedId);
        if (payment == null) {
            payment = createPayment(normalizedId);
            if (payment != null) {
                payments.put(normalizedId, payment);
            }
        }
        BankTransferPayment result = payment;
        if (result == null) {
            postError(callback, "무통장입금 예약 정보를 찾지 못했습니다.");
            return;
        }
        mainHandler.post(() -> callback.onSuccess(result));
    }

    @Override
    public void saveDepositorName(
            String appointmentRequestId,
            String depositorName,
            RepositoryCallback<BankTransferPayment> callback
    ) {
        String normalizedName = normalizeName(depositorName);
        if (normalizedName.isEmpty() || normalizedName.length() > 100) {
            postError(callback, "입금자명은 1자부터 100자까지 입력해 주세요.");
            return;
        }
        getPayment(
                appointmentRequestId,
                new RepositoryCallback<BankTransferPayment>() {
                    @Override
                    public void onSuccess(BankTransferPayment current) {
                        if (!current.canEditDepositorName()) {
                            callback.onError("현재 결제 상태에서는 입금자명을 변경할 수 없습니다.");
                            return;
                        }
                        BankTransferPayment updated = new BankTransferPayment(
                                current.getAppointmentRequestId(),
                                current.getPaymentMethod(),
                                current.getPaymentStatus(),
                                current.getExpectedAmount(),
                                normalizedName,
                                current.getPaymentDueAt(),
                                current.getReceivedAmount(),
                                current.getConfirmedAt(),
                                current.getRefundRequestedAt(),
                                current.getRefundedAt(),
                                current.getPaymentVersion() + 1L,
                                current.isInstructionAvailable()
                        );
                        payments.put(updated.getAppointmentRequestId(), updated);
                        callback.onSuccess(updated);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                }
        );
    }

    private BankTransferPayment createPayment(String appointmentRequestId) {
        AppointmentRequestDetail detail = mockRepository.getAppointmentRequestDetail(
                appointmentRequestId);
        if (detail == null) {
            return null;
        }
        AppointmentRequest request = detail.getAppointmentRequest();
        if (BookingPaymentMethod.fromValue(request.getPaymentMethodCode())
                != BookingPaymentMethod.BANK_TRANSFER) {
            return null;
        }
        BookingPaymentStatus status = BookingPaymentStatus.fromValue(
                request.getPaymentStatusCode());
        return new BankTransferPayment(
                request.getId(),
                BookingPaymentMethod.BANK_TRANSFER,
                status,
                request.getFinalPrice(),
                "",
                "",
                null,
                request.getPaymentApprovedAt(),
                "",
                "",
                0L,
                false
        );
    }

    private void postError(RepositoryCallback<BankTransferPayment> callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeName(String value) {
        return normalize(value).replaceAll("\\s+", " ");
    }
}
