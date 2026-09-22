package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.bodeul.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.Calendar;

/**
 * 방문 날짜와 시간을 한 화면에서 선택하고 예약 폼에 결과를 반환한다.
 */
public class BookingAppointmentSelectorActivity extends AppCompatActivity {
    private static final String EXTRA_INITIAL_APPOINTMENT_AT = "initialAppointmentAt";
    private static final String EXTRA_SELECTED_APPOINTMENT_AT = "selectedAppointmentAt";
    private static final String STATE_SELECTED_DATE = "selectedDate";
    private static final String STATE_DISPLAYED_MONTH = "displayedMonth";
    private static final String STATE_SELECTED_HOUR = "selectedHour";
    private static final String STATE_SELECTED_MINUTE = "selectedMinute";
    private static final int[][] TIME_SLOTS = {
            {9, 0}, {9, 30}, {10, 0},
            {10, 30}, {11, 0}, {11, 30},
            {13, 0}, {13, 30}, {14, 0}
    };
    private Calendar selectedDate;
    private Calendar displayedMonth;
    private int selectedHour;
    private int selectedMinute;

    private TextView textDisplayedMonth;
    private TextView textSelectedTime;
    private GridLayout calendarGrid;
    private GridLayout timeGrid;
    private ImageButton buttonPreviousMonth;

    public static Intent createIntent(Context context, @Nullable String appointmentAt) {
        Intent intent = new Intent(context, BookingAppointmentSelectorActivity.class);
        intent.putExtra(EXTRA_INITIAL_APPOINTMENT_AT, appointmentAt == null ? "" : appointmentAt);
        return intent;
    }

