package com.example.bodeul.ui.booking;

import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.text.format.DateFormat;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * 방문 날짜와 시간 선택 로직을 화면 밖으로 분리한다.
 */
public final class BookingAppointmentSelector {
    private static final Pattern APPOINTMENT_AT_PATTERN =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$");
    private static final String SEOUL_TIME_ZONE = "Asia/Seoul";

    private final AppCompatActivity activity;
    private final TextInputLayout layoutAppointmentAt;
    private final TextInputEditText inputAppointmentAt;
    private final MaterialButton buttonQuickToday;
    private final MaterialButton buttonQuickTomorrow;
    private final MaterialButton buttonQuickDayAfterTomorrow;
    private final MaterialButton buttonQuickMorning;
    private final MaterialButton buttonQuickAfternoon;
    private final MaterialButton buttonQuickLateAfternoon;

    public BookingAppointmentSelector(
            AppCompatActivity activity,
            TextInputLayout layoutAppointmentAt,
            TextInputEditText inputAppointmentAt,
            MaterialButton buttonQuickToday,
            MaterialButton buttonQuickTomorrow,
            MaterialButton buttonQuickDayAfterTomorrow,
            MaterialButton buttonQuickMorning,
            MaterialButton buttonQuickAfternoon,
            MaterialButton buttonQuickLateAfternoon
    ) {
        this.activity = activity;
        this.layoutAppointmentAt = layoutAppointmentAt;
        this.inputAppointmentAt = inputAppointmentAt;
        this.buttonQuickToday = buttonQuickToday;
        this.buttonQuickTomorrow = buttonQuickTomorrow;
        this.buttonQuickDayAfterTomorrow = buttonQuickDayAfterTomorrow;
        this.buttonQuickMorning = buttonQuickMorning;
        this.buttonQuickAfternoon = buttonQuickAfternoon;
        this.buttonQuickLateAfternoon = buttonQuickLateAfternoon;

        configureAppointmentPicker();
        configureQuickAppointmentButtons();
    }

    public String getAppointmentAt() {
        return inputAppointmentAt.getText() == null
                ? ""
                : inputAppointmentAt.getText().toString().trim();
    }

    public void setAppointmentAt(String appointmentAt) {
        inputAppointmentAt.setText(appointmentAt);
        layoutAppointmentAt.setError(null);
        refreshQuickAppointmentButtons();
    }

    public void clear() {
        inputAppointmentAt.setText(null);
        layoutAppointmentAt.setError(null);
        refreshQuickAppointmentButtons();
    }

    public void setEnabled(boolean enabled) {
        inputAppointmentAt.setEnabled(enabled);
        buttonQuickToday.setEnabled(enabled);
        buttonQuickTomorrow.setEnabled(enabled);
        buttonQuickDayAfterTomorrow.setEnabled(enabled);
        buttonQuickMorning.setEnabled(enabled);
        buttonQuickAfternoon.setEnabled(enabled);
        buttonQuickLateAfternoon.setEnabled(enabled);
    }

    public boolean validateRequiredAndFormat() {
        String appointmentAt = getAppointmentAt();
        if (TextUtils.isEmpty(appointmentAt)) {
            layoutAppointmentAt.setError(activity.getString(R.string.error_required_field));
            return false;
        }
        if (!APPOINTMENT_AT_PATTERN.matcher(appointmentAt).matches()) {
            layoutAppointmentAt.setError(activity.getString(R.string.error_booking_appointment_format));
            return false;
        }
        layoutAppointmentAt.setError(null);
        return true;
    }

    private void configureAppointmentPicker() {
        inputAppointmentAt.setOnClickListener(view -> openAppointmentDatePicker());
        layoutAppointmentAt.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        layoutAppointmentAt.setEndIconDrawable(android.R.drawable.ic_menu_my_calendar);
        layoutAppointmentAt.setEndIconOnClickListener(view -> openAppointmentDatePicker());
        layoutAppointmentAt.setOnClickListener(view -> openAppointmentDatePicker());
    }

    private void configureQuickAppointmentButtons() {
        buttonQuickToday.setOnClickListener(view -> applyQuickAppointmentDate(0));
        buttonQuickTomorrow.setOnClickListener(view -> applyQuickAppointmentDate(1));
        buttonQuickDayAfterTomorrow.setOnClickListener(view -> applyQuickAppointmentDate(2));
        buttonQuickMorning.setOnClickListener(view -> applyQuickAppointmentTime(10, 0));
        buttonQuickAfternoon.setOnClickListener(view -> applyQuickAppointmentTime(14, 0));
        buttonQuickLateAfternoon.setOnClickListener(view -> applyQuickAppointmentTime(16, 0));
        refreshQuickAppointmentButtons();
    }

