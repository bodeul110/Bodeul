package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentFollowUpReviewRating;
import com.example.bodeul.domain.model.AppointmentFollowUpSettlementStatus;
import com.example.bodeul.domain.model.AppointmentFollowUpSupportEscalationStatus;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButton;

/**
 * 완료된 동행의 후기, 정산, 긴급 안내를 한 화면에서 정리한다.
 */
public class BookingFollowUpActivity extends AppCompatActivity
        implements BookingFollowUpRatingOptionBinder.Listener {
    private static final String EXTRA_REQUEST_ID = "requestId";
    private static final String STATE_SELECTED_RATING = "selectedRating";

    private AuthRepository authRepository;
    private BookingRepository bookingRepository;
    private BookingFollowUpCoordinator bookingFollowUpCoordinator;
    private BookingFollowUpBinder bookingFollowUpBinder;

    private View bookingFollowUpStatePanel;
    private View bookingFollowUpContentContainer;
    private ProgressBar progressBookingFollowUp;
    private MaterialButton buttonReviewSave;
    private MaterialButton buttonSettlementConfirm;
    private MaterialButton buttonSettlementHelp;
    private MaterialButton buttonCallManager;

    private String requestId;
    private User currentUser;
    private AppointmentRequestDetail currentDetail;
    private AppointmentFollowUpRecord currentFollowUpRecord = AppointmentFollowUpRecord.empty("");
    @Nullable
    private AppointmentFollowUpReviewRating selectedRating;

    public static Intent createIntent(Context context, String requestId) {
        Intent intent = new Intent(context, BookingFollowUpActivity.class);
        intent.putExtra(EXTRA_REQUEST_ID, requestId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_follow_up);

        requestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);
        currentFollowUpRecord = AppointmentFollowUpRecord.empty(requestId);
        authRepository = ServiceLocator.provideAuthRepository(this);
        bookingRepository = ServiceLocator.provideBookingRepository(this);
        bookingFollowUpCoordinator = new BookingFollowUpCoordinator(
                this,
                new BookingPresentationFormatter(this)
        );

        bookingFollowUpStatePanel = findViewById(R.id.bookingFollowUpStatePanel);
        bookingFollowUpContentContainer = findViewById(R.id.bookingFollowUpContentContainer);
        progressBookingFollowUp = findViewById(R.id.progressBookingFollowUp);
        buttonReviewSave = findViewById(R.id.buttonBookingFollowUpReviewSave);
        buttonSettlementConfirm = findViewById(R.id.buttonBookingFollowUpSettlementConfirm);
        buttonSettlementHelp = findViewById(R.id.buttonBookingFollowUpSettlementHelp);
        buttonCallManager = findViewById(R.id.buttonBookingFollowUpCallManager);

        bookingFollowUpBinder = new BookingFollowUpBinder(
                this,
                getLayoutInflater(),
                findViewById(R.id.textBookingFollowUpMode),
                findViewById(R.id.textBookingFollowUpHeroBadge),
                findViewById(R.id.textBookingFollowUpHeroTitle),
                findViewById(R.id.textBookingFollowUpHeroBody),
                findViewById(R.id.textBookingFollowUpReviewTitle),
                findViewById(R.id.textBookingFollowUpReviewBody),
                findViewById(R.id.layoutBookingFollowUpReviewOptionContainer),
                findViewById(R.id.textBookingFollowUpReviewSummary),
                findViewById(R.id.textBookingFollowUpReviewSavedState),
                buttonReviewSave,
                findViewById(R.id.layoutBookingFollowUpSettlementContainer),
                findViewById(R.id.textBookingFollowUpSettlementSavedState),
                buttonSettlementConfirm,
                buttonSettlementHelp,
                findViewById(R.id.textBookingFollowUpEmergencyTitle),
                findViewById(R.id.textBookingFollowUpEmergencyBody),
                findViewById(R.id.layoutBookingFollowUpEmergencyContainer),
                findViewById(R.id.textBookingFollowUpEmergencySavedState),
                buttonCallManager
        );

        if (savedInstanceState != null) {
            selectedRating = AppointmentFollowUpReviewRating.fromValue(
                    savedInstanceState.getString(STATE_SELECTED_RATING, "")
            );
        }

        findViewById(R.id.buttonBackBookingFollowUp).setOnClickListener(view -> finish());
        buttonReviewSave.setOnClickListener(view -> saveReviewSelection());
        buttonSettlementConfirm.setOnClickListener(view ->
                saveSettlementSelection(AppointmentFollowUpSettlementStatus.CONFIRMED));
        buttonSettlementHelp.setOnClickListener(view -> showSettlementInquiryDialog());
        buttonCallManager.setOnClickListener(view -> {
            recordSupportEscalation(AppointmentFollowUpSupportEscalationStatus.MANAGER_CALLED);
            callManager();
        });
        findViewById(R.id.buttonBookingFollowUpEmergencyDial).setOnClickListener(view -> {
            recordSupportEscalation(AppointmentFollowUpSupportEscalationStatus.DIALED_119);
            openDialer("119");
        });
        findViewById(R.id.buttonBookingFollowUpEmergencyGuide).setOnClickListener(
                view -> {
                    recordSupportEscalation(AppointmentFollowUpSupportEscalationStatus.GUIDE_VIEWED);
                    showEmergencyGuideDialog();
                }
        );
        findViewById(R.id.buttonBookingFollowUpDetail).setOnClickListener(view -> openBookingStatus());
        bookingFollowUpContentContainer.setVisibility(View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        reloadDetail();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(
                STATE_SELECTED_RATING,
                selectedRating == null ? "" : selectedRating.getValue()
        );
    }

    @Override
    public void onSelectRating(AppointmentFollowUpReviewRating rating) {
        selectedRating = rating;
        bindScreen(currentFollowUpRecord);
    }

    private void reloadDetail() {
        if (TextUtils.isEmpty(requestId)) {
            showLoadErrorState(getString(R.string.booking_status_request_missing));
            return;
        }

        setLoading(true);
        hideBlockingState();
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    openProfileCompletion();
                    return;
                }
                if (result.getRole() != UserRole.PATIENT && result.getRole() != UserRole.GUARDIAN) {
                    setLoading(false);
                    showPermissionState();
                    return;
                }

                currentUser = result;
                bookingRepository.getAppointmentRequestDetail(
                        result,
                        requestId,
                        new RepositoryCallback<AppointmentRequestDetail>() {
                            @Override
                            public void onSuccess(AppointmentRequestDetail result) {
                                currentDetail = result;
                                if (result.getAppointmentRequest().getStatus() != AppointmentStatus.COMPLETED) {
                                    setLoading(false);
                                    showUnavailableState();
                                    return;
                                }
                                loadFollowUpRecord();
                            }

                            @Override
                            public void onError(String message) {
                                setLoading(false);
                                showLoadErrorState(message);
                            }
                        }
                );
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void loadFollowUpRecord() {
        if (currentUser == null) {
            setLoading(false);
            showAuthState();
            return;
        }
        bookingRepository.getAppointmentFollowUp(
                currentUser,
                requestId,
                new RepositoryCallback<AppointmentFollowUpRecord>() {
                    @Override
                    public void onSuccess(AppointmentFollowUpRecord result) {
                        currentFollowUpRecord = result;
                        if (selectedRating == null && result.hasSavedReview()) {
                            selectedRating = result.getReviewRating();
                        }
                        bindScreen(result);
                        bookingFollowUpContentContainer.setVisibility(View.VISIBLE);
                        hideBlockingState();
                        setLoading(false);
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        showLoadErrorState(message);
                    }
                }
        );
    }

    private void bindScreen(AppointmentFollowUpRecord followUpRecord) {
        if (currentUser == null || currentDetail == null) {
            return;
        }
        bookingFollowUpBinder.bindScreen(
                bookingFollowUpCoordinator.buildScreenModel(
                        currentUser,
                        currentDetail,
                        bookingRepository.isFirebaseBacked(),
                        selectedRating,
                        followUpRecord
                ),
                this
        );
    }

    private void saveReviewSelection() {
        if (TextUtils.isEmpty(requestId) || selectedRating == null || currentUser == null) {
            return;
        }
        setLoading(true);
        bookingRepository.saveAppointmentFollowUpReview(
                currentUser,
                requestId,
                selectedRating,
                new RepositoryCallback<AppointmentFollowUpRecord>() {
                    @Override
                    public void onSuccess(AppointmentFollowUpRecord result) {
                        currentFollowUpRecord = result;
                        bindScreen(result);
                        setLoading(false);
                        new AlertDialog.Builder(BookingFollowUpActivity.this)
                                .setTitle(R.string.booking_follow_up_review_saved_dialog_title)
                                .setMessage(R.string.booking_follow_up_review_saved_dialog_body_live)
                                .setPositiveButton(R.string.booking_follow_up_dialog_confirm, null)
                                .show();
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        showLoadErrorState(message);
                    }
                }
        );
    }

    private void saveSettlementSelection(AppointmentFollowUpSettlementStatus status) {
        if (TextUtils.isEmpty(requestId) || currentUser == null || currentDetail == null) {
            return;
        }
        setLoading(true);
        bookingRepository.saveAppointmentFollowUpSettlement(
                currentUser,
                requestId,
                status,
                bookingFollowUpCoordinator.createSettlementSaveNote(currentDetail, status),
                new RepositoryCallback<AppointmentFollowUpRecord>() {
                    @Override
                    public void onSuccess(AppointmentFollowUpRecord result) {
                        currentFollowUpRecord = result;
                        bindScreen(result);
                        setLoading(false);
                        new AlertDialog.Builder(BookingFollowUpActivity.this)
                                .setTitle(R.string.booking_follow_up_settlement_saved_dialog_title)
                                .setMessage(R.string.booking_follow_up_settlement_saved_dialog_body)
                                .setPositiveButton(R.string.booking_follow_up_dialog_confirm, null)
                                .show();
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        showLoadErrorState(message);
                    }
                }
        );
    }

    private void showSettlementInquiryDialog() {
        if (currentDetail == null) {
            return;
        }
        final AppointmentFollowUpSettlementStatus[] statuses = new AppointmentFollowUpSettlementStatus[]{
                AppointmentFollowUpSettlementStatus.OVERTIME_REVIEW,
                AppointmentFollowUpSettlementStatus.REFUND_REVIEW,
                AppointmentFollowUpSettlementStatus.NEEDS_HELP
        };
        final CharSequence[] labels = new CharSequence[]{
                getString(R.string.booking_follow_up_settlement_inquiry_overtime),
                getString(R.string.booking_follow_up_settlement_inquiry_refund),
                getString(R.string.booking_follow_up_settlement_inquiry_general)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.booking_follow_up_settlement_inquiry_dialog_title)
                .setItems(labels, (dialog, which) -> {
                    if (which < 0 || which >= statuses.length) {
                        return;
                    }
                    saveSettlementSelection(statuses[which]);
                })
                .setNegativeButton(R.string.booking_follow_up_dialog_cancel, null)
                .show();
    }

    private void recordSupportEscalation(AppointmentFollowUpSupportEscalationStatus escalationStatus) {
        if (TextUtils.isEmpty(requestId) || currentUser == null) {
            return;
        }
        bookingRepository.saveAppointmentFollowUpSupportEscalation(
                currentUser,
                requestId,
                escalationStatus,
                new RepositoryCallback<AppointmentFollowUpRecord>() {
                    @Override
                    public void onSuccess(AppointmentFollowUpRecord result) {
                        currentFollowUpRecord = result;
                        bindScreen(result);
                    }

                    @Override
                    public void onError(String message) {
                        // SOS 안내 동작은 저장 실패와 무관하게 바로 진행한다.
                    }
                }
        );
    }

    private void callManager() {
        if (currentDetail == null || currentDetail.getManager() == null) {
            return;
        }
        String phone = currentDetail.getManager().getPhone();
        if (TextUtils.isEmpty(phone)) {
            return;
        }
        openDialer(phone);
    }

    private void openDialer(String phoneNumber) {
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phoneNumber)));
    }

    private void showEmergencyGuideDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.booking_follow_up_emergency_dialog_title)
                .setMessage(R.string.booking_follow_up_emergency_dialog_body)
                .setPositiveButton(R.string.booking_follow_up_dialog_confirm, null)
                .show();
    }

    private void openBookingStatus() {
        if (TextUtils.isEmpty(requestId)) {
            return;
        }
        startActivity(BookingStatusActivity.createIntent(this, requestId));
    }

    private void setLoading(boolean loading) {
        progressBookingFollowUp.setVisibility(loading ? View.VISIBLE : View.GONE);
        buttonReviewSave.setEnabled(!loading && selectedRating != null);
        buttonSettlementConfirm.setEnabled(!loading);
        buttonSettlementHelp.setEnabled(!loading);
        buttonCallManager.setEnabled(!loading && currentDetail != null
                && currentDetail.getManager() != null
                && !TextUtils.isEmpty(currentDetail.getManager().getPhone()));
    }

    private void showUnavailableState() {
        showBlockingState(
                StatePanelHelper.Tone.INFO,
                getString(R.string.state_badge_notice),
                getString(R.string.booking_follow_up_unavailable_title),
                getString(R.string.booking_follow_up_unavailable_body),
                getString(R.string.booking_follow_up_action_open_detail),
                view -> openBookingStatus(),
                getString(R.string.state_action_open_home),
                view -> openHome()
        );
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.booking_follow_up_title)),
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
                getString(R.string.state_load_error_title, getString(R.string.booking_follow_up_title)),
                body,
                getString(R.string.state_action_retry),
                view -> reloadDetail(),
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
                bookingFollowUpStatePanel,
                tone,
                badge,
                title,
                body,
                primaryText,
                primaryListener,
                secondaryText,
                secondaryListener
        );
        bookingFollowUpContentContainer.setVisibility(View.GONE);
    }

    private void hideBlockingState() {
        StatePanelHelper.hide(bookingFollowUpStatePanel);
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
