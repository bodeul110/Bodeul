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

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AdminRepository;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.AdminDashboard;
import com.example.bodeul.domain.model.AdminRequestOverview;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.ManagerDocumentHistoryEntry;
import com.example.bodeul.domain.model.ManagerDocumentHistoryEventType;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerDocumentStatus;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * 관리자용 수동 매칭과 병원 가이드 관리 화면이다.
 */
public class AdminActivity extends AppCompatActivity {
    private enum ManagedRequestDateFilter {
        ALL,
        TODAY,
        UPCOMING,
        PAST
    }

    private enum ManagedRequestFilter {
        ALL,
        MATCHED,
        IN_PROGRESS,
        COMPLETED,
        CANCELED
    }

    private AuthRepository authRepository;
    private AdminRepository adminRepository;
    private User currentUser;
    private HospitalGuide editingGuide;
    private List<AdminRequestOverview> pendingRequestsSnapshot = new ArrayList<>();
    private List<AdminRequestOverview> managedRequestsSnapshot = new ArrayList<>();
    private List<User> availableManagersSnapshot = new ArrayList<>();
    private ManagedRequestFilter managedRequestFilter = ManagedRequestFilter.ALL;
    private ManagedRequestDateFilter managedRequestDateFilter = ManagedRequestDateFilter.ALL;
    private final Set<String> expandedRequestIds = new HashSet<>();
    private boolean loading;

    private TextView textAdminMode;
    private TextView textAdminGreeting;
    private TextView textAdminSummary;
    private TextView textAdminManagers;
    private TextView textAdminGuideFormTitle;
    private TextView textAdminGuideFormBadge;
    private TextView textAdminGuideFormHelper;
    private TextView textAdminManagedSummary;
    private View adminStatePanel;
    private View adminContentContainer;
    private LinearLayout adminManagerDocumentsContainer;
    private LinearLayout adminPendingRequestsContainer;
    private LinearLayout adminManagedDateFilterContainer;
    private LinearLayout adminManagedFilterContainer;
    private LinearLayout adminManagedRequestsContainer;
    private LinearLayout adminGuideListContainer;
    private TextInputLayout layoutAdminGuideHospital;
    private TextInputLayout layoutAdminGuideDepartment;
    private TextInputLayout layoutAdminGuideSteps;
    private TextInputEditText inputAdminGuideHospital;
    private TextInputEditText inputAdminGuideDepartment;
    private TextInputEditText inputAdminGuideSteps;
    private MaterialButton buttonSubmitAdminGuide;
    private MaterialButton buttonCancelAdminGuideEdit;
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
        textAdminGuideFormTitle = findViewById(R.id.textAdminGuideFormTitle);
        textAdminGuideFormBadge = findViewById(R.id.textAdminGuideFormBadge);
        textAdminGuideFormHelper = findViewById(R.id.textAdminGuideFormHelper);
        textAdminManagedSummary = findViewById(R.id.textAdminManagedSummary);
        adminStatePanel = findViewById(R.id.adminStatePanel);
        adminContentContainer = findViewById(R.id.adminContentContainer);
        adminManagerDocumentsContainer = findViewById(R.id.adminManagerDocumentsContainer);
        adminPendingRequestsContainer = findViewById(R.id.adminPendingRequestsContainer);
        adminManagedDateFilterContainer = findViewById(R.id.adminManagedDateFilterContainer);
        adminManagedFilterContainer = findViewById(R.id.adminManagedFilterContainer);
        adminManagedRequestsContainer = findViewById(R.id.adminManagedRequestsContainer);
        adminGuideListContainer = findViewById(R.id.adminGuideListContainer);
        layoutAdminGuideHospital = findViewById(R.id.layoutAdminGuideHospital);
        layoutAdminGuideDepartment = findViewById(R.id.layoutAdminGuideDepartment);
        layoutAdminGuideSteps = findViewById(R.id.layoutAdminGuideSteps);
        inputAdminGuideHospital = findViewById(R.id.inputAdminGuideHospital);
        inputAdminGuideDepartment = findViewById(R.id.inputAdminGuideDepartment);
        inputAdminGuideSteps = findViewById(R.id.inputAdminGuideSteps);
        buttonSubmitAdminGuide = findViewById(R.id.buttonSubmitAdminGuide);
        buttonCancelAdminGuideEdit = findViewById(R.id.buttonCancelAdminGuideEdit);
        progressAdmin = findViewById(R.id.progressAdmin);

        textAdminMode.setText(adminRepository.isFirebaseBacked()
                ? R.string.admin_mode_firebase
                : R.string.admin_mode_demo);

        findViewById(R.id.buttonBackAdmin).setOnClickListener(view -> finish());
        buttonSubmitAdminGuide.setOnClickListener(view -> submitGuide());
        buttonCancelAdminGuideEdit.setOnClickListener(view -> exitGuideEditMode());

        bindEmptyState();
        updateGuideFormMode();
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
                if (result.getRole() != UserRole.ADMIN) {
                    setLoading(false);
                    showPermissionState();
                    return;
                }

