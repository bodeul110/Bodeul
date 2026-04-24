package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingPaymentApproval;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 예약 접수 전에 결제 승인 또는 현장 결제 확정을 마무리하는 화면이다.
 */
public class BookingPaymentApprovalActivity extends AppCompatActivity {
    private static final String EXTRA_PAYMENT_STATUS = "paymentStatus";
    private static final String EXTRA_PAYMENT_PROVIDER = "paymentProvider";
    private static final String EXTRA_PAYMENT_APPROVAL_CODE = "paymentApprovalCode";
    private static final String EXTRA_PAYMENT_APPROVED_AT = "paymentApprovedAt";

    public static Intent createIntent(Context context, BookingPaymentCheckoutSnapshot snapshot) {
        Intent intent = new Intent(context, BookingPaymentApprovalActivity.class);
        snapshot.writeToIntent(intent);
        return intent;
    }

    @Nullable
    public static BookingPaymentApproval parseResult(@Nullable Intent data) {
        if (data == null) {
            return null;
        }
        String status = data.getStringExtra(EXTRA_PAYMENT_STATUS);
        String provider = data.getStringExtra(EXTRA_PAYMENT_PROVIDER);
        String approvalCode = data.getStringExtra(EXTRA_PAYMENT_APPROVAL_CODE);
        String approvedAt = data.getStringExtra(EXTRA_PAYMENT_APPROVED_AT);
        if (TextUtils.isEmpty(status)) {
            return null;
        }
        if ("DEFERRED".equals(status)) {
            return BookingPaymentApproval.deferred(provider, approvedAt);
        }
        return BookingPaymentApproval.authorized(provider, approvalCode, approvedAt);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_payment_approval);

        BookingPaymentCheckoutSnapshot snapshot = BookingPaymentCheckoutSnapshot.fromIntent(getIntent());
        BookingPaymentApprovalScreenModel screenModel = new BookingPaymentApprovalCoordinator(
                this,
                new BookingPresentationFormatter(this)
        ).createScreenModel(snapshot);

        CheckBox checkConsent = findViewById(R.id.checkBookingPaymentConsent);
        new BookingPaymentApprovalBinder(
                findViewById(R.id.textBookingPaymentBadge),
                findViewById(R.id.textBookingPaymentTitle),
                findViewById(R.id.textBookingPaymentBody),
                findViewById(R.id.textBookingPaymentMethod),
                findViewById(R.id.textBookingPaymentCoupon),
                findViewById(R.id.textBookingPaymentAmount),
                findViewById(R.id.textBookingPaymentSchedule),
                findViewById(R.id.textBookingPaymentHospital),
                findViewById(R.id.textBookingPaymentMeetingPlace),
                checkConsent,
                findViewById(R.id.buttonBookingPaymentApprove)
        ).bind(screenModel);

        findViewById(R.id.buttonBackBookingPayment).setOnClickListener(view -> finish());
        findViewById(R.id.buttonBookingPaymentApprove).setOnClickListener(view -> {
            if (!checkConsent.isChecked()) {
                Toast.makeText(
                        this,
                        R.string.booking_payment_approval_consent_required,
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
            finishWithApproval(screenModel);
        });
    }

    private void finishWithApproval(BookingPaymentApprovalScreenModel screenModel) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(
                EXTRA_PAYMENT_STATUS,
                screenModel.isDeferredPayment() ? "DEFERRED" : "AUTHORIZED"
        );
        resultIntent.putExtra(EXTRA_PAYMENT_PROVIDER, screenModel.getProviderLabel());
        resultIntent.putExtra(EXTRA_PAYMENT_APPROVAL_CODE, buildApprovalCode(screenModel.isDeferredPayment()));
        resultIntent.putExtra(EXTRA_PAYMENT_APPROVED_AT, currentApprovedAt());
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private String buildApprovalCode(boolean deferredPayment) {
        if (deferredPayment) {
            return "";
        }
        return "BODEUL-" + System.currentTimeMillis();
    }

    private String currentApprovedAt() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(new Date());
    }
}
