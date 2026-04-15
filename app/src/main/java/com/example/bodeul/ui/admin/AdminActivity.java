package com.example.bodeul.ui.admin;

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

import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.R;
import com.example.bodeul.data.AdminRepository;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.AdminDashboard;
import com.example.bodeul.domain.model.AdminRequestOverview;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자용 수동 매칭과 병원 가이드 관리 화면이다.
 */
public class AdminActivity extends AppCompatActivity {
    private AuthRepository authRepository;
    private AdminRepository adminRepository;
    private User currentUser;
    private boolean loading;

    private TextView textAdminMode;
    private TextView textAdminGreeting;
    private TextView textAdminSummary;
    private TextView textAdminManagers;
    private LinearLayout adminPendingRequestsContainer;
    private LinearLayout adminManagedRequestsContainer;
    private LinearLayout adminGuideListContainer;
    private TextInputLayout layoutAdminGuideHospital;
    private TextInputLayout layoutAdminGuideDepartment;
    private TextInputLayout layoutAdminGuideSteps;
    private TextInputEditText inputAdminGuideHospital;
    private TextInputEditText inputAdminGuideDepartment;
    private TextInputEditText inputAdminGuideSteps;
    private MaterialButton buttonSubmitAdminGuide;
    private ProgressBar progressAdmin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        authRepository = ServiceLocator.provideAuthRepository(this);
        adminRepository = ServiceLocator.provideAdminRepository(this);

        textAdminMode = findViewById(R.id.textAdminMode);
        textAdminGreeting = findViewById(R.id.textAdminGreeting);
        textAdminSummary = findViewById(R.id.textAdminSummary);
        textAdminManagers = findViewById(R.id.textAdminManagers);
        adminPendingRequestsContainer = findViewById(R.id.adminPendingRequestsContainer);
        adminManagedRequestsContainer = findViewById(R.id.adminManagedRequestsContainer);
        adminGuideListContainer = findViewById(R.id.adminGuideListContainer);
        layoutAdminGuideHospital = findViewById(R.id.layoutAdminGuideHospital);
        layoutAdminGuideDepartment = findViewById(R.id.layoutAdminGuideDepartment);
        layoutAdminGuideSteps = findViewById(R.id.layoutAdminGuideSteps);
        inputAdminGuideHospital = findViewById(R.id.inputAdminGuideHospital);
        inputAdminGuideDepartment = findViewById(R.id.inputAdminGuideDepartment);
        inputAdminGuideSteps = findViewById(R.id.inputAdminGuideSteps);
        buttonSubmitAdminGuide = findViewById(R.id.buttonSubmitAdminGuide);
        progressAdmin = findViewById(R.id.progressAdmin);

        textAdminMode.setText(adminRepository.isFirebaseBacked()
                ? R.string.admin_mode_firebase
                : R.string.admin_mode_demo);

        findViewById(R.id.buttonBackAdmin).setOnClickListener(view -> finish());
        buttonSubmitAdminGuide.setOnClickListener(view -> submitGuide());