                currentUser = result;
                hideBlockingState();
                loadDashboard();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void loadDashboard() {
        adminRepository.getAdminDashboard(currentUser, new RepositoryCallback<AdminDashboard>() {
            @Override
            public void onSuccess(AdminDashboard result) {
                setLoading(false);
                hideBlockingState();
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                bindEmptyState();
                showLoadErrorState(message);
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

        renderManagerDocuments(dashboard.getManagerDocumentOverviews());
        pendingRequestsSnapshot = new ArrayList<>(dashboard.getPendingRequests());
        availableManagersSnapshot = new ArrayList<>(dashboard.getAvailableManagers());
        renderPendingRequests(pendingRequestsSnapshot, availableManagersSnapshot);
        managedRequestsSnapshot = new ArrayList<>(dashboard.getManagedRequests());
        renderManagedRequests(managedRequestsSnapshot);
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
        renderPendingRequests(dashboard.getPendingRequests(), dashboard.getAvailableManagers());
    }

    private void renderPendingRequests(
            List<AdminRequestOverview> pendingRequests,
            List<User> availableManagers
    ) {
        adminPendingRequestsContainer.removeAllViews();
        if (pendingRequests.isEmpty()) {
            renderEmptyText(
                    adminPendingRequestsContainer,
                    R.string.admin_pending_title,
                    R.string.admin_pending_empty
            );
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (AdminRequestOverview overview : pendingRequests) {
            View itemView = inflater.inflate(R.layout.item_admin_request, adminPendingRequestsContainer, false);
            bindRequestItem(itemView, overview, availableManagers, true);
            adminPendingRequestsContainer.addView(itemView);
        }
    }

    private void renderManagedRequests(List<AdminRequestOverview> managedRequests) {
        renderManagedFilters(managedRequests);
        renderManagedDateFilters(managedRequests);
        bindManagedSummary(managedRequests);
        adminManagedRequestsContainer.removeAllViews();
        List<AdminRequestOverview> filteredRequests = filterManagedRequests(managedRequests);
        if (managedRequests.isEmpty()) {
            renderEmptyText(
                    adminManagedRequestsContainer,
                    R.string.admin_managed_title,
                    R.string.admin_managed_empty
            );
            return;
        }
        if (filteredRequests.isEmpty()) {
            renderEmptyText(
                    adminManagedRequestsContainer,
                    R.string.admin_managed_title,
                    R.string.admin_managed_filtered_empty
            );
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (AdminRequestOverview overview : filteredRequests) {
            View itemView = inflater.inflate(R.layout.item_admin_request, adminManagedRequestsContainer, false);
            bindRequestItem(itemView, overview, new ArrayList<>(), false);
            adminManagedRequestsContainer.addView(itemView);
        }
    }

    private void renderManagedFilters(List<AdminRequestOverview> managedRequests) {
        adminManagedFilterContainer.removeAllViews();
        if (managedRequests.isEmpty()) {
            adminManagedFilterContainer.setVisibility(View.GONE);
            return;
        }

        adminManagedFilterContainer.setVisibility(View.VISIBLE);
        for (ManagedRequestFilter filter : ManagedRequestFilter.values()) {
            MaterialButton button = new MaterialButton(
                    this,
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle
            );
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd(dpToPx(8));
            button.setLayoutParams(params);
            button.setAllCaps(false);
            button.setCornerRadius(dpToPx(18));
            button.setText(getString(
                    R.string.admin_managed_filter_button,
                    toManagedFilterLabel(filter),
                    countManagedRequests(managedRequests, filter)
            ));
            bindManagedFilterButtonStyle(button, filter == managedRequestFilter);
            button.setOnClickListener(view -> {
                managedRequestFilter = filter;
                renderManagedRequests(managedRequestsSnapshot);
            });
            adminManagedFilterContainer.addView(button);
        }
    }

    private void renderManagedDateFilters(List<AdminRequestOverview> managedRequests) {
        adminManagedDateFilterContainer.removeAllViews();
        if (managedRequests.isEmpty()) {
            adminManagedDateFilterContainer.setVisibility(View.GONE);
            return;
        }

        adminManagedDateFilterContainer.setVisibility(View.VISIBLE);
        for (ManagedRequestDateFilter filter : ManagedRequestDateFilter.values()) {
            MaterialButton button = new MaterialButton(
                    this,
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle
            );
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd(dpToPx(8));
            button.setLayoutParams(params);
            button.setAllCaps(false);
            button.setCornerRadius(dpToPx(18));
            button.setText(getString(
                    R.string.admin_managed_date_filter_button,
                    toManagedDateFilterLabel(filter),
                    countManagedRequestsByDate(managedRequests, filter)
            ));
            bindManagedFilterButtonStyle(button, filter == managedRequestDateFilter);
            button.setOnClickListener(view -> {
                managedRequestDateFilter = filter;
                renderManagedRequests(managedRequestsSnapshot);
            });
            adminManagedDateFilterContainer.addView(button);
        }
    }

    private void bindManagedFilterButtonStyle(MaterialButton button, boolean selected) {
        if (selected) {
            button.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.bodeul_primary)));
            button.setStrokeColor(ColorStateList.valueOf(getColor(R.color.bodeul_primary)));
            button.setTextColor(getColor(R.color.white));
            return;
        }
        button.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.white)));
        button.setStrokeColor(ColorStateList.valueOf(getColor(R.color.bodeul_primary)));
        button.setTextColor(getColor(R.color.bodeul_primary));
    }

    private void bindManagedSummary(List<AdminRequestOverview> managedRequests) {
        if (managedRequests.isEmpty()) {
            textAdminManagedSummary.setText(R.string.admin_managed_summary_empty);
            return;
        }

        int matchedCount = countManagedRequests(managedRequests, ManagedRequestFilter.MATCHED);
        int inProgressCount = countManagedRequests(managedRequests, ManagedRequestFilter.IN_PROGRESS);
        int completedCount = countManagedRequests(managedRequests, ManagedRequestFilter.COMPLETED);
        int canceledCount = countManagedRequests(managedRequests, ManagedRequestFilter.CANCELED);
        int visibleCount = filterManagedRequests(managedRequests).size();
        int totalCount = managedRequests.size();

        textAdminManagedSummary.setText(getString(
                R.string.admin_managed_summary,
                matchedCount,
                inProgressCount,
                completedCount,
                canceledCount,
                toManagedFilterLabel(managedRequestFilter),
                toManagedDateFilterLabel(managedRequestDateFilter),
                visibleCount,
                totalCount
        ));
    }

    private int countManagedRequests(List<AdminRequestOverview> managedRequests, ManagedRequestFilter filter) {
        return filterManagedRequests(managedRequests, filter).size();
    }

    private int countManagedRequestsByDate(List<AdminRequestOverview> managedRequests, ManagedRequestDateFilter filter) {
        int count = 0;
        for (AdminRequestOverview overview : managedRequests) {
            if (matchesManagedDateFilter(overview, filter)) {
                count++;
            }
        }
        return count;
    }

    private List<AdminRequestOverview> filterManagedRequests(List<AdminRequestOverview> managedRequests) {
        return filterManagedRequests(managedRequests, managedRequestFilter);
    }

    private List<AdminRequestOverview> filterManagedRequests(
            List<AdminRequestOverview> managedRequests,
            ManagedRequestFilter filter
    ) {
        List<AdminRequestOverview> filteredRequests = new ArrayList<>();
        for (AdminRequestOverview overview : managedRequests) {
            if (matchesManagedFilter(overview, filter)
                    && matchesManagedDateFilter(overview, managedRequestDateFilter)) {
                filteredRequests.add(overview);
            }
        }
        return filteredRequests;
    }

    private boolean matchesManagedFilter(AdminRequestOverview overview, ManagedRequestFilter filter) {
        AppointmentStatus status = overview.getAppointmentRequest().getStatus();
        switch (filter) {
            case MATCHED:
                return status == AppointmentStatus.MATCHED;
            case IN_PROGRESS:
                return status == AppointmentStatus.IN_PROGRESS;
            case COMPLETED:
                return status == AppointmentStatus.COMPLETED;
            case CANCELED:
                return status == AppointmentStatus.CANCELED;
            case ALL:
            default:
                return true;
        }
    }

    private boolean matchesManagedDateFilter(AdminRequestOverview overview, ManagedRequestDateFilter filter) {
        if (filter == ManagedRequestDateFilter.ALL) {
            return true;
        }

        Calendar appointmentCalendar = parseAppointmentCalendar(overview.getAppointmentRequest().getAppointmentAt());
        if (appointmentCalendar == null) {
            return false;
        }

        Calendar today = Calendar.getInstance();
        normalizeDate(today);

        Calendar appointmentDate = (Calendar) appointmentCalendar.clone();
        normalizeDate(appointmentDate);

        int compare = appointmentDate.compareTo(today);
        switch (filter) {
            case TODAY:
                return compare == 0;
            case UPCOMING:
                return compare > 0;
            case PAST:
                return compare < 0;
            case ALL:
            default:
                return true;
        }
    }

    private String toManagedFilterLabel(ManagedRequestFilter filter) {
        switch (filter) {
            case MATCHED:
                return getString(R.string.admin_managed_filter_matched);
            case IN_PROGRESS:
                return getString(R.string.admin_managed_filter_in_progress);
            case COMPLETED:
                return getString(R.string.admin_managed_filter_completed);
            case CANCELED:
                return getString(R.string.admin_managed_filter_canceled);
            case ALL:
            default:
                return getString(R.string.admin_managed_filter_all);
        }
    }

    private String toManagedDateFilterLabel(ManagedRequestDateFilter filter) {
        switch (filter) {
            case TODAY:
                return getString(R.string.admin_managed_date_filter_today);
            case UPCOMING:
                return getString(R.string.admin_managed_date_filter_upcoming);
            case PAST:
                return getString(R.string.admin_managed_date_filter_past);
            case ALL:
            default:
                return getString(R.string.admin_managed_date_filter_all);
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
        TextView detailButton = itemView.findViewById(R.id.textAdminRequestDetailToggle);
        TextView detailPanel = itemView.findViewById(R.id.textAdminRequestDetailPanel);
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

        boolean expanded = expandedRequestIds.contains(overview.getAppointmentRequest().getId());
        detailButton.setText(expanded
                ? R.string.admin_request_detail_hide
                : R.string.admin_request_detail_show);
        detailPanel.setVisibility(expanded ? View.VISIBLE : View.GONE);
        detailPanel.setText(buildDetailPanelText(overview));
        detailButton.setOnClickListener(view -> toggleRequestDetail(overview.getAppointmentRequest().getId()));

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

    private void toggleRequestDetail(String requestId) {
        if (expandedRequestIds.contains(requestId)) {
            expandedRequestIds.remove(requestId);
        } else {
            expandedRequestIds.add(requestId);
        }
        renderPendingRequests(pendingRequestsSnapshot, availableManagersSnapshot);
        renderManagedRequests(managedRequestsSnapshot);
    }

    private String buildDetailPanelText(AdminRequestOverview overview) {
        AppointmentStatus requestStatus = overview.getAppointmentRequest().getStatus();
        CompanionSession session = overview.getSession();
        String sessionId = session == null
                ? getString(R.string.admin_request_detail_missing)
                : session.getId();
        String sessionStatus = session == null
                ? getString(R.string.admin_request_detail_missing)
                : toSessionStatusLabel(session);
        String stepText = session == null
                ? getString(R.string.admin_request_detail_missing)
                : getString(R.string.admin_request_detail_step_value, session.getCurrentStepOrder());
        String guardianUpdate = session == null || TextUtils.isEmpty(session.getGuardianUpdate())
                ? getString(R.string.admin_request_detail_missing)
                : session.getGuardianUpdate();
        String medicationNote = session == null || TextUtils.isEmpty(session.getMedicationNote())
                ? getString(R.string.admin_request_detail_missing)
                : session.getMedicationNote();
        String patientLink = buildLinkStateText(
                overview.getAppointmentRequest().getPatientUserId(),
                overview.getAppointmentRequest().getPatientEmail()
        );
        String guardianLink = buildLinkStateText(
                overview.getAppointmentRequest().getGuardianUserId(),
                overview.getAppointmentRequest().getGuardianEmail()
        );
        String managerLink = buildLinkStateText(
                overview.getAppointmentRequest().getManagerUserId(),
                overview.getManager() == null ? "" : overview.getManager().getEmail()
        );

        return getString(
                R.string.admin_request_detail_panel,
                overview.getAppointmentRequest().getId(),
                toStatusLabel(requestStatus),
                sessionId,
                sessionStatus,
                stepText,
                patientLink,
                guardianLink,
                managerLink,
                guardianUpdate,
                medicationNote
        );
    }

    private String buildLinkStateText(String linkedUserId, String email) {
        if (!TextUtils.isEmpty(linkedUserId)) {
            if (!TextUtils.isEmpty(email)) {
                return getString(R.string.admin_request_detail_linked_email, linkedUserId, email);
            }
            return getString(R.string.admin_request_detail_linked, linkedUserId);
        }
        if (!TextUtils.isEmpty(email)) {
            return getString(R.string.admin_request_detail_pending_email, email);
        }
        return getString(R.string.admin_request_detail_missing);
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
            renderEmptyText(
                    adminGuideListContainer,
                    R.string.admin_guide_list_title,
                    R.string.admin_guide_empty
            );
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (HospitalGuide guide : guides) {
            View itemView = inflater.inflate(R.layout.item_admin_guide, adminGuideListContainer, false);
            TextView titleView = itemView.findViewById(R.id.textAdminGuideTitle);
            TextView countView = itemView.findViewById(R.id.textAdminGuideCount);
            TextView previewView = itemView.findViewById(R.id.textAdminGuidePreview);
            MaterialButton editButton = itemView.findViewById(R.id.buttonAdminGuideEdit);
            MaterialButton deleteButton = itemView.findViewById(R.id.buttonAdminGuideDelete);

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
            editButton.setOnClickListener(view -> startGuideEdit(guide));
            deleteButton.setOnClickListener(view -> confirmGuideDelete(guide));
            adminGuideListContainer.addView(itemView);
        }
    }

    private void openManagerDocumentReviewDialog(
            ManagerDocumentOverview overview,
            ManagerDocumentStatus targetStatus
    ) {
        if (currentUser == null || loading) {
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(
                R.layout.dialog_admin_document_review,
                null,
                false
        );
        TextInputLayout inputLayout = dialogView.findViewById(R.id.layoutAdminDocumentReviewNote);
        TextInputEditText inputEditText = dialogView.findViewById(R.id.inputAdminDocumentReviewNote);
        inputLayout.setHelperText(getString(
                targetStatus == ManagerDocumentStatus.APPROVED
                        ? R.string.admin_manager_document_approve_helper
                        : R.string.admin_manager_document_reject_helper
        ));
        inputEditText.setText(overview.getProfile().getDocumentReviewNote());
        if (inputEditText.getText() != null) {
            inputEditText.setSelection(inputEditText.getText().length());
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(
                        targetStatus == ManagerDocumentStatus.APPROVED
                                ? R.string.admin_manager_document_approve_dialog_title
                                : R.string.admin_manager_document_reject_dialog_title,
                        overview.getManager().getName()
                ))
                .setView(dialogView)
                .setNegativeButton(R.string.admin_manager_document_review_cancel, null)
                .setPositiveButton(R.string.admin_manager_document_review_confirm, null)
                .create();

        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String reviewNote = valueOf(inputEditText);
                    if (targetStatus == ManagerDocumentStatus.REJECTED && TextUtils.isEmpty(reviewNote)) {
                        inputLayout.setError(getString(R.string.admin_manager_document_review_note_required));
                        return;
                    }
                    inputLayout.setError(null);
                    dialog.dismiss();
                    reviewManagerDocument(overview.getManager().getId(), targetStatus, reviewNote);
                }));
        dialog.show();
    }

    private void reviewManagerDocument(
            String managerUserId,
            ManagerDocumentStatus status,
            String reviewNote
    ) {
        setLoading(true);
        adminRepository.reviewManagerDocument(
                currentUser,
                managerUserId,
                status,
                reviewNote,
                new RepositoryCallback<AdminDashboard>() {
                    @Override
                    public void onSuccess(AdminDashboard result) {
                        setLoading(false);
                        Toast.makeText(
                                AdminActivity.this,
                                status == ManagerDocumentStatus.APPROVED
                                        ? R.string.admin_manager_document_approved
                                        : R.string.admin_manager_document_rejected,
                                Toast.LENGTH_SHORT
                        ).show();
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

    private void openManagerDocumentHistoryDialog(ManagerDocumentOverview overview) {
        View dialogView = LayoutInflater.from(this).inflate(
                R.layout.dialog_admin_document_history,
                null,
                false
        );
        TextView helperView = dialogView.findViewById(R.id.textAdminDocumentHistoryHelper);
        LinearLayout container = dialogView.findViewById(R.id.adminDocumentHistoryContainer);
        helperView.setText(R.string.admin_manager_document_history_helper);
        renderManagerDocumentHistoryEntries(container, overview);

        new AlertDialog.Builder(this)
                .setTitle(getString(
                        R.string.admin_manager_document_history_dialog_title,
                        overview.getManager().getName()
                ))
                .setView(dialogView)
                .setPositiveButton(R.string.admin_manager_document_history_close, null)
                .show();
    }

    private void renderManagerDocumentHistoryEntries(
            LinearLayout container,
            ManagerDocumentOverview overview
    ) {
        container.removeAllViews();
        if (overview.getHistoryEntries().isEmpty()) {
            renderEmptyText(
                    container,
                    R.string.admin_manager_document_history,
                    R.string.admin_manager_document_history_empty
            );
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (ManagerDocumentHistoryEntry historyEntry : overview.getHistoryEntries()) {
            View itemView = inflater.inflate(
                    R.layout.item_admin_document_history,
                    container,
                    false
            );
            bindManagerDocumentHistoryItem(itemView, overview, historyEntry);
            container.addView(itemView);
        }
    }

    private void bindManagerDocumentHistoryItem(
            View itemView,
            ManagerDocumentOverview overview,
            ManagerDocumentHistoryEntry historyEntry
    ) {
        TextView badgeView = itemView.findViewById(R.id.textAdminDocumentHistoryBadge);
        TextView timestampView = itemView.findViewById(R.id.textAdminDocumentHistoryTimestamp);
        TextView actorView = itemView.findViewById(R.id.textAdminDocumentHistoryActor);
        TextView bodyView = itemView.findViewById(R.id.textAdminDocumentHistoryBody);

        bindManagerDocumentHistoryBadge(badgeView, historyEntry.getEventType());
        timestampView.setText(formatManagerDocumentTimestamp(historyEntry.getHappenedAtMillis()));

        String actorName = TextUtils.isEmpty(historyEntry.getActorName())
                ? overview.getManager().getName()
                : historyEntry.getActorName();
        actorView.setText(getString(R.string.admin_manager_document_history_actor, actorName));
        bodyView.setText(buildManagerDocumentHistoryBody(historyEntry));
    }

    private void bindManagerDocumentHistoryBadge(
            TextView textView,
            ManagerDocumentHistoryEventType eventType
    ) {
        int backgroundColor;
        int textColor;
        switch (eventType) {
            case APPROVED:
                backgroundColor = R.color.bodeul_success;
                textColor = R.color.white;
                break;
            case REJECTED:
                backgroundColor = R.color.bodeul_warning;
                textColor = R.color.bodeul_text_primary;
                break;
            case SUBMITTED:
            default:
                backgroundColor = R.color.bodeul_primary;
                textColor = R.color.white;
                break;
        }

        textView.setText(toManagerDocumentHistoryLabel(eventType));
        textView.setBackgroundTintList(ColorStateList.valueOf(getColor(backgroundColor)));
        textView.setTextColor(getColor(textColor));
    }

    private String toManagerDocumentHistoryLabel(ManagerDocumentHistoryEventType eventType) {
        switch (eventType) {
            case APPROVED:
                return getString(R.string.admin_manager_document_history_approved);
            case REJECTED:
                return getString(R.string.admin_manager_document_history_rejected);
            case SUBMITTED:
            default:
                return getString(R.string.admin_manager_document_history_submitted);
        }
    }

    private String buildManagerDocumentHistoryBody(ManagerDocumentHistoryEntry historyEntry) {
        String detail = historyEntry.getEventType() == ManagerDocumentHistoryEventType.SUBMITTED
                ? historyEntry.getSummary()
                : historyEntry.getReviewNote();
        if (TextUtils.isEmpty(detail)) {
            detail = getString(R.string.admin_manager_document_history_body_empty);
        }

        if (historyEntry.getEventType() == ManagerDocumentHistoryEventType.APPROVED) {
            return getString(R.string.admin_manager_document_history_approved_body, detail);
        }
        if (historyEntry.getEventType() == ManagerDocumentHistoryEventType.REJECTED) {
            return getString(R.string.admin_manager_document_history_rejected_body, detail);
        }
        return getString(R.string.admin_manager_document_history_submitted_body, detail);
    }

    private String buildManagerDocumentSummaryText(ManagerHomeProfile profile) {
        if (TextUtils.isEmpty(profile.getDocumentSummary())) {
            return getString(R.string.admin_manager_document_summary_empty);
        }
        return getString(R.string.admin_manager_document_summary_value, profile.getDocumentSummary());
    }

    private String buildManagerDocumentAvailabilityText(ManagerHomeProfile profile) {
        if (TextUtils.isEmpty(profile.getAvailabilitySummary())) {
            return getString(R.string.admin_manager_document_availability_empty);
        }
        return getString(
                R.string.admin_manager_document_availability_value,
                profile.getAvailabilitySummary()
        );
    }

    private String buildManagerDocumentReviewNoteText(ManagerHomeProfile profile) {
        if (TextUtils.isEmpty(profile.getDocumentReviewNote())) {
            return getString(R.string.admin_manager_document_review_note_empty);
        }
        return getString(
                R.string.admin_manager_document_review_note_value,
                profile.getDocumentReviewNote()
        );
    }

    private String buildManagerDocumentTimelineText(ManagerHomeProfile profile) {
        long submittedAtMillis = profile.getDocumentUpdatedAtMillis();
        long reviewedAtMillis = profile.getDocumentReviewedAtMillis();
        if (submittedAtMillis <= 0L
                && reviewedAtMillis <= 0L
                && TextUtils.isEmpty(profile.getDocumentReviewedByName())) {
            return getString(R.string.admin_manager_document_timeline_empty);
        }

        String reviewerName = TextUtils.isEmpty(profile.getDocumentReviewedByName())
                ? getString(R.string.admin_manager_document_timeline_none)
                : profile.getDocumentReviewedByName();
        return getString(
                R.string.admin_manager_document_timeline_value,
                formatManagerDocumentTimestamp(submittedAtMillis),
                formatManagerDocumentTimestamp(reviewedAtMillis),
                reviewerName
        );
    }

    private String formatManagerDocumentTimestamp(long timestampMillis) {
        if (timestampMillis <= 0L) {
            return getString(R.string.admin_manager_document_timeline_none);
        }
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
        return formatter.format(timestampMillis);
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

        boolean editing = editingGuide != null;
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
                        setLoading(false);
                        if (editing) {
                            Toast.makeText(AdminActivity.this, R.string.admin_guide_updated, Toast.LENGTH_SHORT).show();
                            exitGuideEditMode();
                        } else {
                            Toast.makeText(AdminActivity.this, R.string.admin_guide_saved, Toast.LENGTH_SHORT).show();
                            clearGuideForm();
                        }
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

    private void startGuideEdit(HospitalGuide guide) {
        if (loading) {
            return;
        }
        editingGuide = guide;
        inputAdminGuideHospital.setText(guide.getHospitalName());
        inputAdminGuideDepartment.setText(guide.getDepartmentName());
        inputAdminGuideSteps.setText(buildEditableGuideSteps(guide));
        clearGuideErrors();
        updateGuideFormMode();
    }

    private String buildEditableGuideSteps(HospitalGuide guide) {
        StringBuilder builder = new StringBuilder();
        for (GuideStep step : guide.getSteps()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            if (!TextUtils.isEmpty(step.getDescription())) {
                builder.append(step.getTitle()).append(": ").append(step.getDescription());
            } else {
                builder.append(step.getTitle());
            }
        }
        return builder.toString();
    }

    private void renderManagerDocuments(List<ManagerDocumentOverview> overviews) {
        adminManagerDocumentsContainer.removeAllViews();
        if (overviews.isEmpty()) {
            renderEmptyText(
                    adminManagerDocumentsContainer,
                    R.string.admin_manager_documents_title,
                    R.string.admin_manager_documents_empty
            );
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (ManagerDocumentOverview overview : overviews) {
            View itemView = inflater.inflate(
                    R.layout.item_admin_manager_document,
                    adminManagerDocumentsContainer,
                    false
            );
            bindManagerDocumentItem(itemView, overview);
            adminManagerDocumentsContainer.addView(itemView);
        }
    }

    private void bindManagerDocumentItem(View itemView, ManagerDocumentOverview overview) {
        TextView titleView = itemView.findViewById(R.id.textAdminManagerDocumentTitle);
        TextView statusView = itemView.findViewById(R.id.textAdminManagerDocumentStatus);
        TextView summaryView = itemView.findViewById(R.id.textAdminManagerDocumentSummary);
        TextView availabilityView = itemView.findViewById(R.id.textAdminManagerDocumentAvailability);
        TextView reviewNoteView = itemView.findViewById(R.id.textAdminManagerDocumentReviewNote);
        TextView timelineView = itemView.findViewById(R.id.textAdminManagerDocumentTimeline);
        View actionLayout = itemView.findViewById(R.id.layoutAdminManagerDocumentActions);
        MaterialButton historyButton = itemView.findViewById(R.id.buttonAdminManagerDocumentHistory);
        MaterialButton approveButton = itemView.findViewById(R.id.buttonAdminManagerDocumentApprove);
        MaterialButton rejectButton = itemView.findViewById(R.id.buttonAdminManagerDocumentReject);

        ManagerHomeProfile profile = overview.getProfile();
        titleView.setText(getString(
                R.string.admin_manager_document_title,
                overview.getManager().getName()
        ));
        bindManagerDocumentStatusBadge(statusView, profile.getDocumentStatus());
        summaryView.setText(buildManagerDocumentSummaryText(profile));
        availabilityView.setText(buildManagerDocumentAvailabilityText(profile));
        reviewNoteView.setText(buildManagerDocumentReviewNoteText(profile));
        timelineView.setText(buildManagerDocumentTimelineText(profile));

        boolean hasDocumentSummary = !TextUtils.isEmpty(profile.getDocumentSummary());
        actionLayout.setVisibility(hasDocumentSummary ? View.VISIBLE : View.GONE);
        approveButton.setEnabled(!loading && hasDocumentSummary);
        rejectButton.setEnabled(!loading && hasDocumentSummary);
        historyButton.setVisibility(overview.getHistoryEntries().isEmpty() ? View.GONE : View.VISIBLE);
        historyButton.setEnabled(!loading);
        approveButton.setOnClickListener(view ->
                openManagerDocumentReviewDialog(overview, ManagerDocumentStatus.APPROVED));
        rejectButton.setOnClickListener(view ->
                openManagerDocumentReviewDialog(overview, ManagerDocumentStatus.REJECTED));
        historyButton.setOnClickListener(view -> openManagerDocumentHistoryDialog(overview));
    }

    private void confirmGuideDelete(HospitalGuide guide) {
        if (currentUser == null || loading) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_guide_delete_dialog_title)
                .setMessage(getString(
                        R.string.admin_guide_delete_dialog_body,
                        guide.getHospitalName(),
                        guide.getDepartmentName()
                ))
                .setNegativeButton(R.string.admin_guide_delete_dialog_keep, null)
                .setPositiveButton(R.string.admin_guide_delete_dialog_confirm, (dialogInterface, which) ->
                        deleteGuide(guide))
                .show();
    }

    private void deleteGuide(HospitalGuide guide) {
        setLoading(true);
        adminRepository.deleteHospitalGuide(
                currentUser,
                guide.getId(),
                new RepositoryCallback<AdminDashboard>() {
                    @Override
                    public void onSuccess(AdminDashboard result) {
                        setLoading(false);
                        if (editingGuide != null && editingGuide.getId().equals(guide.getId())) {
                            exitGuideEditMode();
                        }
                        Toast.makeText(AdminActivity.this, R.string.admin_guide_deleted, Toast.LENGTH_SHORT).show();
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

    private void renderEmptyText(LinearLayout container, int titleResId, int messageResId) {
        View emptyPanel = LayoutInflater.from(this).inflate(
                R.layout.include_state_panel,
                container,
                false
        );
        StatePanelHelper.show(
                emptyPanel,
                StatePanelHelper.Tone.INFO,
                getString(R.string.state_badge_notice),
                getString(titleResId),
                getString(messageResId),
                null,
                null,
                null,
                null
        );
        container.addView(emptyPanel);
    }

    private void bindEmptyState() {
        textAdminGreeting.setText(R.string.admin_empty_greeting);
        textAdminSummary.setText(R.string.admin_empty_summary);
        textAdminManagers.setText(R.string.admin_manager_summary_empty);
        pendingRequestsSnapshot.clear();
        availableManagersSnapshot.clear();
        managedRequestsSnapshot.clear();
        textAdminManagedSummary.setText(R.string.admin_managed_summary_empty);
        expandedRequestIds.clear();
        adminManagedFilterContainer.removeAllViews();
        adminManagedFilterContainer.setVisibility(View.GONE);
        adminManagedDateFilterContainer.removeAllViews();
        adminManagedDateFilterContainer.setVisibility(View.GONE);
        adminManagerDocumentsContainer.removeAllViews();
        adminPendingRequestsContainer.removeAllViews();
        adminManagedRequestsContainer.removeAllViews();
        adminGuideListContainer.removeAllViews();
        renderEmptyText(
                adminManagerDocumentsContainer,
                R.string.admin_manager_documents_title,
                R.string.admin_manager_documents_empty
        );
        renderEmptyText(adminPendingRequestsContainer, R.string.admin_pending_title, R.string.admin_pending_empty);
        renderEmptyText(adminManagedRequestsContainer, R.string.admin_managed_title, R.string.admin_managed_empty);
        renderEmptyText(adminGuideListContainer, R.string.admin_guide_list_title, R.string.admin_guide_empty);
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

    private void bindManagerDocumentStatusBadge(TextView textView, ManagerDocumentStatus status) {
        int backgroundColor;
        int textColor;
        switch (status) {
            case APPROVED:
                backgroundColor = R.color.bodeul_success;
                textColor = R.color.white;
                break;
            case REJECTED:
                backgroundColor = R.color.bodeul_warning;
                textColor = R.color.bodeul_text_primary;
                break;
            case PENDING_REVIEW:
                backgroundColor = R.color.bodeul_primary;
                textColor = R.color.white;
                break;
            case NOT_SUBMITTED:
            default:
                backgroundColor = R.color.bodeul_surface_alt;
                textColor = R.color.bodeul_text_primary;
                break;
        }

        textView.setText(toManagerDocumentStatusLabel(status));
        textView.setBackgroundTintList(ColorStateList.valueOf(getColor(backgroundColor)));
        textView.setTextColor(getColor(textColor));
    }

    private String toManagerDocumentStatusLabel(ManagerDocumentStatus status) {
        switch (status) {
            case APPROVED:
                return getString(R.string.manager_document_status_approved);
            case REJECTED:
                return getString(R.string.manager_document_status_rejected);
            case PENDING_REVIEW:
                return getString(R.string.manager_document_status_pending_review);
            case NOT_SUBMITTED:
            default:
                return getString(R.string.manager_document_status_not_submitted);
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
            case CANCELED:
                return getString(R.string.guardian_report_session_canceled);
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

    private void exitGuideEditMode() {
        editingGuide = null;
        clearGuideForm();
        clearGuideErrors();
        updateGuideFormMode();
    }

    private void updateGuideFormMode() {
        if (editingGuide == null) {
            textAdminGuideFormTitle.setText(R.string.admin_guide_form_title);
            textAdminGuideFormBadge.setText(R.string.admin_guide_form_badge);
            textAdminGuideFormHelper.setText(R.string.admin_guide_form_helper);
            buttonSubmitAdminGuide.setText(R.string.admin_guide_submit);
            buttonCancelAdminGuideEdit.setVisibility(View.GONE);
        } else {
            textAdminGuideFormTitle.setText(R.string.admin_guide_form_edit_title);
            textAdminGuideFormBadge.setText(R.string.admin_guide_form_edit_badge);
            textAdminGuideFormHelper.setText(getString(
                    R.string.admin_guide_form_edit_helper,
                    editingGuide.getHospitalName(),
                    editingGuide.getDepartmentName()
            ));
            buttonSubmitAdminGuide.setText(R.string.admin_guide_submit_update);
            buttonCancelAdminGuideEdit.setVisibility(View.VISIBLE);
        }
        updateGuideFieldEnabledState();
    }

    private void updateGuideFieldEnabledState() {
        boolean canEditTarget = !loading && editingGuide == null;
        boolean canEditSteps = !loading;
        layoutAdminGuideHospital.setEnabled(canEditTarget);
        layoutAdminGuideDepartment.setEnabled(canEditTarget);
        layoutAdminGuideSteps.setEnabled(canEditSteps);
        inputAdminGuideHospital.setEnabled(canEditTarget);
        inputAdminGuideDepartment.setEnabled(canEditTarget);
        inputAdminGuideSteps.setEnabled(canEditSteps);
        buttonSubmitAdminGuide.setEnabled(!loading);
        buttonCancelAdminGuideEdit.setEnabled(!loading);
    }

    @Nullable
    private Calendar parseAppointmentCalendar(String appointmentAt) {
        if (TextUtils.isEmpty(appointmentAt)) {
            return null;
        }
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
        formatter.setLenient(false);
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(formatter.parse(appointmentAt));
            return calendar;
        } catch (ParseException exception) {
            return null;
        }
    }

    private void normalizeDate(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        progressAdmin.setVisibility(loading ? View.VISIBLE : View.GONE);
        updateGuideFieldEnabledState();
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.feature_admin_title)),
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
                getString(R.string.state_load_error_title, getString(R.string.feature_admin_title)),
                body,
                getString(R.string.state_action_retry),
                view -> {
                    if (currentUser == null) {
                        showAuthState();
                        return;
                    }
                    setLoading(true);
                    hideBlockingState();
                    loadDashboard();
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
                adminStatePanel,
                tone,
                badge,
                title,
                body,
                primaryText,
                primaryListener,
                secondaryText,
                secondaryListener
        );
        adminContentContainer.setVisibility(View.GONE);
    }

    private void hideBlockingState() {
        StatePanelHelper.hide(adminStatePanel);
        adminContentContainer.setVisibility(View.VISIBLE);
    }

    private String valueOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private int dpToPx(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
