package com.example.bodeul.ui.booking;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

/**
 * 환자와 보호자가 병원 동행을 신청하고 현재 요청 상태를 확인하는 화면이다.
 */
public class BookingActivity extends AppCompatActivity {
    private AuthRepository authRepository;
    private BookingRepository bookingRepository;
    private User currentUser;
    private boolean loading;

    private TextView textBookingMode;
    private TextView textBookingRequesterName;
    private TextView textBookingRequesterRole;
    private TextView textBookingRequesterPhone;
    private TextView textBookingLatestStatus;
    private TextView textBookingLatestTitle;
    private TextView textBookingLatestBody;
    private LinearLayout bookingRequestsContainer;
    private TextInputLayout layoutHospitalName;
    private TextInputLayout layoutDepartmentName;
    private TextInputLayout layoutAppointmentAt;
    private TextInputLayout layoutMeetingPlace;
    private TextInputLayout layoutSpecialNotes;
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
        textBookingLatestStatus = findViewById(R.id.textBookingLatestStatus);
        textBookingLatestTitle = findViewById(R.id.textBookingLatestTitle);
        textBookingLatestBody = findViewById(R.id.textBookingLatestBody);
        bookingRequestsContainer = findViewById(R.id.bookingRequestsContainer);
        layoutHospitalName = findViewById(R.id.layoutBookingHospitalName);
        layoutDepartmentName = findViewById(R.id.layoutBookingDepartmentName);
        layoutAppointmentAt = findViewById(R.id.layoutBookingAppointmentAt);
        layoutMeetingPlace = findViewById(R.id.layoutBookingMeetingPlace);
        layoutSpecialNotes = findViewById(R.id.layoutBookingSpecialNotes);
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
        String hospitalName = valueOf(inputHospitalName);
        String departmentName = valueOf(inputDepartmentName);
        String appointmentAt = valueOf(inputAppointmentAt);
        String meetingPlace = valueOf(inputMeetingPlace);
        String specialNotes = valueOf(inputSpecialNotes);
        if (!validateRequired(layoutHospitalName, hospitalName)
                || !validateRequired(layoutDepartmentName, departmentName)
                || !validateRequired(layoutAppointmentAt, appointmentAt)
                || !validateRequired(layoutMeetingPlace, meetingPlace)) {
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
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        emptyView.setPadding(padding, padding, padding, padding);
        emptyView.setText(R.string.booking_requests_empty);
        emptyView.setTextColor(getColor(R.color.bodeul_text_secondary));
        emptyView.setTextSize(14f);
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

    private void clearErrors() {
        layoutHospitalName.setError(null);
        layoutDepartmentName.setError(null);
        layoutAppointmentAt.setError(null);
        layoutMeetingPlace.setError(null);
        layoutSpecialNotes.setError(null);
    }

    private void clearForm() {
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