        bindEmptyState();
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
                if (result.getRole() != UserRole.ADMIN) {
                    setLoading(false);
                    Toast.makeText(AdminActivity.this, R.string.toast_admin_only, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                currentUser = result;
                loadDashboard();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                openRoleSelection();
            }
        });
    }

    private void loadDashboard() {
        adminRepository.getAdminDashboard(currentUser, new RepositoryCallback<AdminDashboard>() {
            @Override
            public void onSuccess(AdminDashboard result) {
                setLoading(false);
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                bindEmptyState();
                Toast.makeText(AdminActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindDashboard(AdminDashboard dashboard) {
        textAdminGreeting.setText(getString(
                R.string.admin_greeting,
                dashboard.getAdmin().getName()
        ));
        textAdminSummary.setText(getString(
                R.string.admin_summary,
                dashboard.getPendingRequests().size(),
                dashboard.getManagedRequests().size(),
                dashboard.getHospitalGuides().size()
        ));
        textAdminManagers.setText(buildManagerSummary(dashboard));

        renderPendingRequests(dashboard);
        renderManagedRequests(dashboard.getManagedRequests());
        renderGuides(dashboard.getHospitalGuides());
    }

    private String buildManagerSummary(AdminDashboard dashboard) {
        if (dashboard.getAvailableManagers().isEmpty() && dashboard.getBusyManagers().isEmpty()) {
            return getString(R.string.admin_manager_summary_empty);
        }

        String availableNames = joinManagerNames(dashboard.getAvailableManagers());
        String busyNames = joinManagerNames(dashboard.getBusyManagers());
        return getString(
                R.string.admin_manager_summary,
                dashboard.getAvailableManagers().size(),
                TextUtils.isEmpty(availableNames) ? getString(R.string.admin_manager_none) : availableNames,
                dashboard.getBusyManagers().size(),
                TextUtils.isEmpty(busyNames) ? getString(R.string.admin_manager_none) : busyNames
        );
    }

    private String joinManagerNames(List<User> managers) {
        StringBuilder builder = new StringBuilder();
        for (User manager : managers) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(manager.getName());
        }
        return builder.toString();
    }

    private void renderPendingRequests(AdminDashboard dashboard) {
        adminPendingRequestsContainer.removeAllViews();
        if (dashboard.getPendingRequests().isEmpty()) {
            renderEmptyText(adminPendingRequestsContainer, R.string.admin_pending_empty);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (AdminRequestOverview overview : dashboard.getPendingRequests()) {
            View itemView = inflater.inflate(R.layout.item_admin_request, adminPendingRequestsContainer, false);
            bindRequestItem(itemView, overview, dashboard.getAvailableManagers(), true);
            adminPendingRequestsContainer.addView(itemView);
        }
    }

    private void renderManagedRequests(List<AdminRequestOverview> managedRequests) {
        adminManagedRequestsContainer.removeAllViews();
        if (managedRequests.isEmpty()) {
            renderEmptyText(adminManagedRequestsContainer, R.string.admin_managed_empty);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (AdminRequestOverview overview : managedRequests) {
            View itemView = inflater.inflate(R.layout.item_admin_request, adminManagedRequestsContainer, false);
            bindRequestItem(itemView, overview, new ArrayList<>(), false);
            adminManagedRequestsContainer.addView(itemView);
        }
    }

    private void bindRequestItem(
            View itemView,
            AdminRequestOverview overview,
            List<User> availableManagers,
            boolean actionable
    ) {
        TextView statusView = itemView.findViewById(R.id.textAdminRequestStatus);
        TextView titleView = itemView.findViewById(R.id.textAdminRequestTitle);
        TextView participantsView = itemView.findViewById(R.id.textAdminRequestParticipants);
        TextView scheduleView = itemView.findViewById(R.id.textAdminRequestSchedule);
        TextView managerView = itemView.findViewById(R.id.textAdminRequestManager);
        TextView progressView = itemView.findViewById(R.id.textAdminRequestProgress);
        TextView noteView = itemView.findViewById(R.id.textAdminRequestNote);
        TextView constraintView = itemView.findViewById(R.id.textAdminRequestConstraint);
        LinearLayout managerActionsContainer = itemView.findViewById(R.id.managerActionsContainer);

        bindStatusBadge(statusView, overview.getAppointmentRequest().getStatus());
        titleView.setText(getString(
                R.string.admin_request_title,
                overview.getAppointmentRequest().getHospitalName(),
                overview.getAppointmentRequest().getDepartmentName()
        ));
        participantsView.setText(getString(
                R.string.admin_request_participants,
                buildParticipantDisplay(
                        overview.getPatient(),
                        overview.getAppointmentRequest().getPatientName(),
                        overview.getAppointmentRequest().getPatientPhone(),
                        R.string.admin_participant_patient_missing
                ),
                buildParticipantDisplay(
                        overview.getGuardian(),
                        overview.getAppointmentRequest().getGuardianName(),
                        overview.getAppointmentRequest().getGuardianPhone(),
                        R.string.admin_participant_guardian_missing
                )
        ));
        scheduleView.setText(getString(
                R.string.admin_request_schedule,
                overview.getAppointmentRequest().getAppointmentAt(),
                overview.getAppointmentRequest().getMeetingPlace()
        ));
        managerView.setText(getString(
                R.string.admin_request_manager,
                overview.getManager() == null
                        ? getString(R.string.admin_manager_pending)
                        : overview.getManager().getName()
        ));
        progressView.setText(getString(
                R.string.admin_request_progress,
                buildProgressText(overview)
        ));

        if (TextUtils.isEmpty(overview.getAppointmentRequest().getSpecialNotes())) {
            noteView.setVisibility(View.GONE);
        } else {
            noteView.setVisibility(View.VISIBLE);
            noteView.setText(getString(
                    R.string.admin_request_note,
                    overview.getAppointmentRequest().getSpecialNotes()
            ));
        }

        managerActionsContainer.removeAllViews();
        if (!actionable) {
            constraintView.setVisibility(View.GONE);
            managerActionsContainer.setVisibility(View.GONE);
            return;
        }

        String blockingReason = resolveBlockingReason(overview, availableManagers);
        if (!TextUtils.isEmpty(blockingReason)) {
            constraintView.setVisibility(View.VISIBLE);
            constraintView.setText(blockingReason);
            managerActionsContainer.setVisibility(View.GONE);
            return;
        }

        constraintView.setVisibility(View.GONE);
        managerActionsContainer.setVisibility(View.VISIBLE);
        renderManagerButtons(managerActionsContainer, overview.getAppointmentRequest().getId(), availableManagers);
    }

    private String buildProgressText(AdminRequestOverview overview) {
        CompanionSession session = overview.getSession();
        if (session == null) {
            return toStatusLabel(overview.getAppointmentRequest().getStatus());
        }
        return getString(
                R.string.admin_session_progress_value,
                session.getCurrentStepOrder(),
                toSessionStatusLabel(session)
        );
    }

    private String resolveBlockingReason(AdminRequestOverview overview, List<User> availableManagers) {
        if (!overview.hasLinkedParticipants()) {
            if (hasParticipantInfo(
                    overview.getPatient(),
                    overview.getAppointmentRequest().getPatientName(),
                    overview.getAppointmentRequest().getPatientPhone(),
                    overview.getAppointmentRequest().getPatientEmail()
            ) && hasParticipantInfo(
                    overview.getGuardian(),
                    overview.getAppointmentRequest().getGuardianName(),
                    overview.getAppointmentRequest().getGuardianPhone(),
                    overview.getAppointmentRequest().getGuardianEmail()
            )) {
                return getString(R.string.admin_request_block_pending_link);
            }
            return getString(R.string.admin_request_block_missing_participants);
        }
        if (!overview.hasGuide()) {
            return getString(R.string.admin_request_block_missing_guide);
        }
        if (availableManagers.isEmpty()) {
            return getString(R.string.admin_request_block_no_manager);
        }
        return "";
    }

    private String buildParticipantDisplay(
            User user,
            String snapshotName,
            String snapshotPhone,
            int missingResId
    ) {
        if (user != null) {
            return buildParticipantValue(user.getName(), user.getPhone(), false);
        }
        if (TextUtils.isEmpty(snapshotName) && TextUtils.isEmpty(snapshotPhone)) {
            return getString(missingResId);
        }
        return buildParticipantValue(snapshotName, snapshotPhone, true);
    }

    private boolean hasParticipantInfo(User user, String name, String phone, String email) {
        if (user != null) {
            return true;
        }
        return !TextUtils.isEmpty(name) || !TextUtils.isEmpty(phone) || !TextUtils.isEmpty(email);
    }

    private String buildParticipantValue(String name, String phone, boolean pendingLink) {
        String baseText;
        if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(phone)) {
            baseText = getString(R.string.admin_participant_value_name_phone, name, phone);
        } else if (!TextUtils.isEmpty(name)) {
            baseText = name;
        } else if (!TextUtils.isEmpty(phone)) {
            baseText = phone;
        } else {
            baseText = getString(R.string.admin_participant_value_unknown);
        }
        return pendingLink
                ? getString(R.string.admin_participant_value_pending_link, baseText)
                : baseText;
    }

    private void renderManagerButtons(
            LinearLayout container,
            String requestId,
            List<User> availableManagers
    ) {
        for (User manager : availableManagers) {
            MaterialButton button = new MaterialButton(
                    this,
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle
            );
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.topMargin = dpToPx(8);
            button.setLayoutParams(params);
            button.setText(getString(R.string.admin_assign_button, manager.getName()));
            button.setAllCaps(false);
            button.setCornerRadius(dpToPx(18));
            button.setStrokeColor(ColorStateList.valueOf(getColor(R.color.bodeul_primary)));
            button.setTextColor(getColor(R.color.bodeul_primary));
            button.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.white)));
            button.setOnClickListener(view -> assignManager(requestId, manager.getId()));
            container.addView(button);
        }
    }

    private void renderGuides(List<HospitalGuide> guides) {
        adminGuideListContainer.removeAllViews();
        if (guides.isEmpty()) {
            renderEmptyText(adminGuideListContainer, R.string.admin_guide_empty);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (HospitalGuide guide : guides) {
            View itemView = inflater.inflate(R.layout.item_admin_guide, adminGuideListContainer, false);
            TextView titleView = itemView.findViewById(R.id.textAdminGuideTitle);
            TextView countView = itemView.findViewById(R.id.textAdminGuideCount);
            TextView previewView = itemView.findViewById(R.id.textAdminGuidePreview);

            titleView.setText(getString(
                    R.string.admin_guide_title,
                    guide.getHospitalName(),
                    guide.getDepartmentName()
            ));
            countView.setText(getString(
                    R.string.admin_guide_count,
                    guide.getSteps().size()
            ));
            previewView.setText(buildGuidePreview(guide));
            adminGuideListContainer.addView(itemView);
        }
    }

    private String buildGuidePreview(HospitalGuide guide) {
        StringBuilder builder = new StringBuilder();
        int previewCount = Math.min(guide.getSteps().size(), 2);
        for (int index = 0; index < previewCount; index++) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(guide.getSteps().get(index).getTitle());
        }
        return getString(
                R.string.admin_guide_preview,
                builder.toString()
        );
    }

    private void submitGuide() {
        if (currentUser == null || loading) {
            return;
        }

        clearGuideErrors();
        String hospitalName = valueOf(inputAdminGuideHospital);
        String departmentName = valueOf(inputAdminGuideDepartment);
        String rawSteps = valueOf(inputAdminGuideSteps);
        boolean valid = validateRequired(layoutAdminGuideHospital, hospitalName)
                && validateRequired(layoutAdminGuideDepartment, departmentName)
                && validateRequired(layoutAdminGuideSteps, rawSteps);
        if (!valid) {
            return;
        }

        List<String> stepLines = parseStepLines(rawSteps);
        if (stepLines.isEmpty()) {
            layoutAdminGuideSteps.setError(getString(R.string.error_required_field));
            return;
        }

        setLoading(true);
        adminRepository.saveHospitalGuide(
                currentUser,
                hospitalName,
                departmentName,
                stepLines,
                new RepositoryCallback<AdminDashboard>() {
                    @Override
                    public void onSuccess(AdminDashboard result) {
                        Toast.makeText(AdminActivity.this, R.string.admin_guide_saved, Toast.LENGTH_SHORT).show();
                        clearGuideForm();
                        setLoading(false);
                        bindDashboard(result);
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        Toast.makeText(AdminActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void assignManager(String requestId, String managerUserId) {
        if (currentUser == null || loading) {
            return;
        }

        setLoading(true);
        adminRepository.assignManager(
                currentUser,
                requestId,
                managerUserId,
                new RepositoryCallback<AdminDashboard>() {
                    @Override
                    public void onSuccess(AdminDashboard result) {
                        Toast.makeText(AdminActivity.this, R.string.admin_match_saved, Toast.LENGTH_SHORT).show();
                        setLoading(false);
                        bindDashboard(result);
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        Toast.makeText(AdminActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private List<String> parseStepLines(String rawSteps) {
        List<String> stepLines = new ArrayList<>();
        String[] lines = rawSteps.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                stepLines.add(trimmed);
            }
        }
        return stepLines;
    }

    private void renderEmptyText(LinearLayout container, int messageResId) {
        TextView emptyView = new TextView(this);
        emptyView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        int padding = dpToPx(20);
        emptyView.setBackgroundResource(R.drawable.bg_surface_card_soft);
        emptyView.setPadding(padding, padding, padding, padding);
        emptyView.setText(messageResId);
        emptyView.setTextColor(getColor(R.color.bodeul_text_secondary));
        emptyView.setTextSize(14f);
        emptyView.setLineSpacing(0f, 1.2f);
        container.addView(emptyView);
    }

    private void bindEmptyState() {
        textAdminGreeting.setText(R.string.admin_empty_greeting);
        textAdminSummary.setText(R.string.admin_empty_summary);
        textAdminManagers.setText(R.string.admin_manager_summary_empty);
        adminPendingRequestsContainer.removeAllViews();
        adminManagedRequestsContainer.removeAllViews();
        adminGuideListContainer.removeAllViews();
        renderEmptyText(adminPendingRequestsContainer, R.string.admin_pending_empty);
        renderEmptyText(adminManagedRequestsContainer, R.string.admin_managed_empty);
        renderEmptyText(adminGuideListContainer, R.string.admin_guide_empty);
    }

    private void bindStatusBadge(TextView textView, AppointmentStatus status) {
        int backgroundColor;
        int textColor;
        switch (status) {
            case MATCHED:
                backgroundColor = R.color.bodeul_primary;
                textColor = R.color.white;
                break;
            case IN_PROGRESS:
            case COMPLETED:
                backgroundColor = R.color.bodeul_success;
                textColor = R.color.white;
                break;
            case CANCELED:
                backgroundColor = R.color.bodeul_surface_alt;
                textColor = R.color.bodeul_text_primary;
                break;
            case REQUESTED:
            default:
                backgroundColor = R.color.bodeul_warning;
                textColor = R.color.bodeul_text_primary;
                break;
        }

        textView.setText(toStatusLabel(status));
        textView.setBackgroundTintList(ColorStateList.valueOf(getColor(backgroundColor)));
        textView.setTextColor(getColor(textColor));
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

    private String toSessionStatusLabel(CompanionSession session) {
        switch (session.getStatus()) {
            case READY:
                return getString(R.string.guardian_report_session_ready);
            case WAITING:
                return getString(R.string.guardian_report_session_waiting);
            case IN_TREATMENT:
                return getString(R.string.guardian_report_session_treatment);
            case PAYMENT:
                return getString(R.string.guardian_report_session_payment);
            case COMPLETED:
                return getString(R.string.guardian_report_session_completed);
            case MEETING:
            default:
                return getString(R.string.guardian_report_session_meeting);
        }
    }

    private boolean validateRequired(TextInputLayout layout, String value) {
        if (TextUtils.isEmpty(value)) {
            layout.setError(getString(R.string.error_required_field));
            return false;
        }
        return true;
    }

    private void clearGuideErrors() {
        layoutAdminGuideHospital.setError(null);
        layoutAdminGuideDepartment.setError(null);
        layoutAdminGuideSteps.setError(null);
    }

    private void clearGuideForm() {
        inputAdminGuideHospital.setText(null);
        inputAdminGuideDepartment.setText(null);
        inputAdminGuideSteps.setText(null);
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        progressAdmin.setVisibility(loading ? View.VISIBLE : View.GONE);
        buttonSubmitAdminGuide.setEnabled(!loading);
        inputAdminGuideHospital.setEnabled(!loading);
        inputAdminGuideDepartment.setEnabled(!loading);
        inputAdminGuideSteps.setEnabled(!loading);
    }

    private String valueOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private int dpToPx(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
