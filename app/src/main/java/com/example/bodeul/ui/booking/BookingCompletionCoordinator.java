package com.example.bodeul.ui.booking;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;

import java.util.Locale;

/**
 * 예약 완료 화면에 필요한 표시 문구를 조합한다.
 */
public final class BookingCompletionCoordinator {
    private final Context context;

    public BookingCompletionCoordinator(Context context) {
        this.context = context;
    }

    public BookingCompletionScreenModel buildScreenModel(
            BookingCompletionSnapshot snapshot,
            boolean isUpdated
    ) {
        BookingCompletionDateTime.DisplayValue dateTime = BookingCompletionDateTime.format(
                snapshot.getAppointmentAt(),
                Locale.KOREA
        );
        String hospital = valueOrFallback(
                snapshot.getHospitalName(),
                R.string.booking_completion_hospital_pending
        );
        String department = valueOrFallback(
                snapshot.getDepartmentName(),
                R.string.booking_completion_department_pending
        );
        String date = valueOrFallback(
                dateTime.getDate(),
                R.string.booking_completion_schedule_pending
        );
        String note = valueOrFallback(
                snapshot.getSpecialNotes(),
                R.string.booking_completion_note_empty
        );

        return new BookingCompletionScreenModel(
                context.getString(isUpdated
                        ? R.string.booking_completion_screen_title_updated
                        : R.string.booking_completion_screen_title_submitted),
                context.getString(isUpdated
                        ? R.string.booking_completion_title_updated
                        : R.string.booking_completion_title_submitted),
                context.getString(isUpdated
                        ? R.string.booking_completion_body_updated
                        : R.string.booking_completion_body_submitted),
                hospital,
                department,
                date,
                dateTime.getTime(),
                note
        );
    }

    private String valueOrFallback(String value, int fallbackResId) {
        return TextUtils.isEmpty(value) ? context.getString(fallbackResId) : value;
    }
}
