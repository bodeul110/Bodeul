package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;

/**
 * 예약 접수 또는 수정 완료 후 최종 요약을 보여준다.
 */
public class BookingCompletionActivity extends AppCompatActivity {
    private static final String EXTRA_IS_UPDATED = "isUpdated";

    public static Intent createIntent(Context context, AppointmentRequest request, boolean isUpdated) {
        Intent intent = new Intent(context, BookingCompletionActivity.class);
        BookingCompletionSnapshot.fromRequest(request).writeToIntent(intent);
        intent.putExtra(EXTRA_IS_UPDATED, isUpdated);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_completion);

        BookingCompletionSnapshot snapshot = BookingCompletionSnapshot.fromIntent(getIntent());
        boolean isUpdated = getIntent().getBooleanExtra(EXTRA_IS_UPDATED, false);

        BookingCompletionCoordinator coordinator = new BookingCompletionCoordinator(
                this,
                new BookingPresentationFormatter(this)
        );
        BookingCompletionBinder binder = new BookingCompletionBinder(
                findViewById(R.id.textBookingCompletionBadge),
                findViewById(R.id.textBookingCompletionTitle),
                findViewById(R.id.textBookingCompletionBody),
                findViewById(R.id.textBookingCompletionRequestId),
                findViewById(R.id.textBookingCompletionSchedule),
                findViewById(R.id.textBookingCompletionHospital),
                findViewById(R.id.textBookingCompletionMeetingPlace),
                findViewById(R.id.textBookingCompletionOption),
                findViewById(R.id.textBookingCompletionPayment),
                findViewById(R.id.layoutBookingCompletionNote),
                findViewById(R.id.textBookingCompletionNote)
        );
        binder.bind(coordinator.buildScreenModel(snapshot, isUpdated));

        findViewById(R.id.buttonBackBookingCompletion).setOnClickListener(view -> finish());
        findViewById(R.id.buttonBookingCompletionList).setOnClickListener(view -> finish());
        findViewById(R.id.buttonBookingCompletionDetail).setOnClickListener(view ->
                startActivity(BookingStatusActivity.createIntent(this, snapshot.getRequestId())));
    }
}
