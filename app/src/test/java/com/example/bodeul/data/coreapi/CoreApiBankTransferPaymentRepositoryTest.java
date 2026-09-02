package com.example.bodeul.data.coreapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.BankTransferPayment;
import com.example.bodeul.domain.model.BookingPaymentMethod;
import com.example.bodeul.domain.model.BookingPaymentStatus;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.UUID;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CoreApiBankTransferPaymentRepositoryTest {
    @Test
    public void parsePayment_preservesBankTransferLedgerFields() throws Exception {
        BankTransferPayment payment = CoreApiBankTransferPaymentRepository.parsePayment(
                createPaymentJson());

        assertEquals("5badeede-222d-4fcc-b357-29611528886a", payment.getAppointmentRequestId());
        assertEquals(BookingPaymentMethod.BANK_TRANSFER, payment.getPaymentMethod());
        assertEquals(BookingPaymentStatus.AWAITING_DEPOSIT, payment.getPaymentStatus());
        assertEquals(40_000, payment.getExpectedAmount());
        assertEquals("안진영", payment.getDepositorName());
        assertEquals("2026-09-03T03:00:00Z", payment.getPaymentDueAt());
        assertNull(payment.getReceivedAmount());
        assertEquals("", payment.getConfirmedAt());
        assertEquals("", payment.getRefundRequestedAt());
        assertEquals("", payment.getRefundedAt());
        assertEquals(7L, payment.getPaymentVersion());
        assertFalse(payment.isInstructionAvailable());
    }

    @Test
    public void parsePayment_mapsUnknownStatusWithoutInventingKnownState() throws Exception {
        JSONObject fixture = createPaymentJson()
                .put("paymentStatusCode", "SERVER_EXTENSION")
                .put("receivedAmount", 41_000)
                .put("instructionAvailable", true);

        BankTransferPayment payment = CoreApiBankTransferPaymentRepository.parsePayment(fixture);

        assertEquals(BookingPaymentMethod.BANK_TRANSFER, payment.getPaymentMethod());
        assertEquals(BookingPaymentStatus.UNKNOWN, payment.getPaymentStatus());
        assertEquals(Integer.valueOf(41_000), payment.getReceivedAmount());
        assertTrue(payment.isInstructionAvailable());
        assertFalse(payment.canEditDepositorName());
    }

    @Test
    public void parsePayment_rejectsUnknownMethod() throws Exception {
        try {
            CoreApiBankTransferPaymentRepository.parsePayment(
                    createPaymentJson().put("paymentMethodCode", "SERVER_EXTENSION"));
        } catch (JSONException expected) {
            return;
        }
        throw new AssertionError("알 수 없는 결제 수단 응답은 거부해야 합니다.");
    }

    @Test(expected = JSONException.class)
    public void parsePayment_rejectsNegativeLedgerValues() throws Exception {
        CoreApiBankTransferPaymentRepository.parsePayment(
                createPaymentJson().put("paymentVersion", -1L));
    }

    @Test(expected = JSONException.class)
    public void parsePayment_rejectsMissingExpectedAmount() throws Exception {
        JSONObject fixture = createPaymentJson();
        fixture.remove("expectedAmount");

        CoreApiBankTransferPaymentRepository.parsePayment(fixture);
    }

    @Test(expected = JSONException.class)
    public void parsePayment_rejectsStringExpectedAmount() throws Exception {
        CoreApiBankTransferPaymentRepository.parsePayment(
                createPaymentJson().put("expectedAmount", "40000"));
    }

    @Test(expected = JSONException.class)
    public void parsePayment_rejectsFractionalExpectedAmount() throws Exception {
        CoreApiBankTransferPaymentRepository.parsePayment(
                createPaymentJson().put("expectedAmount", 40_000.5));
    }

    @Test(expected = JSONException.class)
    public void parsePayment_rejectsNegativeExpectedAmount() throws Exception {
        CoreApiBankTransferPaymentRepository.parsePayment(
                createPaymentJson().put("expectedAmount", -1));
    }

    @Test(expected = JSONException.class)
    public void parsePayment_rejectsMissingReceivedAmount() throws Exception {
        JSONObject fixture = createPaymentJson();
        fixture.remove("receivedAmount");

        CoreApiBankTransferPaymentRepository.parsePayment(fixture);
    }

    @Test(expected = JSONException.class)
    public void parsePayment_rejectsStringReceivedAmount() throws Exception {
        CoreApiBankTransferPaymentRepository.parsePayment(
                createPaymentJson().put("receivedAmount", "40000"));
    }

    @Test(expected = JSONException.class)
    public void parsePayment_rejectsNegativeReceivedAmount() throws Exception {
        CoreApiBankTransferPaymentRepository.parsePayment(
                createPaymentJson().put("receivedAmount", -1));
    }

    @Test(expected = JSONException.class)
    public void parsePayment_rejectsFractionalReceivedAmount() throws Exception {
        CoreApiBankTransferPaymentRepository.parsePayment(
                createPaymentJson().put("receivedAmount", 40_000.5));
    }

    @Test(expected = JSONException.class)
    public void parsePayment_rejectsMissingAppointmentId() throws Exception {
        CoreApiBankTransferPaymentRepository.parsePayment(
                createPaymentJson().put("appointmentId", " "));
    }

    @Test(expected = JSONException.class)
    public void parsePayment_rejectsMissingPaymentVersion() throws Exception {
        JSONObject fixture = createPaymentJson();
        fixture.remove("paymentVersion");

        CoreApiBankTransferPaymentRepository.parsePayment(fixture);
    }

    @Test
    public void buildDepositorRequest_preservesOperationAndVersion() throws Exception {
        UUID operationId = UUID.fromString("1974b0eb-3eb2-4013-a831-770f07a659d7");

        JSONObject request = CoreApiBankTransferPaymentRepository.buildDepositorRequest(
                operationId,
                7L,
                "  안진영   보호자  ");

        assertEquals(operationId.toString(), request.getString("operationId"));
        assertEquals(7L, request.getLong("paymentVersion"));
        assertEquals("안진영 보호자", request.getString("depositorName"));
    }

    @Test
    public void requestDepositorWithRetry_reusesSameBodyAndOperationId() throws Exception {
        UUID operationId = UUID.fromString("5bfefad7-27d1-47cb-b93d-419a0ce38c45");
        List<JSONObject> attempts = new ArrayList<>();

        JSONObject response = CoreApiBankTransferPaymentRepository.requestDepositorWithRetry(
                operationId,
                9L,
                " 안진영 ",
                body -> {
                    attempts.add(body);
                    if (attempts.size() == 1) {
                        throw new IOException("응답 유실");
                    }
                    return createPaymentJson();
                });

        assertEquals(2, attempts.size());
        assertSame(attempts.get(0), attempts.get(1));
        assertEquals(operationId.toString(), attempts.get(1).getString("operationId"));
        assertEquals(9L, attempts.get(1).getLong("paymentVersion"));
        assertEquals("안진영", attempts.get(1).getString("depositorName"));
        assertEquals(
                "5badeede-222d-4fcc-b357-29611528886a",
                response.getString("appointmentId"));
    }

    @Test
    public void validateDepositorName_enforcesServerLengthContract() {
        assertFalse(CoreApiBankTransferPaymentRepository.isValidDepositorName("   "));
        assertTrue(CoreApiBankTransferPaymentRepository.isValidDepositorName(" 안진영 "));
        assertTrue(CoreApiBankTransferPaymentRepository.isValidDepositorName("가".repeat(100)));
        assertFalse(CoreApiBankTransferPaymentRepository.isValidDepositorName("가".repeat(101)));
    }

    private JSONObject createPaymentJson() throws Exception {
        return new JSONObject()
                .put("appointmentId", "5badeede-222d-4fcc-b357-29611528886a")
                .put("paymentMethodCode", "BANK_TRANSFER")
                .put("paymentStatusCode", "AWAITING_DEPOSIT")
                .put("expectedAmount", 40_000)
                .put("depositorName", "안진영")
                .put("paymentDueAt", "2026-09-03T03:00:00Z")
                .put("receivedAmount", JSONObject.NULL)
                .put("confirmedAt", "")
                .put("refundRequestedAt", "")
                .put("refundedAt", "")
                .put("paymentVersion", 7L)
                .put("instructionAvailable", false);
    }
}
