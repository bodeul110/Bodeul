package com.example.bodeul.ui.booking;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

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
    private MaterialButton buttonSubmitBooking;
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
        buttonSubmitBooking = findViewById(R.id.buttonSubmitBooking);
        progressBooking = findViewById(R.id.progressBooking);

        textBookingMode.setText(bookingRepository.isFirebaseBacked()
                ? R.string.booking_mode_firebase
                : R.string.booking_mode_demo);

        findViewById(R.id.buttonBackBooking).setOnClickListener(view -> finish());
        buttonSubmitBooking.setOnClickListener(view -> submitAppointmentRequest());
        configureAppointmentPicker();

        bindEmptySummary();
        renderEmptyRequests();
    }

    @Override
    protected void onStart() {
        super.onStart();
        setLoading(true);
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    openProfileCompletion();
                    return;
                }
                if (!supportsBookingRole(result.getRole())) {
                    setLoading(false);
                    Toast.makeText(BookingActivity.this, R.string.toast_booking_role_only, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                currentUser = result;
                bindRequester(result);
                loadRequests();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                openRoleSelection();
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
                bindLatestRequest(result.isEmpty() ? null : result.get(0), result.size());
                renderRequests(result);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                bindEmptySummary();
                renderEmptyRequests();
                Toast.makeText(BookingActivity.this, message, Toast.LENGTH_SHORT).show();
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
                        clearForm();
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
            bookingRequestsContainer.addView(requestView);
        }
    }

    private void renderEmptyRequests() {
        bookingRequestsContainer.removeAllViews();

        TextView emptyView = new TextView(this);
        emptyView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        emptyView.setBackgroundResource(R.drawable.bg_surface_card_soft);
        emptyView.setPadding(padding, padding, padding, padding);
        emptyView.setText(R.string.booking_requests_empty);
        emptyView.setTextColor(getColor(R.color.bodeul_text_secondary));
        emptyView.setTextSize(14f);
        emptyView.setLineSpacing(0f, 1.2f);
        bookingRequestsContainer.addView(emptyView);
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

    private void clearForm() {
        inputBookingLinkedName.setText(null);
        inputBookingLinkedPhone.setText(null);
        inputBookingLinkedEmail.setText(null);
        inputHospitalName.setText(null);
        inputDepartmentName.setText(null);
        inputAppointmentAt.setText(null);
        inputMeetingPlace.setText(null);
        inputSpecialNotes.setText(null);
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        progressBooking.setVisibility(loading ? View.VISIBLE : View.GONE);
        buttonSubmitBooking.setEnabled(!loading);
        inputBookingLinkedName.setEnabled(!loading);
        inputBookingLinkedPhone.setEnabled(!loading);
        inputBookingLinkedEmail.setEnabled(!loading);
        inputHospitalName.setEnabled(!loading);
        inputDepartmentName.setEnabled(!loading);
        inputAppointmentAt.setEnabled(!loading);
        inputMeetingPlace.setEnabled(!loading);
        inputSpecialNotes.setEnabled(!loading);
    }

    private String valueOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
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