    public static String parseResult(@Nullable Intent data) {
        if (data == null) {
            return "";
        }
        String appointmentAt = data.getStringExtra(EXTRA_SELECTED_APPOINTMENT_AT);
        return appointmentAt == null ? "" : appointmentAt;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_appointment_selector);
        configureSystemBars();
        bindViews();
        restoreSelection(savedInstanceState);
        bindActions();
        render();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_SELECTED_DATE, selectedDate.getTimeInMillis());
        outState.putLong(STATE_DISPLAYED_MONTH, displayedMonth.getTimeInMillis());
        outState.putInt(STATE_SELECTED_HOUR, selectedHour);
        outState.putInt(STATE_SELECTED_MINUTE, selectedMinute);
    }

    private void bindViews() {
        textDisplayedMonth = findViewById(R.id.textBookingAppointmentDisplayedMonth);
        textSelectedTime = findViewById(R.id.textBookingAppointmentSelectedTime);
        calendarGrid = findViewById(R.id.gridBookingAppointmentCalendar);
        timeGrid = findViewById(R.id.gridBookingAppointmentTimes);
        buttonPreviousMonth = findViewById(R.id.buttonBookingAppointmentPreviousMonth);
    }

    private void restoreSelection(@Nullable Bundle savedInstanceState) {
        Calendar initial = BookingAppointmentDateTime.resolveInitialSelection(
                getIntent().getStringExtra(EXTRA_INITIAL_APPOINTMENT_AT),
                System.currentTimeMillis()
        );
        selectedDate = BookingAppointmentDateTime.newCalendar();
        displayedMonth = BookingAppointmentDateTime.newCalendar();

        if (savedInstanceState != null) {
            selectedDate.setTimeInMillis(savedInstanceState.getLong(
                    STATE_SELECTED_DATE,
                    initial.getTimeInMillis()
            ));
            displayedMonth.setTimeInMillis(savedInstanceState.getLong(
                    STATE_DISPLAYED_MONTH,
                    initial.getTimeInMillis()
            ));
            selectedHour = savedInstanceState.getInt(
                    STATE_SELECTED_HOUR,
                    initial.get(Calendar.HOUR_OF_DAY)
            );
            selectedMinute = savedInstanceState.getInt(
                    STATE_SELECTED_MINUTE,
                    initial.get(Calendar.MINUTE)
            );
        } else {
            selectedDate.setTimeInMillis(initial.getTimeInMillis());
            displayedMonth.setTimeInMillis(initial.getTimeInMillis());
            selectedHour = initial.get(Calendar.HOUR_OF_DAY);
            selectedMinute = initial.get(Calendar.MINUTE);
        }
        displayedMonth.set(Calendar.DAY_OF_MONTH, 1);
    }

    private void bindActions() {
        findViewById(R.id.buttonBookingAppointmentBack).setOnClickListener(view -> finish());
        buttonPreviousMonth.setOnClickListener(view -> changeMonth(-1));
        findViewById(R.id.buttonBookingAppointmentNextMonth)
                .setOnClickListener(view -> changeMonth(1));
        findViewById(R.id.buttonBookingAppointmentCustomTime)
                .setOnClickListener(view -> openCustomTimePicker());
        findViewById(R.id.buttonBookingAppointmentComplete)
                .setOnClickListener(view -> returnSelection());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.figma_mvp_background));
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.figma_mvp_background));
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightNavigationBars(true);

        ScrollView content = findViewById(R.id.scrollBookingAppointmentSelector);
        View topBar = findViewById(R.id.layoutBookingAppointmentTopBar);
        View bottomAction = findViewById(R.id.layoutBookingAppointmentBottomAction);
        applyInsets(content, topBar, bottomAction);
    }

    private void applyInsets(ScrollView content, View topBar, View bottomAction) {
        int contentTop = content.getPaddingTop();
        int contentBottom = content.getPaddingBottom();
        int topPaddingTop = topBar.getPaddingTop();
        int bottomPaddingBottom = bottomAction.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets systemInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    systemInsets.left,
                    contentTop + systemInsets.top,
                    systemInsets.right,
                    contentBottom + systemInsets.bottom
            );
            return windowInsets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(topBar, (view, windowInsets) -> {
            Insets topInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(topInsets.left, topPaddingTop + topInsets.top, topInsets.right, 0);
            return windowInsets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(bottomAction, (view, windowInsets) -> {
            Insets bottomInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    bottomInsets.left + dp(24),
                    dp(16),
                    bottomInsets.right + dp(24),
                    bottomPaddingBottom + bottomInsets.bottom
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(content);
        ViewCompat.requestApplyInsets(topBar);
        ViewCompat.requestApplyInsets(bottomAction);
    }

    private void changeMonth(int offset) {
        displayedMonth.add(Calendar.MONTH, offset);
        displayedMonth.set(Calendar.DAY_OF_MONTH, 1);
        renderCalendar();
    }

    private void render() {
        renderCalendar();
        renderTimeSlots();
        renderSelectedTime();
    }

    private void renderCalendar() {
        textDisplayedMonth.setText(getString(
                R.string.booking_appointment_schedule_month,
                displayedMonth.get(Calendar.YEAR),
                displayedMonth.get(Calendar.MONTH) + 1
        ));
        calendarGrid.removeAllViews();

        String[] weekdayLabels = getResources().getStringArray(
                R.array.booking_appointment_schedule_weekdays
        );
        for (int column = 0; column < weekdayLabels.length; column++) {
            TextView header = createCalendarTextView(weekdayLabels[column], 10);
            header.setTextColor(ContextCompat.getColor(this, R.color.figma_mvp_text_tertiary));
            header.setTypeface(ResourcesCompat.getFont(this, R.font.pretendard_bold));
            calendarGrid.addView(header, calendarCellParams(column, 0, 28));
        }

        Calendar monthStart = (Calendar) displayedMonth.clone();
        monthStart.set(Calendar.DAY_OF_MONTH, 1);
        int firstColumn = monthStart.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        int maximumDay = monthStart.getActualMaximum(Calendar.DAY_OF_MONTH);
        int rowCount = (int) Math.ceil((firstColumn + maximumDay) / 7.0);
        int cellCount = rowCount * 7;

        for (int index = 0; index < cellCount; index++) {
            Calendar cellDate = (Calendar) monthStart.clone();
            cellDate.add(Calendar.DAY_OF_MONTH, index - firstColumn);
            boolean inDisplayedMonth = cellDate.get(Calendar.MONTH) == displayedMonth.get(Calendar.MONTH)
                    && cellDate.get(Calendar.YEAR) == displayedMonth.get(Calendar.YEAR);
            boolean selected = isSameDate(cellDate, selectedDate);
            boolean selectable = inDisplayedMonth
                    && !BookingAppointmentDateTime.isBeforeToday(cellDate, System.currentTimeMillis());

            TextView dayView = createCalendarTextView(
                    String.valueOf(cellDate.get(Calendar.DAY_OF_MONTH)),
                    14
            );
            bindCalendarDayStyle(dayView, cellDate, inDisplayedMonth, selected, selectable);
            if (selectable) {
                dayView.setOnClickListener(view -> {
                    selectedDate.setTimeInMillis(cellDate.getTimeInMillis());
                    renderCalendar();
                });
            }
            calendarGrid.addView(dayView, calendarCellParams(index % 7, 1 + index / 7, 44));
        }

        Calendar currentMonth = BookingAppointmentDateTime.newCalendar();
        currentMonth.set(Calendar.DAY_OF_MONTH, 1);
        boolean canMovePrevious = displayedMonth.after(currentMonth);
        buttonPreviousMonth.setEnabled(canMovePrevious);
        buttonPreviousMonth.setAlpha(canMovePrevious ? 1f : 0.3f);
    }

    private void bindCalendarDayStyle(
            TextView dayView,
            Calendar cellDate,
            boolean inDisplayedMonth,
            boolean selected,
            boolean selectable
    ) {
        int textColor;
        if (selected) {
            dayView.setBackgroundResource(R.drawable.bg_figma_booking_calendar_selected);
            textColor = R.color.figma_mvp_primary;
            dayView.setTypeface(ResourcesCompat.getFont(this, R.font.pretendard_bold));
        } else if (!inDisplayedMonth || !selectable) {
            textColor = R.color.figma_mvp_text_disabled;
            dayView.setTypeface(ResourcesCompat.getFont(this, R.font.pretendard_medium));
        } else if (cellDate.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            textColor = R.color.figma_booking_schedule_sunday;
            dayView.setTypeface(ResourcesCompat.getFont(this, R.font.pretendard_semibold));
        } else {
            textColor = R.color.figma_mvp_text_primary;
            dayView.setTypeface(ResourcesCompat.getFont(this, R.font.pretendard_semibold));
        }
        dayView.setTextColor(ContextCompat.getColor(this, textColor));

        String dateDescription = getString(
                R.string.booking_appointment_schedule_date_description,
                cellDate.get(Calendar.YEAR),
                cellDate.get(Calendar.MONTH) + 1,
                cellDate.get(Calendar.DAY_OF_MONTH)
        );
        dayView.setContentDescription(selected
                ? getString(R.string.booking_appointment_schedule_date_selected_description, dateDescription)
                : dateDescription);
        dayView.setEnabled(selectable);
    }

    private TextView createCalendarTextView(String text, int textSizeSp) {
        TextView textView = new TextView(this);
        textView.setGravity(Gravity.CENTER);
        textView.setText(text);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        textView.setIncludeFontPadding(false);
        return textView;
    }

    private GridLayout.LayoutParams calendarCellParams(int column, int row, int heightDp) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(row),
                GridLayout.spec(column, 1f)
        );
        params.width = 0;
        params.height = dp(heightDp);
        return params;
    }

    private void renderTimeSlots() {
        timeGrid.removeAllViews();
        for (int index = 0; index < TIME_SLOTS.length; index++) {
            int hour = TIME_SLOTS[index][0];
            int minute = TIME_SLOTS[index][1];
            MaterialButton button = createTimeButton(hour, minute);
            timeGrid.addView(button, timeCellParams(index));
        }
    }

    private MaterialButton createTimeButton(int hour, int minute) {
        boolean selected = selectedHour == hour && selectedMinute == minute;
        MaterialButton button = new MaterialButton(this);
        button.setText(getString(R.string.booking_appointment_schedule_time_value, hour, minute));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        button.setAllCaps(false);
        button.setTypeface(ResourcesCompat.getFont(
                this,
                selected ? R.font.pretendard_bold : R.font.pretendard_semibold
        ));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(0, 0, 0, 0);
        button.setCornerRadius(dp(16));
        button.setStrokeWidth(selected ? 0 : dp(1));
        button.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(
                this,
                R.color.figma_booking_schedule_slot_outline
        )));
        button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(
                this,
                selected ? R.color.figma_mvp_primary : R.color.figma_mvp_surface
        )));
        button.setTextColor(ContextCompat.getColor(
                this,
                selected ? R.color.white : R.color.figma_mvp_text_primary
        ));
        button.setElevation(selected ? dp(4) : 0);
        button.setOnClickListener(view -> {
            selectedHour = hour;
            selectedMinute = minute;
            renderTimeSlots();
            renderSelectedTime();
        });
        return button;
    }

    private GridLayout.LayoutParams timeCellParams(int index) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(index / 3),
                GridLayout.spec(index % 3, 1f)
        );
        int margin = dp(6);
        params.width = 0;
        params.height = dp(50);
        params.setMargins(margin, margin, margin, margin);
        return params;
    }

    private void openCustomTimePicker() {
        MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(this)
                        ? TimeFormat.CLOCK_24H
                        : TimeFormat.CLOCK_12H)
                .setHour(selectedHour)
                .setMinute(selectedMinute)
                .setTitleText(R.string.booking_time_picker_title)
                .build();
        timePicker.addOnPositiveButtonClickListener(view -> {
            selectedHour = timePicker.getHour();
            selectedMinute = timePicker.getMinute();
            renderTimeSlots();
            renderSelectedTime();
        });
        timePicker.show(getSupportFragmentManager(), "bookingAppointmentCustomTimePicker");
    }

    private void renderSelectedTime() {
        textSelectedTime.setText(getString(
                R.string.booking_appointment_schedule_selected_time,
                selectedHour,
                selectedMinute
        ));
    }

    private void returnSelection() {
        Intent result = new Intent();
        result.putExtra(
                EXTRA_SELECTED_APPOINTMENT_AT,
                BookingAppointmentDateTime.format(selectedDate, selectedHour, selectedMinute)
        );
        setResult(RESULT_OK, result);
        finish();
    }

    private boolean isSameDate(Calendar first, Calendar second) {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
