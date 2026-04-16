package com.example.bodeul.ui.booking;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.util.StatePanelHelper;
import com.example.bodeul.util.UserProfileSanitizer;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * 환자와 보호자가 병원 동행을 신청하고 현재 요청 상태를 확인하는 화면이다.
 */
public class BookingActivity extends AppCompatActivity {
    private static final Pattern APPOINTMENT_AT_PATTERN =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$");
    private static final String SEOUL_TIME_ZONE = "Asia/Seoul";

    private AuthRepository authRepository;
    private BookingRepository bookingRepository;
    private User currentUser;
    private AppointmentRequest editingRequest;
    private boolean loading;

    private TextView textBookingMode;
    private TextView textBookingRequesterName;
    private TextView textBookingRequesterRole;
    private TextView textBookingRequesterPhone;
    private TextView textBookingLinkedSection;
    private TextView textBookingLinkedHelper;
    private TextView textBookingLatestStatus;
    private TextView textBookingLatestTitle;
    private TextView textBookingLatestBody;
    private TextView textBookingFormTitle;
    private TextView textBookingFormBadge;
    private TextView textBookingFormHelper;
    private View bookingStatePanel;
    private View bookingContentContainer;
    private LinearLayout bookingRequestsContainer;
    private TextInputLayout layoutBookingLinkedName;
    private TextInputLayout layoutBookingLinkedPhone;
    private TextInputLayout layoutBookingLinkedEmail;
    private TextInputLayout layoutHospitalName;
    private TextInputLayout layoutDepartmentName;
    private TextInputLayout layoutAppointmentAt;
    private TextInputLayout layoutMeetingPlace;
    private TextInputLayout layoutSpecialNotes;
    private TextInputEditText inputBookingLinkedName;
    private TextInputEditText inputBookingLinkedPhone;
    private TextInputEditText inputBookingLinkedEmail;
    private TextInputEditText inputHospitalName;
    private TextInputEditText inputDepartmentName;
    private TextInputEditText inputAppointmentAt;
    private TextInputEditText inputMeetingPlace;
    private TextInputEditText inputSpecialNotes;
    private MaterialButton buttonBookingQuickToday;
    private MaterialButton buttonBookingQuickTomorrow;
    private MaterialButton buttonBookingQuickDayAfterTomorrow;
    private MaterialButton buttonBookingQuickMorning;
    private MaterialButton buttonBookingQuickAfternoon;
    private MaterialButton buttonBookingQuickLateAfternoon;
    private MaterialButton buttonSubmitBooking;
    private MaterialButton buttonCancelBookingEdit;
    private ProgressBar progressBooking;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking);

        authRepository = ServiceLocator.provideAuthRepository(this);
        bookingRepository = ServiceLocator.provideBookingRepository(this);

        textBookingMode = findViewById(R.id.textBookingMode);
        textBookingRequesterName = findViewById(R.id.textBookingRequesterName);
        textBookingRequesterRole = findViewById(R.id.textBookingRequesterRole);
        textBookingRequesterPhone = findViewById(R.id.textBookingRequesterPhone);
        textBookingLinkedSection = findViewById(R.id.textBookingLinkedSection);
        textBookingLinkedHelper = findViewById(R.id.textBookingLinkedHelper);
        textBookingLatestStatus = findViewById(R.id.textBookingLatestStatus);
        textBookingLatestTitle = findViewById(R.id.textBookingLatestTitle);
        textBookingLatestBody = findViewById(R.id.textBookingLatestBody);
        textBookingFormTitle = findViewById(R.id.textBookingFormTitle);
        textBookingFormBadge = findViewById(R.id.textBookingFormBadge);
        textBookingFormHelper = findViewById(R.id.textBookingFormHelper);
        bookingStatePanel = findViewById(R.id.bookingStatePanel);
        bookingContentContainer = findViewById(R.id.bookingContentContainer);
        bookingRequestsContainer = findViewById(R.id.bookingRequestsContainer);
        layoutBookingLinkedName = findViewById(R.id.layoutBookingLinkedName);
        layoutBookingLinkedPhone = findViewById(R.id.layoutBookingLinkedPhone);
        layoutBookingLinkedEmail = findViewById(R.id.layoutBookingLinkedEmail);
        layoutHospitalName = findViewById(R.id.layoutBookingHospitalName);
        layoutDepartmentName = findViewById(R.id.layoutBookingDepartmentName);
        layoutAppointmentAt = findViewById(R.id.layoutBookingAppointmentAt);
        layoutMeetingPlace = findViewById(R.id.layoutBookingMeetingPlace);
        layoutSpecialNotes = findViewById(R.id.layoutBookingSpecialNotes);
        inputBookingLinkedName = findViewById(R.id.inputBookingLinkedName);
        inputBookingLinkedPhone = findViewById(R.id.inputBookingLinkedPhone);
        inputBookingLinkedEmail = findViewById(R.id.inputBookingLinkedEmail);
        inputHospitalName = findViewById(R.id.inputBookingHospitalName);
        inputDepartmentName = findViewById(R.id.inputBookingDepartmentName);
        inputAppointmentAt = findViewById(R.id.inputBookingAppointmentAt);
        inputMeetingPlace = findViewById(R.id.inputBookingMeetingPlace);
        inputSpecialNotes = findViewById(R.id.inputBookingSpecialNotes);
        buttonBookingQuickToday = findViewById(R.id.buttonBookingQuickToday);
        buttonBookingQuickTomorrow = findViewById(R.id.buttonBookingQuickTomorrow);
        buttonBookingQuickDayAfterTomorrow = findViewById(R.id.buttonBookingQuickDayAfterTomorrow);
        buttonBookingQuickMorning = findViewById(R.id.buttonBookingQuickMorning);
        buttonBookingQuickAfternoon = findViewById(R.id.buttonBookingQuickAfternoon);
        buttonBookingQuickLateAfternoon = findViewById(R.id.buttonBookingQuickLateAfternoon);
        buttonSubmitBooking = findViewById(R.id.buttonSubmitBooking);
        buttonCancelBookingEdit = findViewById(R.id.buttonCancelBookingEdit);
        progressBooking = findViewById(R.id.progressBooking);

        textBookingMode.setText(bookingRepository.isFirebaseBacked()
                ? R.string.booking_mode_firebase
                : R.string.booking_mode_demo);

        findViewById(R.id.buttonBackBooking).setOnClickListener(view -> finish());
        buttonSubmitBooking.setOnClickListener(view -> submitAppointmentRequest());
        buttonCancelBookingEdit.setOnClickListener(view -> exitEditMode());
        configureAppointmentPicker();
        configureQuickAppointmentButtons();

        bindEmptySummary();
        renderEmptyRequests();
        updateFormMode();
    }

    @Override
    protected void onStart() {
        super.onStart();
        setLoading(true);
        hideBlockingState();
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    openProfileCompletion();
                    return;
                }
                if (!supportsBookingRole(result.getRole())) {
                    setLoading(false);
                    showPermissionState();
                    return;
                }

                currentUser = result;
                hideBlockingState();
                bindRequester(result);
                loadRequests();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void bindRequester(User user) {
        // 신청 주체와 현재 등록된 연락처를 화면 상단에 고정해 입력 실수를 줄인다.
        textBookingRequesterName.setText(getString(R.string.booking_requester_name, user.getName()));
        textBookingRequesterRole.setText(getString(
                R.string.booking_requester_role,
                toRoleLabel(user.getRole())
        ));
        textBookingRequesterPhone.setText(getString(
                R.string.booking_requester_phone,
                TextUtils.isEmpty(user.getPhone())
                        ? getString(R.string.booking_requester_phone_empty)
                        : user.getPhone()
        ));
        bindLinkedParticipantSection(user);
    }

    private void bindLinkedParticipantSection(User user) {
        String counterpartRole = toCounterpartRoleLabel(user.getRole());
        textBookingLinkedSection.setText(getString(R.string.booking_linked_section_format, counterpartRole));
        textBookingLinkedHelper.setText(getString(R.string.booking_linked_helper_format, counterpartRole));
        layoutBookingLinkedName.setHint(getString(R.string.booking_linked_name_hint_format, counterpartRole));
        layoutBookingLinkedPhone.setHint(getString(R.string.booking_linked_phone_hint_format, counterpartRole));
        layoutBookingLinkedEmail.setHint(getString(R.string.booking_linked_email_hint_format, counterpartRole));
    }

    private void configureAppointmentPicker() {
        inputAppointmentAt.setOnClickListener(view -> openAppointmentDatePicker());
        layoutAppointmentAt.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        layoutAppointmentAt.setEndIconDrawable(android.R.drawable.ic_menu_my_calendar);
        layoutAppointmentAt.setEndIconOnClickListener(view -> openAppointmentDatePicker());
        layoutAppointmentAt.setOnClickListener(view -> openAppointmentDatePicker());
    }

    private void configureQuickAppointmentButtons() {
        buttonBookingQuickToday.setOnClickListener(view -> applyQuickAppointmentDate(0));
        buttonBookingQuickTomorrow.setOnClickListener(view -> applyQuickAppointmentDate(1));
        buttonBookingQuickDayAfterTomorrow.setOnClickListener(view -> applyQuickAppointmentDate(2));
        buttonBookingQuickMorning.setOnClickListener(view -> applyQuickAppointmentTime(10, 0));
        buttonBookingQuickAfternoon.setOnClickListener(view -> applyQuickAppointmentTime(14, 0));
        buttonBookingQuickLateAfternoon.setOnClickListener(view -> applyQuickAppointmentTime(16, 0));
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
                openAppointmentTimePicker(selection == null ? MaterialDatePicker.todayInUtcMilliseconds() : selection));
        datePicker.show(getSupportFragmentManager(), "bookingAppointmentDatePicker");
    }

    private void openAppointmentTimePicker(long selectedDateUtcMillis) {
        Calendar initialCalendar = resolveInitialTimeCalendar(selectedDateUtcMillis);
        MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(this) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(initialCalendar.get(Calendar.HOUR_OF_DAY))
                .setMinute(initialCalendar.get(Calendar.MINUTE))
                .setTitleText(R.string.booking_time_picker_title)
                .build();

        timePicker.addOnPositiveButtonClickListener(view -> applyAppointmentDateTime(
                selectedDateUtcMillis,
                timePicker.getHour(),
                timePicker.getMinute()
        ));
        timePicker.show(getSupportFragmentManager(), "bookingAppointmentTimePicker");
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
        Calendar todayCalendar = Calendar.getInstance(TimeZone.getTimeZone(SEOUL_TIME_ZONE), Locale.KOREA);
        todayCalendar.add(Calendar.DAY_OF_MONTH, dayOffset);

        baseCalendar.set(Calendar.YEAR, todayCalendar.get(Calendar.YEAR));
        baseCalendar.set(Calendar.MONTH, todayCalendar.get(Calendar.MONTH));
        baseCalendar.set(Calendar.DAY_OF_MONTH, todayCalendar.get(Calendar.DAY_OF_MONTH));
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
        Calendar selectedCalendar = parseAppointmentCalendar(valueOf(inputAppointmentAt));
        int selectedDateOffset = resolveSelectedDateOffset(selectedCalendar);
        bindQuickButtonStyle(buttonBookingQuickToday, selectedDateOffset == 0);
        bindQuickButtonStyle(buttonBookingQuickTomorrow, selectedDateOffset == 1);
        bindQuickButtonStyle(buttonBookingQuickDayAfterTomorrow, selectedDateOffset == 2);

        boolean hasSelectedTime = selectedCalendar != null;
        bindQuickButtonStyle(
                buttonBookingQuickMorning,
                hasSelectedTime
                        && selectedCalendar.get(Calendar.HOUR_OF_DAY) == 10
                        && selectedCalendar.get(Calendar.MINUTE) == 0
        );
        bindQuickButtonStyle(
                buttonBookingQuickAfternoon,
                hasSelectedTime
                        && selectedCalendar.get(Calendar.HOUR_OF_DAY) == 14
                        && selectedCalendar.get(Calendar.MINUTE) == 0
        );
        bindQuickButtonStyle(
                buttonBookingQuickLateAfternoon,
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
            button.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.bodeul_primary)));
            button.setStrokeColor(ColorStateList.valueOf(getColor(R.color.bodeul_primary)));
            button.setTextColor(getColor(R.color.white));
            return;
        }
        button.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.white)));
        button.setStrokeColor(ColorStateList.valueOf(getColor(R.color.bodeul_outline)));
        button.setTextColor(getColor(R.color.bodeul_primary));
    }

    private Calendar resolveBaseAppointmentCalendar() {
        Calendar parsedCalendar = parseAppointmentCalendar(valueOf(inputAppointmentAt));
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
        Calendar parsedCalendar = parseAppointmentCalendar(valueOf(inputAppointmentAt));
        if (parsedCalendar == null) {
            return null;
        }

        Calendar utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.KOREA);
        utcCalendar.clear();
        utcCalendar.set(
                parsedCalendar.get(Calendar.YEAR),
                parsedCalendar.get(Calendar.MONTH),
                parsedCalendar.get(Calendar.DAY_OF_MONTH)
        );
        return utcCalendar.getTimeInMillis();
    }

    private Calendar resolveInitialTimeCalendar(long selectedDateUtcMillis) {
        Calendar parsedCalendar = parseAppointmentCalendar(valueOf(inputAppointmentAt));
        if (parsedCalendar != null) {
            return parsedCalendar;
        }

        Calendar utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.KOREA);
        utcCalendar.setTimeInMillis(selectedDateUtcMillis);
        Calendar seoulCalendar = Calendar.getInstance(TimeZone.getTimeZone(SEOUL_TIME_ZONE), Locale.KOREA);
        seoulCalendar.set(Calendar.YEAR, utcCalendar.get(Calendar.YEAR));
        seoulCalendar.set(Calendar.MONTH, utcCalendar.get(Calendar.MONTH));
        seoulCalendar.set(Calendar.DAY_OF_MONTH, utcCalendar.get(Calendar.DAY_OF_MONTH));
        seoulCalendar.set(Calendar.SECOND, 0);
        seoulCalendar.set(Calendar.MILLISECOND, 0);
        return seoulCalendar;
    }

    @Nullable
    private Calendar parseAppointmentCalendar(String appointmentAt) {
        if (TextUtils.isEmpty(appointmentAt)) {
            return null;
        }

        SimpleDateFormat formatter = createAppointmentFormatter();
        try {
            java.util.Date parsedDate = formatter.parse(appointmentAt);
            if (parsedDate == null) {
                return null;
            }
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(SEOUL_TIME_ZONE), Locale.KOREA);
            calendar.setTime(parsedDate);
            return calendar;
        } catch (ParseException exception) {
            return null;
        }
    }

    private String formatAppointmentAt(long appointmentAtMillis) {
        return createAppointmentFormatter().format(appointmentAtMillis);
    }

    private SimpleDateFormat createAppointmentFormatter() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
        formatter.setLenient(false);
        formatter.setTimeZone(TimeZone.getTimeZone(SEOUL_TIME_ZONE));
        return formatter;
    }

    private void loadRequests() {
        if (currentUser == null) {
            setLoading(false);
            return;
        }

        bookingRepository.getMyAppointmentRequests(currentUser, new RepositoryCallback<List<AppointmentRequest>>() {
            @Override
            public void onSuccess(List<AppointmentRequest> result) {
                setLoading(false);
                hideBlockingState();
                bindLatestRequest(result.isEmpty() ? null : result.get(0), result.size());
                renderRequests(result);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                bindEmptySummary();
                renderEmptyRequests();
                showLoadErrorState(message);
            }
        });
    }

    private void submitAppointmentRequest() {
        if (currentUser == null || loading) {
            return;
        }

        clearErrors();
        String linkedParticipantName = valueOf(inputBookingLinkedName);
        String linkedParticipantPhone = valueOf(inputBookingLinkedPhone);
        String linkedParticipantEmail = valueOf(inputBookingLinkedEmail);
        String hospitalName = valueOf(inputHospitalName);
        String departmentName = valueOf(inputDepartmentName);
        String appointmentAt = valueOf(inputAppointmentAt);
        String meetingPlace = valueOf(inputMeetingPlace);
        String specialNotes = valueOf(inputSpecialNotes);
        if (!validateRequired(layoutBookingLinkedName, linkedParticipantName)
                || !validateRequired(layoutBookingLinkedPhone, linkedParticipantPhone)
                || !validateLinkedPhone(linkedParticipantPhone)
                || !validateLinkedEmail(linkedParticipantEmail)
                || !validateRequired(layoutHospitalName, hospitalName)
                || !validateRequired(layoutDepartmentName, departmentName)
                || !validateRequired(layoutAppointmentAt, appointmentAt)
                || !validateRequired(layoutMeetingPlace, meetingPlace)
                || !validateAppointmentAtFormat(appointmentAt)) {
            return;
        }

        setLoading(true);
        if (editingRequest == null) {
            bookingRepository.createAppointmentRequest(
                    currentUser,
                    hospitalName,
                    departmentName,
                    appointmentAt,
                    meetingPlace,
                    specialNotes,
                    linkedParticipantName,
                    linkedParticipantPhone,
                    linkedParticipantEmail,
                    new RepositoryCallback<AppointmentRequest>() {
                        @Override
                        public void onSuccess(AppointmentRequest result) {
                            Toast.makeText(
                                    BookingActivity.this,
                                    R.string.toast_booking_submitted,
                                    Toast.LENGTH_SHORT
                            ).show();
                            clearFormFields();
                            loadRequests();
                        }

                        @Override
                        public void onError(String message) {
                            setLoading(false);
                            Toast.makeText(BookingActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    }
            );
            return;
        }

        bookingRepository.updateAppointmentRequest(
                currentUser,
                editingRequest.getId(),
                hospitalName,
                departmentName,
                appointmentAt,
                meetingPlace,
                specialNotes,
                linkedParticipantName,
                linkedParticipantPhone,
                linkedParticipantEmail,
                new RepositoryCallback<AppointmentRequest>() {
                    @Override
                    public void onSuccess(AppointmentRequest result) {
                        Toast.makeText(
                                BookingActivity.this,
                                R.string.toast_booking_updated,
                                Toast.LENGTH_SHORT
                        ).show();
                        exitEditMode();
                        loadRequests();
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        Toast.makeText(BookingActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void startEditingRequest(AppointmentRequest request) {
        if (!canEditRequest(request) || currentUser == null || loading) {
            Toast.makeText(this, R.string.toast_booking_request_locked, Toast.LENGTH_SHORT).show();
            return;
        }

        editingRequest = request;
        boolean requesterIsGuardian = currentUser.getRole() == UserRole.GUARDIAN;
        inputBookingLinkedName.setText(requesterIsGuardian ? request.getPatientName() : request.getGuardianName());
        inputBookingLinkedPhone.setText(requesterIsGuardian ? request.getPatientPhone() : request.getGuardianPhone());
        inputBookingLinkedEmail.setText(requesterIsGuardian ? request.getPatientEmail() : request.getGuardianEmail());
        inputHospitalName.setText(request.getHospitalName());
        inputDepartmentName.setText(request.getDepartmentName());
        inputAppointmentAt.setText(request.getAppointmentAt());
        inputMeetingPlace.setText(request.getMeetingPlace());
        inputSpecialNotes.setText(request.getSpecialNotes());
        clearErrors();
        refreshQuickAppointmentButtons();
        updateFormMode();
    }

    private void cancelRequest(AppointmentRequest request) {
        if (!canCancelRequest(request) || currentUser == null || loading) {
            Toast.makeText(this, R.string.toast_booking_request_cancel_locked, Toast.LENGTH_SHORT).show();
            return;
        }

        int messageResId = request.getStatus() == AppointmentStatus.MATCHED
                ? R.string.booking_cancel_dialog_body_matched
                : R.string.booking_cancel_dialog_body_requested;
        new AlertDialog.Builder(this)
                .setTitle(R.string.booking_cancel_dialog_title)
                .setMessage(messageResId)
                .setNegativeButton(R.string.booking_cancel_dialog_keep, null)
                .setPositiveButton(R.string.booking_cancel_dialog_confirm, (dialogInterface, which) -> performCancelRequest(request))
                .show();
    }

    private void performCancelRequest(AppointmentRequest request) {
        setLoading(true);
        bookingRepository.cancelAppointmentRequest(
                currentUser,
                request.getId(),
                new RepositoryCallback<AppointmentRequest>() {
                    @Override
                    public void onSuccess(AppointmentRequest result) {
                        if (editingRequest != null && editingRequest.getId().equals(result.getId())) {
                            exitEditMode();
                        }
                        Toast.makeText(
                                BookingActivity.this,
                                R.string.toast_booking_canceled,
                                Toast.LENGTH_SHORT
                        ).show();
                        loadRequests();
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        Toast.makeText(BookingActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private boolean canEditRequest(AppointmentRequest request) {
        return request.getStatus() == AppointmentStatus.REQUESTED;
    }

    private boolean canCancelRequest(AppointmentRequest request) {
        return request.getStatus() == AppointmentStatus.REQUESTED
                || request.getStatus() == AppointmentStatus.MATCHED;
    }

    private void updateFormMode() {
        if (editingRequest == null) {
            textBookingFormTitle.setText(R.string.booking_form_section);
            textBookingFormBadge.setText(R.string.booking_form_badge);
            textBookingFormHelper.setText(R.string.booking_form_helper);
            buttonSubmitBooking.setText(R.string.booking_submit_button);
            buttonCancelBookingEdit.setVisibility(View.GONE);
            return;
        }

        textBookingFormTitle.setText(R.string.booking_form_edit_section);
        textBookingFormBadge.setText(R.string.booking_form_edit_badge);
        textBookingFormHelper.setText(getString(
                R.string.booking_form_edit_helper,
                editingRequest.getHospitalName(),
                editingRequest.getDepartmentName()
        ));
        buttonSubmitBooking.setText(R.string.booking_submit_update_button);
        buttonCancelBookingEdit.setVisibility(View.VISIBLE);
    }

    private void exitEditMode() {
        editingRequest = null;
        clearFormFields();
        clearErrors();
        updateFormMode();
    }

    private void bindLatestRequest(@Nullable AppointmentRequest request, int requestCount) {
        if (request == null) {
            bindEmptySummary();
            return;
        }

        bindStatusBadge(textBookingLatestStatus, request.getStatus());
        textBookingLatestTitle.setText(getString(
                R.string.booking_latest_title,
                request.getHospitalName(),
                request.getDepartmentName()
        ));
        textBookingLatestBody.setText(getString(
                R.string.booking_latest_body,
                requestCount,
                toStatusLabel(request.getStatus()),
                request.getAppointmentAt(),
                request.getMeetingPlace()
        ));
    }

    private void bindEmptySummary() {
        textBookingLatestStatus.setText(R.string.booking_empty_badge);
        textBookingLatestStatus.setTextColor(getColor(R.color.bodeul_text_secondary));
        textBookingLatestStatus.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.bodeul_surface_alt)));
        textBookingLatestTitle.setText(R.string.booking_empty_title);
        textBookingLatestBody.setText(R.string.booking_empty_body);
    }

    private void renderRequests(List<AppointmentRequest> requests) {
        bookingRequestsContainer.removeAllViews();
        if (requests.isEmpty()) {
            renderEmptyRequests();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (AppointmentRequest request : requests) {
            View requestView = inflater.inflate(R.layout.item_booking_request, bookingRequestsContainer, false);
            MaterialCardView cardView = (MaterialCardView) requestView;
            TextView statusView = requestView.findViewById(R.id.textBookingRequestStatus);
            TextView titleView = requestView.findViewById(R.id.textBookingRequestTitle);
            TextView detailView = requestView.findViewById(R.id.textBookingRequestDetail);
            TextView placeView = requestView.findViewById(R.id.textBookingRequestPlace);
            TextView linkedView = requestView.findViewById(R.id.textBookingRequestLinked);
            TextView noteView = requestView.findViewById(R.id.textBookingRequestNote);
            View actionsView = requestView.findViewById(R.id.layoutBookingRequestActions);
            MaterialButton editButton = requestView.findViewById(R.id.buttonBookingRequestEdit);
            MaterialButton cancelButton = requestView.findViewById(R.id.buttonBookingRequestCancel);

            bindStatusBadge(statusView, request.getStatus());
            titleView.setText(getString(
                    R.string.booking_request_title,
                    request.getHospitalName(),
                    request.getDepartmentName()
            ));
            detailView.setText(getString(
                    R.string.booking_request_detail,
                    request.getAppointmentAt()
            ));
            placeView.setText(getString(
                    R.string.booking_request_place,
                    request.getMeetingPlace()
            ));
            linkedView.setText(getString(
                    R.string.booking_request_linked,
                    toCounterpartRoleLabel(currentUser == null ? UserRole.PATIENT : currentUser.getRole()),
                    buildLinkedParticipantLine(request)
            ));

            if (TextUtils.isEmpty(request.getSpecialNotes())) {
                noteView.setVisibility(View.GONE);
            } else {
                noteView.setVisibility(View.VISIBLE);
                noteView.setText(getString(
                        R.string.booking_request_note,
                        request.getSpecialNotes()
                ));
            }

            cardView.setStrokeColor(getColor(resolveStatusStrokeColor(request.getStatus())));
            boolean editable = canEditRequest(request);
            boolean cancelable = canCancelRequest(request);
            if (editable || cancelable) {
                actionsView.setVisibility(View.VISIBLE);
                editButton.setVisibility(editable ? View.VISIBLE : View.GONE);
                cancelButton.setVisibility(cancelable ? View.VISIBLE : View.GONE);
                updateRequestActionMargins(editButton, cancelButton, editable, cancelable);
                if (editable) {
                    editButton.setOnClickListener(view -> startEditingRequest(request));
                } else {
                    editButton.setOnClickListener(null);
                }
                if (cancelable) {
                    cancelButton.setOnClickListener(view -> cancelRequest(request));
                } else {
                    cancelButton.setOnClickListener(null);
                }
            } else {
                actionsView.setVisibility(View.GONE);
            }
            bookingRequestsContainer.addView(requestView);
        }
    }

    private void updateRequestActionMargins(
            MaterialButton editButton,
            MaterialButton cancelButton,
            boolean editable,
            boolean cancelable
    ) {
        int splitMargin = dpToPx(8);
        updateActionButtonMargin(editButton, 0, editable && cancelable ? splitMargin : 0);
        updateActionButtonMargin(cancelButton, editable && cancelable ? splitMargin : 0, 0);
    }

    private void updateActionButtonMargin(MaterialButton button, int startMargin, int endMargin) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) button.getLayoutParams();
        params.setMarginStart(startMargin);
        params.setMarginEnd(endMargin);
        button.setLayoutParams(params);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void renderEmptyRequests() {
        bookingRequestsContainer.removeAllViews();
        View emptyPanel = LayoutInflater.from(this).inflate(
                R.layout.include_state_panel,
                bookingRequestsContainer,
                false
        );
        StatePanelHelper.show(
                emptyPanel,
                StatePanelHelper.Tone.INFO,
                getString(R.string.state_badge_notice),
                getString(R.string.booking_empty_title),
                getString(R.string.booking_requests_empty),
                null,
                null,
                null,
                null
        );
        bookingRequestsContainer.addView(emptyPanel);
    }

    private void bindStatusBadge(TextView textView, AppointmentStatus status) {
        int backgroundColor = resolveStatusBackgroundColor(status);
        int textColor = resolveStatusTextColor(status);
        textView.setText(toStatusLabel(status));
        textView.setBackgroundTintList(ColorStateList.valueOf(getColor(backgroundColor)));
        textView.setTextColor(getColor(textColor));
    }

    private int resolveStatusBackgroundColor(AppointmentStatus status) {
        switch (status) {
            case MATCHED:
                return R.color.bodeul_primary;
            case IN_PROGRESS:
                return R.color.bodeul_success;
            case COMPLETED:
                return R.color.bodeul_success;
            case CANCELED:
                return R.color.bodeul_surface_alt;
            case REQUESTED:
            default:
                return R.color.bodeul_warning;
        }
    }

    private int resolveStatusTextColor(AppointmentStatus status) {
        switch (status) {
            case REQUESTED:
            case CANCELED:
                return R.color.bodeul_text_primary;
            case MATCHED:
            case IN_PROGRESS:
            case COMPLETED:
            default:
                return R.color.white;
        }
    }

    private int resolveStatusStrokeColor(AppointmentStatus status) {
        switch (status) {
            case MATCHED:
                return R.color.bodeul_primary;
            case IN_PROGRESS:
            case COMPLETED:
                return R.color.bodeul_success;
            case CANCELED:
                return R.color.bodeul_outline;
            case REQUESTED:
            default:
                return R.color.bodeul_warning;
        }
    }

    private String toStatusLabel(AppointmentStatus status) {
        switch (status) {
            case MATCHED:
                return getString(R.string.booking_status_matched);
            case IN_PROGRESS:
                return getString(R.string.booking_status_in_progress);
            case COMPLETED:
                return getString(R.string.booking_status_completed);
            case CANCELED:
                return getString(R.string.booking_status_canceled);
            case REQUESTED:
            default:
                return getString(R.string.booking_status_requested);
        }
    }

    private String toRoleLabel(UserRole role) {
        if (role == UserRole.GUARDIAN) {
            return getString(R.string.login_role_guardian);
        }
        return getString(R.string.login_role_patient);
    }

    private String toCounterpartRoleLabel(UserRole requesterRole) {
        return requesterRole == UserRole.GUARDIAN
                ? getString(R.string.login_role_patient)
                : getString(R.string.login_role_guardian);
    }

    private String buildLinkedParticipantLine(AppointmentRequest request) {
        boolean requesterIsGuardian = currentUser != null && currentUser.getRole() == UserRole.GUARDIAN;
        String linkedName = requesterIsGuardian ? request.getPatientName() : request.getGuardianName();
        String linkedPhone = requesterIsGuardian ? request.getPatientPhone() : request.getGuardianPhone();
        String linkedEmail = requesterIsGuardian ? request.getPatientEmail() : request.getGuardianEmail();
        String linkedUserId = requesterIsGuardian ? request.getPatientUserId() : request.getGuardianUserId();

        String primaryValue;
        if (!TextUtils.isEmpty(linkedName) && !TextUtils.isEmpty(linkedPhone)) {
            primaryValue = getString(R.string.booking_linked_value_name_phone, linkedName, linkedPhone);
        } else if (!TextUtils.isEmpty(linkedName) && !TextUtils.isEmpty(linkedEmail)) {
            primaryValue = getString(R.string.booking_linked_value_name_phone, linkedName, linkedEmail);
        } else if (!TextUtils.isEmpty(linkedName)) {
            primaryValue = linkedName;
        } else if (!TextUtils.isEmpty(linkedPhone)) {
            primaryValue = linkedPhone;
        } else if (!TextUtils.isEmpty(linkedEmail)) {
            primaryValue = linkedEmail;
        } else {
            return getString(R.string.booking_linked_pending);
        }

        if (TextUtils.isEmpty(linkedUserId)) {
            return getString(R.string.booking_linked_pending_format, primaryValue);
        }
        return primaryValue;
    }

    private boolean supportsBookingRole(UserRole role) {
        return role == UserRole.PATIENT || role == UserRole.GUARDIAN;
    }

    private boolean validateRequired(TextInputLayout layout, String value) {
        if (TextUtils.isEmpty(value)) {
            layout.setError(getString(R.string.error_required_field));
            return false;
        }
        return true;
    }

    private boolean validateAppointmentAtFormat(String appointmentAt) {
        if (APPOINTMENT_AT_PATTERN.matcher(appointmentAt).matches()) {
            return true;
        }
        layoutAppointmentAt.setError(getString(R.string.error_booking_appointment_format));
        return false;
    }

    private boolean validateLinkedPhone(String phone) {
        if (UserProfileSanitizer.isValidPhone(phone)) {
            return true;
        }
        layoutBookingLinkedPhone.setError(getString(R.string.error_phone_invalid));
        return false;
    }

    private boolean validateLinkedEmail(String email) {
        if (TextUtils.isEmpty(email) || Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return true;
        }
        layoutBookingLinkedEmail.setError(getString(R.string.error_email_invalid));
        return false;
    }

    private void clearErrors() {
        layoutBookingLinkedName.setError(null);
        layoutBookingLinkedPhone.setError(null);
        layoutBookingLinkedEmail.setError(null);
        layoutHospitalName.setError(null);
        layoutDepartmentName.setError(null);
        layoutAppointmentAt.setError(null);
        layoutMeetingPlace.setError(null);
        layoutSpecialNotes.setError(null);
    }

    private void clearFormFields() {
        inputBookingLinkedName.setText(null);
        inputBookingLinkedPhone.setText(null);
        inputBookingLinkedEmail.setText(null);
        inputHospitalName.setText(null);
        inputDepartmentName.setText(null);
        inputAppointmentAt.setText(null);
        inputMeetingPlace.setText(null);
        inputSpecialNotes.setText(null);
        refreshQuickAppointmentButtons();
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        progressBooking.setVisibility(loading ? View.VISIBLE : View.GONE);
        buttonSubmitBooking.setEnabled(!loading);
        buttonCancelBookingEdit.setEnabled(!loading);
        inputBookingLinkedName.setEnabled(!loading);
        inputBookingLinkedPhone.setEnabled(!loading);
        inputBookingLinkedEmail.setEnabled(!loading);
        inputHospitalName.setEnabled(!loading);
        inputDepartmentName.setEnabled(!loading);
        inputAppointmentAt.setEnabled(!loading);
        inputMeetingPlace.setEnabled(!loading);
        inputSpecialNotes.setEnabled(!loading);
        buttonBookingQuickToday.setEnabled(!loading);
        buttonBookingQuickTomorrow.setEnabled(!loading);
        buttonBookingQuickDayAfterTomorrow.setEnabled(!loading);
        buttonBookingQuickMorning.setEnabled(!loading);
        buttonBookingQuickAfternoon.setEnabled(!loading);
        buttonBookingQuickLateAfternoon.setEnabled(!loading);
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.feature_booking_title)),
                getString(R.string.state_permission_body),
                getString(R.string.state_action_open_home),
                view -> openHome(),
                getString(R.string.state_action_open_login),
                view -> openRoleSelection()
        );
    }

    private void showAuthState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_auth),
                getString(R.string.state_auth_title),
                getString(R.string.state_auth_body),
                getString(R.string.state_action_open_login),
                view -> openRoleSelection(),
                null,
                null
        );
    }

    private void showLoadErrorState(String message) {
        String body = getString(R.string.state_load_error_body);
        if (!TextUtils.isEmpty(message)) {
            body = body + "\n\n" + message;
        }
        showBlockingState(
                StatePanelHelper.Tone.ERROR,
                getString(R.string.state_badge_error),
                getString(R.string.state_load_error_title, getString(R.string.feature_booking_title)),
                body,
                getString(R.string.state_action_retry),
                view -> {
                    if (currentUser == null) {
                        showAuthState();
                        return;
                    }
                    setLoading(true);
                    hideBlockingState();
                    loadRequests();
                },
                getString(R.string.state_action_open_home),
                view -> openHome()
        );
    }

    private void showBlockingState(
            StatePanelHelper.Tone tone,
            CharSequence badge,
            CharSequence title,
            CharSequence body,
            @Nullable CharSequence primaryText,
            @Nullable View.OnClickListener primaryListener,
            @Nullable CharSequence secondaryText,
            @Nullable View.OnClickListener secondaryListener
    ) {
        StatePanelHelper.show(
                bookingStatePanel,
                tone,
                badge,
                title,
                body,
                primaryText,
                primaryListener,
                secondaryText,
                secondaryListener
        );
        bookingContentContainer.setVisibility(View.GONE);
    }

    private void hideBlockingState() {
        StatePanelHelper.hide(bookingStatePanel);
        bookingContentContainer.setVisibility(View.VISIBLE);
    }

    private String valueOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private void openHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void openRoleSelection() {
        Intent intent = new Intent(this, RoleSelectionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void openProfileCompletion() {
        Intent intent = ProfileCompletionActivity.createIntent(this);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
