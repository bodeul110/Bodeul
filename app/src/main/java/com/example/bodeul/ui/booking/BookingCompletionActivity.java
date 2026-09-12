package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;

/**
 * 예약 접수 또는 수정 완료 후 Figma 기준 성공 화면을 보여준다.
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
        configureSystemBars();
        setContentView(R.layout.activity_booking_completion);
        BookingCompletionInsets.apply(findViewById(R.id.bookingCompletionRoot));

        BookingCompletionSnapshot snapshot = BookingCompletionSnapshot.fromIntent(getIntent());
        boolean isUpdated = getIntent().getBooleanExtra(EXTRA_IS_UPDATED, false);

        BookingCompletionCoordinator coordinator = new BookingCompletionCoordinator(this);
        BookingCompletionBinder binder = new BookingCompletionBinder(
                findViewById(R.id.textBookingCompletionScreenTitle),
                findViewById(R.id.textBookingCompletionTitle),
                findViewById(R.id.textBookingCompletionBody),
                findViewById(R.id.textBookingCompletionHospital),
                findViewById(R.id.textBookingCompletionDepartment),
                findViewById(R.id.textBookingCompletionDate),
                findViewById(R.id.textBookingCompletionTime),
                findViewById(R.id.textBookingCompletionNote)
        );
        binder.bind(coordinator.buildScreenModel(snapshot, isUpdated));

        findViewById(R.id.buttonBackBookingCompletion).setOnClickListener(view -> finish());
        findViewById(R.id.buttonBookingCompletionList).setOnClickListener(view -> openBookingHistory());
    }

    private void configureSystemBars() {
        EdgeToEdge.enable(this);
    }

    private void openBookingHistory() {
        Intent intent = new Intent(this, ClientBookingHistoryActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