    private void openAppointmentDatePicker() {
        Long initialSelection = resolveInitialAppointmentDateSelection();
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.booking_date_picker_title)
                .setSelection(initialSelection == null
                        ? MaterialDatePicker.todayInUtcMilliseconds()
                        : initialSelection)
                .build();

        datePicker.addOnPositiveButtonClickListener(selection ->
                openAppointmentTimePicker(selection == null
                        ? MaterialDatePicker.todayInUtcMilliseconds()
                        : selection));
        datePicker.show(activity.getSupportFragmentManager(), "bookingAppointmentDatePicker");
    }

    private void openAppointmentTimePicker(long selectedDateUtcMillis) {
        Calendar initialCalendar = resolveInitialTimeCalendar(selectedDateUtcMillis);
        MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(activity) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(initialCalendar.get(Calendar.HOUR_OF_DAY))
                .setMinute(initialCalendar.get(Calendar.MINUTE))
                .setTitleText(R.string.booking_time_picker_title)
                .build();

        timePicker.addOnPositiveButtonClickListener(view -> applyAppointmentDateTime(
                selectedDateUtcMillis,
                timePicker.getHour(),
                timePicker.getMinute()
        ));
        timePicker.show(activity.getSupportFragmentManager(), "bookingAppointmentTimePicker");
    }

    private void applyAppointmentDateTime(long selectedDateUtcMillis, int hourOfDay, int minute) {
        Calendar utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.KOREA);
        utcCalendar.setTimeInMillis(selectedDateUtcMillis);

        Calendar seoulCalendar = Calendar.getInstance(TimeZone.getTimeZone(SEOUL_TIME_ZONE), Locale.KOREA);
        seoulCalendar.set(Calendar.YEAR, utcCalendar.get(Calendar.YEAR));
        seoulCalendar.set(Calendar.MONTH, utcCalendar.get(Calendar.MONTH));
        seoulCalendar.set(Calendar.DAY_OF_MONTH, utcCalendar.get(Calendar.DAY_OF_MONTH));
        seoulCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        seoulCalendar.set(Calendar.MINUTE, minute);
        seoulCalendar.set(Calendar.SECOND, 0);
        seoulCalendar.set(Calendar.MILLISECOND, 0);

        inputAppointmentAt.setText(formatAppointmentAt(seoulCalendar.getTimeInMillis()));
        layoutAppointmentAt.setError(null);
        refreshQuickAppointmentButtons();
    }

    private void applyQuickAppointmentDate(int dayOffset) {
        Calendar baseCalendar = resolveBaseAppointmentCalendar();
        Calendar targetCalendar = Calendar.getInstance(TimeZone.getTimeZone(SEOUL_TIME_ZONE), Locale.KOREA);
        targetCalendar.add(Calendar.DAY_OF_MONTH, dayOffset);

        baseCalendar.set(Calendar.YEAR, targetCalendar.get(Calendar.YEAR));
        baseCalendar.set(Calendar.MONTH, targetCalendar.get(Calendar.MONTH));
        baseCalendar.set(Calendar.DAY_OF_MONTH, targetCalendar.get(Calendar.DAY_OF_MONTH));
        inputAppointmentAt.setText(formatAppointmentAt(baseCalendar.getTimeInMillis()));
        layoutAppointmentAt.setError(null);
        refreshQuickAppointmentButtons();
    }

    private void applyQuickAppointmentTime(int hourOfDay, int minute) {
        Calendar baseCalendar = resolveBaseAppointmentCalendar();
        baseCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        baseCalendar.set(Calendar.MINUTE, minute);
        baseCalendar.set(Calendar.SECOND, 0);
        baseCalendar.set(Calendar.MILLISECOND, 0);
        inputAppointmentAt.setText(formatAppointmentAt(baseCalendar.getTimeInMillis()));
        layoutAppointmentAt.setError(null);
        refreshQuickAppointmentButtons();
    }

    private void refreshQuickAppointmentButtons() {
        Calendar selectedCalendar = parseAppointmentCalendar(getAppointmentAt());
        int selectedDateOffset = resolveSelectedDateOffset(selectedCalendar);
        bindQuickButtonStyle(buttonQuickToday, selectedDateOffset == 0);
        bindQuickButtonStyle(buttonQuickTomorrow, selectedDateOffset == 1);
        bindQuickButtonStyle(buttonQuickDayAfterTomorrow, selectedDateOffset == 2);

        boolean hasSelectedTime = selectedCalendar != null;
        bindQuickButtonStyle(
                buttonQuickMorning,
                hasSelectedTime
                        && selectedCalendar.get(Calendar.HOUR_OF_DAY) == 10
                        && selectedCalendar.get(Calendar.MINUTE) == 0
        );
        bindQuickButtonStyle(
                buttonQuickAfternoon,
                hasSelectedTime
                        && selectedCalendar.get(Calendar.HOUR_OF_DAY) == 14
                        && selectedCalendar.get(Calendar.MINUTE) == 0
        );
        bindQuickButtonStyle(
                buttonQuickLateAfternoon,
                hasSelectedTime
                        && selectedCalendar.get(Calendar.HOUR_OF_DAY) == 16
                        && selectedCalendar.get(Calendar.MINUTE) == 0
        );
    }

    private int resolveSelectedDateOffset(@Nullable Calendar selectedCalendar) {
        if (selectedCalendar == null) {
            return -1;
        }

        Calendar todayCalendar = Calendar.getInstance(TimeZone.getTimeZone(SEOUL_TIME_ZONE), Locale.KOREA);
        normalizeDateOnly(todayCalendar);

        Calendar selectedDateCalendar = (Calendar) selectedCalendar.clone();
        normalizeDateOnly(selectedDateCalendar);
        long diffMillis = selectedDateCalendar.getTimeInMillis() - todayCalendar.getTimeInMillis();
        long dayMillis = 24L * 60L * 60L * 1000L;
        if (diffMillis < 0L || diffMillis % dayMillis != 0L) {
            return -1;
        }

        long dayOffset = diffMillis / dayMillis;
        return dayOffset <= 2L ? (int) dayOffset : -1;
    }

    private void normalizeDateOnly(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private void bindQuickButtonStyle(MaterialButton button, boolean selected) {
        if (selected) {
            button.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(activity, R.color.bodeul_primary)
            ));
            button.setStrokeColor(ColorStateList.valueOf(
                    ContextCompat.getColor(activity, R.color.bodeul_primary)
            ));
            button.setTextColor(ContextCompat.getColor(activity, R.color.white));
            return;
        }
        button.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(activity, R.color.white)
        ));
        button.setStrokeColor(ColorStateList.valueOf(
                ContextCompat.getColor(activity, R.color.bodeul_outline)
        ));
        button.setTextColor(ContextCompat.getColor(activity, R.color.bodeul_primary));
    }

    private Calendar resolveBaseAppointmentCalendar() {
        Calendar parsedCalendar = parseAppointmentCalendar(getAppointmentAt());
        if (parsedCalendar != null) {
            return parsedCalendar;
        }

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(SEOUL_TIME_ZONE), Locale.KOREA);
        calendar.set(Calendar.HOUR_OF_DAY, 10);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    @Nullable
    private Long resolveInitialAppointmentDateSelection() {
        Calendar parsedCalendar = parseAppointmentCalendar(getAppointmentAt());
        if (parsedCalendar == null) {
            return null;
        }

        Calendar utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.KOREA);
        utcCalendar.set(Calendar.YEAR, parsedCalendar.get(Calendar.YEAR));
        utcCalendar.set(Calendar.MONTH, parsedCalendar.get(Calendar.MONTH));
        utcCalendar.set(Calendar.DAY_OF_MONTH, parsedCalendar.get(Calendar.DAY_OF_MONTH));
        utcCalendar.set(Calendar.HOUR_OF_DAY, 0);
        utcCalendar.set(Calendar.MINUTE, 0);
        utcCalendar.set(Calendar.SECOND, 0);
        utcCalendar.set(Calendar.MILLISECOND, 0);
        return utcCalendar.getTimeInMillis();
    }

    private Calendar resolveInitialTimeCalendar(long selectedDateUtcMillis) {
        Calendar parsedCalendar = parseAppointmentCalendar(getAppointmentAt());
        if (parsedCalendar != null) {
            return parsedCalendar;
        }

        Calendar utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.KOREA);
        utcCalendar.setTimeInMillis(selectedDateUtcMillis);

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(SEOUL_TIME_ZONE), Locale.KOREA);
        calendar.set(Calendar.YEAR, utcCalendar.get(Calendar.YEAR));
        calendar.set(Calendar.MONTH, utcCalendar.get(Calendar.MONTH));
        calendar.set(Calendar.DAY_OF_MONTH, utcCalendar.get(Calendar.DAY_OF_MONTH));
        calendar.set(Calendar.HOUR_OF_DAY, 10);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    @Nullable
    private Calendar parseAppointmentCalendar(String appointmentAt) {
        if (!APPOINTMENT_AT_PATTERN.matcher(appointmentAt).matches()) {
            return null;
        }

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
        formatter.setLenient(false);
        formatter.setTimeZone(TimeZone.getTimeZone(SEOUL_TIME_ZONE));
        try {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(SEOUL_TIME_ZONE), Locale.KOREA);
            calendar.setTime(formatter.parse(appointmentAt));
            return calendar;
        } catch (ParseException exception) {
            return null;
        }
    }

    private String formatAppointmentAt(long appointmentAtMillis) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
        formatter.setTimeZone(TimeZone.getTimeZone(SEOUL_TIME_ZONE));
        return formatter.format(appointmentAtMillis);
    }
}
