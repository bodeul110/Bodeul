package com.example.bodeul.ui.booking;

import android.content.Context;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.BankTransferPayment;
import com.example.bodeul.domain.model.BookingPaymentStatus;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class BankTransferPaymentCoordinator {
    private static final String[] ISO_PATTERNS = {
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX"
    };

    private final Context context;
    private final BookingPresentationFormatter formatter;

    public BankTransferPaymentCoordinator(
            Context context,
            BookingPresentationFormatter formatter
    ) {
        this.context = context.getApplicationContext();
        this.formatter = formatter;
    }

    public BankTransferPaymentScreenModel createScreenModel(BankTransferPayment payment) {
        return new BankTransferPaymentScreenModel(
                formatter.toPaymentStatusLabel(payment.getPaymentStatus().name()),
                resolveStatusBody(payment.getPaymentStatus()),
                formatter.formatPrice(payment.getExpectedAmount()),
                formatTimestamp(
                        payment.getPaymentDueAt(),
                        R.string.bank_transfer_payment_due_unknown),
                payment.getReceivedAmount() == null
                        ? context.getString(R.string.bank_transfer_payment_record_empty)
                        : formatter.formatPrice(payment.getReceivedAmount()),
                formatTimestamp(
                        payment.getConfirmedAt(),
                        R.string.bank_transfer_payment_record_empty),
                formatTimestamp(
                        payment.getRefundRequestedAt(),
                        R.string.bank_transfer_payment_record_empty),
                formatTimestamp(
                        payment.getRefundedAt(),
                        R.string.bank_transfer_payment_record_empty),
                payment.hasDepositorName()
                        ? payment.getDepositorName()
                        : context.getString(R.string.bank_transfer_payment_depositor_empty),
                payment.getDepositorName(),
                payment.canEditDepositorName()
                        ? context.getString(R.string.bank_transfer_payment_depositor_helper_editable)
                        : context.getString(R.string.bank_transfer_payment_depositor_helper_locked),
                context.getString(payment.isInstructionAvailable()
                        ? R.string.bank_transfer_payment_instruction_pending_display
                        : R.string.bank_transfer_payment_instruction_unavailable),
                payment.canEditDepositorName()
        );
    }

    private String resolveStatusBody(BookingPaymentStatus status) {
        switch (status) {
            case AWAITING_DEPOSIT:
                return context.getString(R.string.bank_transfer_payment_status_body_waiting);
            case REVIEW_REQUIRED:
                return context.getString(R.string.bank_transfer_payment_status_body_review);
            case DEPOSIT_CONFIRMED:
                return context.getString(R.string.bank_transfer_payment_status_body_confirmed);
            case REFUND_REQUESTED:
                return context.getString(R.string.bank_transfer_payment_status_body_refund_requested);
            case REFUNDED:
                return context.getString(R.string.bank_transfer_payment_status_body_refunded);
            case CANCELED:
                return context.getString(R.string.bank_transfer_payment_status_body_canceled);
            case UNKNOWN:
                return context.getString(R.string.bank_transfer_payment_status_body_unknown);
            case PENDING:
            case AUTHORIZED:
            case DEFERRED:
            default:
                return context.getString(R.string.bank_transfer_payment_status_body_pending);
        }
    }

    private String formatTimestamp(String value, int emptyTextResId) {
        if (value == null || value.trim().isEmpty()) {
            return context.getString(emptyTextResId);
        }
        Date parsed = parseIsoTimestamp(value.trim());
        if (parsed == null) {
            return context.getString(emptyTextResId);
        }
        SimpleDateFormat display = new SimpleDateFormat(
                "yyyy년 M월 d일 a h시 m분",
                Locale.KOREA);
        display.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        return display.format(parsed);
    }

    static Date parseIsoTimestamp(String value) {
        String normalized = normalizeFraction(value);
        for (String pattern : ISO_PATTERNS) {
            SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
            parser.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date parsed = parser.parse(normalized, position);
            if (parsed != null && position.getIndex() == normalized.length()) {
                return parsed;
            }
        }
        return null;
    }

    private static String normalizeFraction(String value) {
        int dotIndex = value.indexOf('.');
        if (dotIndex < 0) {
            return value;
        }
        int zoneIndex = value.indexOf('Z', dotIndex);
        if (zoneIndex < 0) {
            int plusIndex = value.indexOf('+', dotIndex);
            int minusIndex = value.indexOf('-', dotIndex);
            zoneIndex = plusIndex >= 0 ? plusIndex : minusIndex;
        }
        if (zoneIndex < 0) {
            return value;
        }
        String fraction = value.substring(dotIndex + 1, zoneIndex);
        if (fraction.isEmpty() || !fraction.matches("\\d+")) {
            return value;
        }
        if (fraction.length() == 3) {
            return value;
        }
        String millis = (fraction + "000").substring(0, 3);
        return value.substring(0, dotIndex + 1) + millis + value.substring(zoneIndex);
    }
}
