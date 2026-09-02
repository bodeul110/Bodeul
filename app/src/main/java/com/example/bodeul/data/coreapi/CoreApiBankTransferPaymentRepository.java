package com.example.bodeul.data.coreapi;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.bodeul.data.BankTransferPaymentRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.BankTransferPayment;
import com.example.bodeul.domain.model.BookingPaymentMethod;
import com.example.bodeul.domain.model.BookingPaymentStatus;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID;

/**
 * 환자 본인의 무통장입금 원장 조회와 입금자명 저장을 Core API에 위임한다.
 */
public final class CoreApiBankTransferPaymentRepository
        implements BankTransferPaymentRepository {
    private final CoreApiAppointmentClient appointmentClient;
    private final CoreApiAuthenticatedClient authenticatedClient;

    public CoreApiBankTransferPaymentRepository(Context context) {
        Context appContext = context.getApplicationContext();
        appointmentClient = new CoreApiAppointmentClient(appContext);
        authenticatedClient = new CoreApiAuthenticatedClient(appContext);
    }

    @Override
    public void getPayment(
            String appointmentRequestId,
            RepositoryCallback<BankTransferPayment> callback
    ) {
        withCoreId(
                appointmentRequestId,
                callback,
                coreId -> executeGet(coreId, callback)
        );
    }

    @Override
    public void saveDepositorName(
            String appointmentRequestId,
            String depositorName,
            RepositoryCallback<BankTransferPayment> callback
    ) {
        String normalizedName = normalizeDepositorName(depositorName);
        if (!isValidDepositorName(normalizedName)) {
            authenticatedClient.postError(callback, "입금자명은 1자부터 100자까지 입력해 주세요.");
            return;
        }
        withCoreId(
                appointmentRequestId,
                callback,
                coreId -> executeGet(
                        coreId,
                        new RepositoryCallback<BankTransferPayment>() {
                            @Override
                            public void onSuccess(BankTransferPayment current) {
                                if (!current.canEditDepositorName()) {
                                    callback.onError("현재 결제 상태에서는 입금자명을 변경할 수 없습니다.");
                                    return;
                                }
                                if (normalizedName.equals(current.getDepositorName())) {
                                    callback.onSuccess(current);
                                    return;
                                }
                                executeSave(
                                        coreId,
                                        current.getPaymentVersion(),
                                        normalizedName,
                                        UUID.randomUUID(),
                                        callback
                                );
                            }

                            @Override
                            public void onError(String message) {
                                callback.onError(message);
                            }
                        }
                )
        );
    }

    private void executeGet(
            String coreId,
            RepositoryCallback<BankTransferPayment> callback
    ) {
        authenticatedClient.execute(
                (idToken, appCheckToken) -> parsePayment(authenticatedClient.requestJson(
                        "GET",
                        paymentPath(coreId),
                        null,
                        idToken,
                        appCheckToken)),
                callback,
                "무통장입금 정보를 불러오지 못했습니다.",
                "무통장입금 조회 API"
        );
    }

    private void executeSave(
            String coreId,
            long paymentVersion,
            String depositorName,
            UUID operationId,
            RepositoryCallback<BankTransferPayment> callback
    ) {
        authenticatedClient.execute(
                (idToken, appCheckToken) -> parsePayment(requestDepositorWithRetry(
                        operationId,
                        paymentVersion,
                        depositorName,
                        body -> authenticatedClient.requestJson(
                                "PATCH",
                                paymentPath(coreId) + "/depositor",
                                body,
                                idToken,
                                appCheckToken))),
                callback,
                "입금자명을 저장하지 못했습니다.",
                "입금자명 저장 API"
        );
    }

    private <T> void withCoreId(
            String appointmentRequestId,
            RepositoryCallback<T> callback,
            CoreIdOperation operation
    ) {
        appointmentClient.resolveCoreId(
                appointmentRequestId,
                new RepositoryCallback<String>() {
                    @Override
                    public void onSuccess(String coreId) {
                        if (coreId.isEmpty()) {
                            callback.onError("예약의 Core API 식별자를 확인하지 못했습니다.");
                            return;
                        }
                        operation.run(coreId);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                }
        );
    }

    static BankTransferPayment parsePayment(JSONObject response) throws JSONException {
        String appointmentRequestId = requireUuidText(response, "appointmentId");
        BookingPaymentMethod paymentMethod = BookingPaymentMethod.fromValue(
                requireText(response, "paymentMethodCode"));
        if (paymentMethod != BookingPaymentMethod.BANK_TRANSFER) {
            throw new JSONException("무통장입금 결제 원장이 아닙니다.");
        }
        BookingPaymentStatus paymentStatus = BookingPaymentStatus.fromValue(
                requireText(response, "paymentStatusCode"));
        long paymentVersion = requireNonNegativeLong(response, "paymentVersion");
        int expectedAmount = requireNonNegativeInt(response, "expectedAmount");
        if (!response.has("receivedAmount")) {
            throw new JSONException("receivedAmount 값이 없습니다.");
        }
        @Nullable Integer receivedAmount = response.isNull("receivedAmount")
                ? null
                : requireNonNegativeInt(response, "receivedAmount");
        return new BankTransferPayment(
                appointmentRequestId,
                paymentMethod,
                paymentStatus,
                expectedAmount,
                response.optString("depositorName", ""),
                response.optString("paymentDueAt", ""),
                receivedAmount,
                response.optString("confirmedAt", ""),
                response.optString("refundRequestedAt", ""),
                response.optString("refundedAt", ""),
                paymentVersion,
                response.optBoolean("instructionAvailable", false)
        );
    }

    static JSONObject buildDepositorRequest(
            UUID operationId,
            long paymentVersion,
            String depositorName
    ) throws JSONException {
        return new JSONObject()
                .put("operationId", operationId.toString())
                .put("paymentVersion", paymentVersion)
                .put("depositorName", normalizeDepositorName(depositorName));
    }

    static JSONObject requestDepositorWithRetry(
            UUID operationId,
            long paymentVersion,
            String depositorName,
            DepositorRequest request
    ) throws Exception {
        JSONObject body = buildDepositorRequest(operationId, paymentVersion, depositorName);
        try {
            return request.execute(body);
        } catch (IOException firstFailure) {
            return request.execute(body);
        }
    }

    static String normalizeDepositorName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    static boolean isValidDepositorName(String value) {
        String normalized = normalizeDepositorName(value);
        return !normalized.isEmpty() && normalized.length() <= 100;
    }

    private static String requireText(JSONObject response, String key) throws JSONException {
        String value = response.optString(key, "").trim();
        if (value.isEmpty()) {
            throw new JSONException(key + " 값이 없습니다.");
        }
        return value;
    }

    private static String requireUuidText(JSONObject response, String key) throws JSONException {
        String value = requireText(response, key);
        try {
            UUID.fromString(value);
            return value;
        } catch (IllegalArgumentException exception) {
            throw new JSONException(key + " 값이 UUID 형식이 아닙니다.");
        }
    }

    private static long requireNonNegativeLong(JSONObject response, String key)
            throws JSONException {
        Object rawValue = response.opt(key);
        if (!(rawValue instanceof Number)) {
            throw new JSONException(key + " 값이 숫자가 아닙니다.");
        }
        Number number = (Number) rawValue;
        long value = number.longValue();
        if (value < 0L || number.doubleValue() != (double) value) {
            throw new JSONException(key + " 값이 올바른 정수가 아닙니다.");
        }
        return value;
    }

    private static int requireNonNegativeInt(JSONObject response, String key)
            throws JSONException {
        long value = requireNonNegativeLong(response, key);
        if (value > Integer.MAX_VALUE) {
            throw new JSONException(key + " 값이 허용 범위를 벗어났습니다.");
        }
        return (int) value;
    }

    private String paymentPath(String coreId) {
        return "/api/appointments/" + coreId + "/payment";
    }

    private interface CoreIdOperation {
        void run(String coreId);
    }

    interface DepositorRequest {
        JSONObject execute(JSONObject body) throws Exception;
    }
}
