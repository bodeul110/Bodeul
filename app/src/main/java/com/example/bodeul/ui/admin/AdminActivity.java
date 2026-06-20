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
import com.example.bodeul.data.ManagerDocumentPreviewResolver;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.AdminDashboard;
import com.example.bodeul.domain.model.AdminEmergencyIssueStatus;
import com.example.bodeul.domain.model.AdminRequestOverview;
import com.example.bodeul.domain.model.AdminSettlementStatus;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.ManagerDocumentStatus;
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
import java.util.List;

/**
 * 관리자용 수동 매칭과 병원 가이드 관리 화면이다.
 */
public class AdminActivity extends AppCompatActivity {
    private AuthRepository authRepository;
    private AdminRepository adminRepository;
    private ManagerDocumentPreviewResolver managerDocumentPreviewResolver;
    private User currentUser;
    private HospitalGuide editingGuide;
    @Nullable
    private AdminDashboard adminDashboardSnapshot;
    private AdminMonitoringFilter monitoringFilter = AdminMonitoringFilter.ALL;
    private AdminSettlementFilter settlementFilter = AdminSettlementFilter.ALL;
    private boolean loading;
    private AdminOperationsCoordinator adminOperationsCoordinator;
    private AdminOperationCardBinder adminOperationCardBinder;
    private AdminRequestSectionController adminRequestSectionController;
    private AdminGuideCoordinator adminGuideCoordinator;
    private AdminGuideCardBinder adminGuideCardBinder;
    private AdminGuideFormBinder adminGuideFormBinder;
    private AdminManagerDocumentSectionController adminManagerDocumentSectionController;
    private AdminSupportSectionController adminSupportSectionController;
    private AdminActionCenterSectionController adminActionCenterSectionController;
    private AdminActionDeliverySectionController adminActionDeliverySectionController;

    private TextView textAdminMode;
    private TextView textAdminGreeting;
    private TextView textAdminSummary;
    private TextView textAdminManagers;
    private TextView textAdminMonitoringSummary;
    private TextView textAdminMonitoringAlert;
    private TextView textAdminSettlementSummary;
    private TextView textAdminSettlementAlert;
    private TextView textAdminActionCenterSummary;
    private TextView textAdminActionDeliverySummary;
    private TextView textAdminGuideFormTitle;
    private TextView textAdminGuideFormBadge;
    private TextView textAdminGuideFormHelper;
    private TextView textAdminManagedSummary;
    private View adminStatePanel;
    private View adminContentContainer;
    private LinearLayout adminManagerDocumentsContainer;
    private LinearLayout adminPendingRequestsContainer;
    private LinearLayout adminMonitoringFilterContainer;
    private LinearLayout adminMonitoringContainer;
    private LinearLayout adminSettlementFilterContainer;
    private LinearLayout adminSettlementContainer;
    private LinearLayout adminActionCenterFilterContainer;
    private LinearLayout adminActionCenterContainer;
    private LinearLayout adminActionDeliveryContainer;
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
        managerDocumentPreviewResolver = ServiceLocator.provideManagerDocumentPreviewResolver(this);
        adminOperationsCoordinator = new AdminOperationsCoordinator(
                this,
                new AdminOperationsPresentationFormatter(this)
        );
        adminOperationCardBinder = new AdminOperationCardBinder(getLayoutInflater());
        adminGuideCoordinator = new AdminGuideCoordinator(new AdminGuidePresentationFormatter(this));
        adminGuideCardBinder = new AdminGuideCardBinder();

        textAdminMode = findViewById(R.id.textAdminMode);
        textAdminGreeting = findViewById(R.id.textAdminGreeting);
        textAdminSummary = findViewById(R.id.textAdminSummary);
        textAdminManagers = findViewById(R.id.textAdminManagers);
        textAdminMonitoringSummary = findViewById(R.id.textAdminMonitoringSummary);
        textAdminMonitoringAlert = findViewById(R.id.textAdminMonitoringAlert);
        textAdminSettlementSummary = findViewById(R.id.textAdminSettlementSummary);
        textAdminSettlementAlert = findViewById(R.id.textAdminSettlementAlert);
        textAdminActionCenterSummary = findViewById(R.id.textAdminActionCenterSummary);
        textAdminActionDeliverySummary = findViewById(R.id.textAdminActionDeliverySummary);
        textAdminGuideFormTitle = findViewById(R.id.textAdminGuideFormTitle);
        textAdminGuideFormBadge = findViewById(R.id.textAdminGuideFormBadge);
        textAdminGuideFormHelper = findViewById(R.id.textAdminGuideFormHelper);
        textAdminManagedSummary = findViewById(R.id.textAdminManagedSummary);
        adminStatePanel = findViewById(R.id.adminStatePanel);
        adminContentContainer = findViewById(R.id.adminContentContainer);
        adminManagerDocumentsContainer = findViewById(R.id.adminManagerDocumentsContainer);
        adminPendingRequestsContainer = findViewById(R.id.adminPendingRequestsContainer);
        adminMonitoringFilterContainer = findViewById(R.id.adminMonitoringFilterContainer);
        adminMonitoringContainer = findViewById(R.id.adminMonitoringContainer);
        adminSettlementFilterContainer = findViewById(R.id.adminSettlementFilterContainer);
        adminSettlementContainer = findViewById(R.id.adminSettlementContainer);
        adminActionCenterFilterContainer = findViewById(R.id.adminActionCenterFilterContainer);
        adminActionCenterContainer = findViewById(R.id.adminActionCenterContainer);
        adminActionDeliveryContainer = findViewById(R.id.adminActionDeliveryContainer);
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
        adminGuideFormBinder = new AdminGuideFormBinder(
                textAdminGuideFormTitle,
                textAdminGuideFormBadge,
                textAdminGuideFormHelper,
                layoutAdminGuideHospital,
                layoutAdminGuideDepartment,
                layoutAdminGuideSteps,
                inputAdminGuideHospital,
                inputAdminGuideDepartment,
                inputAdminGuideSteps,
                buttonSubmitAdminGuide,
                buttonCancelAdminGuideEdit
        );
        adminManagerDocumentSectionController = new AdminManagerDocumentSectionController(
                this,
                adminManagerDocumentsContainer,
                new AdminManagerDocumentCoordinator(
                        new AdminManagerDocumentPresentationFormatter(this)
                ),
                new AdminManagerDocumentCardBinder(),
                new AdminManagerDocumentHistoryItemBinder(),
                managerDocumentPreviewResolver,
                new AdminManagerDocumentSectionController.Listener() {
                    @Override
                    public boolean isInteractionBlocked() {
                        return currentUser == null || loading;
                    }

                    @Override
                    public void onReviewManagerDocument(
                            String managerUserId,
                            ManagerDocumentStatus status,
                            String reviewNote
                    ) {
                        reviewManagerDocument(managerUserId, status, reviewNote);
                    }

                    @Override
                    public void setLoading(boolean loading) {
                        AdminActivity.this.setLoading(loading);
                    }

                    @Override
                    public void renderEmptyText(
                            LinearLayout container,
                            int titleResId,
                            int messageResId
                    ) {
                        AdminActivity.this.renderEmptyText(container, titleResId, messageResId);
                    }
                }
        );
        adminSupportSectionController = new AdminSupportSectionController(
                this,
                findViewById(R.id.textAdminSupportSummary),
                findViewById(R.id.adminSupportSourceFilterContainer),
                findViewById(R.id.adminSupportStatusFilterContainer),
                findViewById(R.id.adminSupportContainer),
                new AdminSupportCoordinator(new AdminSupportInquiryPresentationFormatter(this)),
                new AdminSupportInquiryCardBinder(),
                new AdminSupportSectionController.Listener() {
                    @Override
                    public boolean isInteractionBlocked() {
                        return currentUser == null || loading;
                    }

                    @Override
                    public void onRespondSupportInquiry(String inquiryId, String response) {
                        respondSupportInquiry(inquiryId, response);
                    }

                    @Override
                    public void onRespondClientSupportRequest(String supportRequestId, String response) {
                        respondClientSupportRequest(supportRequestId, response);
                    }

                    @Override
                    public MaterialButton createFilterButton(String text, boolean selected) {
                        return AdminActivity.this.createOperationFilterButton(text, selected);
                    }

                    @Override
                    public void renderEmptyText(
                            LinearLayout container,
                            int titleResId,
                            int messageResId
                    ) {
                        AdminActivity.this.renderEmptyText(container, titleResId, messageResId);
                    }
                }
        );
        adminActionCenterSectionController = new AdminActionCenterSectionController(
                this,
                textAdminActionCenterSummary,
                adminActionCenterFilterContainer,
                adminActionCenterContainer,
                new AdminActionCenterCoordinator(
                        new AdminActionCenterPresentationFormatter(this)
                ),
                new AdminActionCenterEntryBinder(),
                new AdminActionCenterSectionController.Listener() {
                    @Override
                    public MaterialButton createFilterButton(String text, boolean selected) {
                        return AdminActivity.this.createOperationFilterButton(text, selected);
                    }

                    @Override
                    public void renderEmptyText(
                            LinearLayout container,
                            CharSequence title,
                            CharSequence message
                    ) {
                        AdminActivity.this.renderEmptyText(container, title, message);
                    }

                    @Override
                    public void onMarkRead(String notificationId) {
                        markActionNotificationRead(notificationId);
                    }

                    @Override
                    public void onMarkResolved(String notificationId) {
                        updateActionNotificationResolved(notificationId, true);
                    }

                    @Override
                    public void onReopen(String notificationId) {
                        updateActionNotificationResolved(notificationId, false);
                    }
                }
        );
        adminRequestSectionController = new AdminRequestSectionController(
                this,
                getLayoutInflater(),
                textAdminManagedSummary,
                adminPendingRequestsContainer,
                adminManagedFilterContainer,
                adminManagedDateFilterContainer,
                adminManagedRequestsContainer,
                new AdminRequestCoordinator(this, new AdminRequestPresentationFormatter(this)),
                new AdminRequestCardBinder(getLayoutInflater()),
                new AdminRequestSectionController.Listener() {
                    @Override
                    public void onAssignManager(String requestId, String managerUserId) {
                        assignManager(requestId, managerUserId);
                    }
                }
        );
        adminActionDeliverySectionController = new AdminActionDeliverySectionController(
                getLayoutInflater(),
                textAdminActionDeliverySummary,
                adminActionDeliveryContainer,
                new AdminActionDeliveryCoordinator(
                        new AdminActionDeliveryPresentationFormatter(this)
                ),
                new AdminActionDeliveryCardBinder(),
                new AdminActionDeliverySectionController.Listener() {
                    @Override
                    public void renderEmptyText(
                            LinearLayout container,
                            int titleResId,
                            int messageResId
                    ) {
                        AdminActivity.this.renderEmptyText(container, titleResId, messageResId);
                    }
                }
        );

        textAdminMode.setText(adminRepository.isFirebaseBacked()
                ? R.string.admin_mode_firebase
                : R.string.admin_mode_demo);

        findViewById(R.id.buttonBackAdmin).setOnClickListener(view -> signOut());
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
        adminDashboardSnapshot = dashboard;
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

        adminManagerDocumentSectionController.bindDocuments(
                dashboard.getManagerDocumentOverviews(),
                loading
        );
        adminRequestSectionController.bindRequests(
                dashboard.getPendingRequests(),
                dashboard.getManagedRequests(),
                dashboard.getAvailableManagers()
        );
        renderOperations(dashboard);
        adminSupportSectionController.bindSupportInquiries(
                dashboard.getSupportInquiries(),
                dashboard.getClientSupportRequests()
        );
        adminActionCenterSectionController.bind(dashboard);
        adminActionDeliverySectionController.bind(dashboard);
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

    private void renderOperations(AdminDashboard dashboard) {
        AdminOperationsDashboardModel operationsModel =
                adminOperationsCoordinator.createDashboardModel(
                        dashboard,
                        monitoringFilter,
                        settlementFilter
                );
        textAdminMonitoringSummary.setText(operationsModel.getMonitoringSummaryText());
        bindOperationAlert(
                textAdminMonitoringAlert,
                operationsModel.getMonitoringAlertText()
        );
        renderMonitoringFilters(operationsModel);
        textAdminSettlementSummary.setText(operationsModel.getSettlementSummaryText());
        bindOperationAlert(
                textAdminSettlementAlert,
                operationsModel.getSettlementAlertText()
        );
        renderSettlementFilters(operationsModel);
        renderOperationCards(
                adminMonitoringContainer,
                operationsModel.getMonitoringCards(),
                R.string.admin_monitoring_title,
                R.string.admin_monitoring_empty
        );
        renderOperationCards(
                adminSettlementContainer,
                operationsModel.getSettlementCards(),
                R.string.admin_settlement_title,
                R.string.admin_settlement_empty
        );
    }

    private void bindOperationAlert(TextView alertView, String alertText) {
        if (TextUtils.isEmpty(alertText)) {
            alertView.setVisibility(View.GONE);
            return;
        }
        alertView.setVisibility(View.VISIBLE);
        alertView.setText(alertText);
    }

    private void renderMonitoringFilters(AdminOperationsDashboardModel operationsModel) {
        adminMonitoringFilterContainer.removeAllViews();
        if (!operationsModel.hasMonitoringTargets()) {
            adminMonitoringFilterContainer.setVisibility(View.GONE);
            return;
        }

        adminMonitoringFilterContainer.setVisibility(View.VISIBLE);
        for (AdminMonitoringFilterChipModel chipModel : operationsModel.getMonitoringFilterChips()) {
            MaterialButton button = createOperationFilterButton(chipModel.getButtonText(), chipModel.isSelected());
            button.setOnClickListener(view -> {
                monitoringFilter = chipModel.getFilter();
                if (adminDashboardSnapshot != null) {
                    renderOperations(adminDashboardSnapshot);
                }
            });
            adminMonitoringFilterContainer.addView(button);
        }
    }

    private void renderSettlementFilters(AdminOperationsDashboardModel operationsModel) {
        adminSettlementFilterContainer.removeAllViews();
        if (!operationsModel.hasSettlementTargets()) {
            adminSettlementFilterContainer.setVisibility(View.GONE);
            return;
        }

        adminSettlementFilterContainer.setVisibility(View.VISIBLE);
        for (AdminSettlementFilterChipModel chipModel : operationsModel.getSettlementFilterChips()) {
            MaterialButton button = createOperationFilterButton(chipModel.getButtonText(), chipModel.isSelected());
            button.setOnClickListener(view -> {
                settlementFilter = chipModel.getFilter();
                if (adminDashboardSnapshot != null) {
                    renderOperations(adminDashboardSnapshot);
                }
            });
            adminSettlementFilterContainer.addView(button);
        }
    }

    private void renderOperationCards(
            LinearLayout container,
            List<AdminOperationCardModel> cardModels,
            int titleResId,
            int emptyResId
    ) {
        container.removeAllViews();
        if (cardModels.isEmpty()) {
            renderEmptyText(container, titleResId, emptyResId);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (AdminOperationCardModel cardModel : cardModels) {
            View itemView = inflater.inflate(R.layout.item_admin_operation_card, container, false);
            adminOperationCardBinder.bind(itemView, cardModel, (requestId, actionType) -> {
                if (actionType == AdminOperationActionType.SAVE_SETTLEMENT_CONFIRMED) {
                    openSettlementActionDialog(requestId, AdminSettlementStatus.CONFIRMED);
                    return;
                }
                if (actionType == AdminOperationActionType.SAVE_SETTLEMENT_RECHECK) {
                    openSettlementActionDialog(requestId, AdminSettlementStatus.NEEDS_REVIEW);
                    return;
                }
                if (actionType == AdminOperationActionType.RESOLVE_EMERGENCY) {
                    openEmergencyActionDialog(requestId, AdminEmergencyIssueStatus.RESOLVED);
                    return;
                }
                openEmergencyActionDialog(requestId, AdminEmergencyIssueStatus.REPORTED);
            });
            container.addView(itemView);
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

    private MaterialButton createOperationFilterButton(String buttonText, boolean selected) {
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
        button.setText(buttonText);
        bindManagedFilterButtonStyle(button, selected);
        return button;
    }

    private void renderGuides(List<HospitalGuide> guides) {
        adminGuideListContainer.removeAllViews();
        List<AdminGuideCardModel> guideCards = adminGuideCoordinator.createGuideCards(guides);
        if (guideCards.isEmpty()) {
            renderEmptyText(
                    adminGuideListContainer,
                    R.string.admin_guide_list_title,
                    R.string.admin_guide_empty
            );
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (AdminGuideCardModel guideCard : guideCards) {
            View itemView = inflater.inflate(R.layout.item_admin_guide, adminGuideListContainer, false);
            adminGuideCardBinder.bind(itemView, guideCard, new AdminGuideCardBinder.Listener() {
                @Override
                public void onEditGuide(String guideId) {
                    startGuideEdit(findGuideById(guideId, guides));
                }

                @Override
                public void onDeleteGuide(String guideId) {
                    confirmGuideDelete(findGuideById(guideId, guides));
                }
            });
            adminGuideListContainer.addView(itemView);
        }
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

    private void openSettlementActionDialog(
            String requestId,
            AdminSettlementStatus status
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
        inputLayout.setHint(getString(R.string.admin_operation_note_hint));
        inputLayout.setHelperText(getString(
                status == AdminSettlementStatus.CONFIRMED
                        ? R.string.admin_settlement_action_confirm_helper
                        : R.string.admin_settlement_action_recheck_helper
        ));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(status == AdminSettlementStatus.CONFIRMED
                        ? R.string.admin_settlement_action_confirm
                        : R.string.admin_settlement_action_recheck)
                .setView(dialogView)
                .setNegativeButton(R.string.admin_manager_document_review_cancel, null)
                .setPositiveButton(R.string.admin_manager_document_review_confirm, null)
                .create();

        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String note = valueOf(inputEditText);
                    if (status == AdminSettlementStatus.NEEDS_REVIEW && TextUtils.isEmpty(note)) {
                        inputLayout.setError(getString(R.string.admin_operation_note_required));
                        return;
                    }
                    inputLayout.setError(null);
                    dialog.dismiss();
                    saveSettlementRecord(requestId, status, note);
                }));
        dialog.show();
    }

    private void saveSettlementRecord(
            String requestId,
            AdminSettlementStatus status,
            String note
    ) {
        setLoading(true);
        adminRepository.saveSettlementRecord(
                currentUser,
                requestId,
                status,
                note,
                new RepositoryCallback<AdminDashboard>() {
                    @Override
                    public void onSuccess(AdminDashboard result) {
                        setLoading(false);
                        Toast.makeText(
                                AdminActivity.this,
                                status == AdminSettlementStatus.CONFIRMED
                                        ? R.string.admin_settlement_action_saved_confirm
                                        : R.string.admin_settlement_action_saved_recheck,
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

    private void openEmergencyActionDialog(
            String requestId,
            AdminEmergencyIssueStatus status
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
        inputLayout.setHint(getString(R.string.admin_operation_note_hint));
        inputLayout.setHelperText(getString(
                status == AdminEmergencyIssueStatus.REPORTED
                        ? R.string.admin_emergency_action_report_helper
                        : R.string.admin_emergency_action_resolve_helper
        ));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(status == AdminEmergencyIssueStatus.REPORTED
                        ? R.string.admin_emergency_action_report
                        : R.string.admin_emergency_action_resolve)
                .setView(dialogView)
                .setNegativeButton(R.string.admin_manager_document_review_cancel, null)
                .setPositiveButton(R.string.admin_manager_document_review_confirm, null)
                .create();

        dialog.setOnShowListener(dialogInterface -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String note = valueOf(inputEditText);
                    if (TextUtils.isEmpty(note)) {
                        inputLayout.setError(getString(R.string.admin_operation_note_required));
                        return;
                    }
                    inputLayout.setError(null);
                    dialog.dismiss();
                    saveEmergencyIssue(requestId, status, note);
                }));
        dialog.show();
    }

    private void saveEmergencyIssue(
            String requestId,
            AdminEmergencyIssueStatus status,
            String note
    ) {
        setLoading(true);
        adminRepository.saveEmergencyIssue(
                currentUser,
                requestId,
                status,
                note,
                new RepositoryCallback<AdminDashboard>() {
                    @Override
                    public void onSuccess(AdminDashboard result) {
                        setLoading(false);
                        Toast.makeText(
                                AdminActivity.this,
                                status == AdminEmergencyIssueStatus.REPORTED
                                        ? R.string.admin_emergency_action_saved_report
                                        : R.string.admin_emergency_action_saved_resolve,
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

    private void respondSupportInquiry(String inquiryId, String response) {
        setLoading(true);
        adminRepository.respondSupportInquiry(
                currentUser,
                inquiryId,
                response,
                new RepositoryCallback<AdminDashboard>() {
                    @Override
                    public void onSuccess(AdminDashboard result) {
                        setLoading(false);
                        Toast.makeText(
                                AdminActivity.this,
                                R.string.admin_support_response_saved,
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

    private void respondClientSupportRequest(String supportRequestId, String response) {
        setLoading(true);
        adminRepository.respondClientSupportRequest(
                currentUser,
                supportRequestId,
                response,
                new RepositoryCallback<AdminDashboard>() {
                    @Override
                    public void onSuccess(AdminDashboard result) {
                        setLoading(false);
                        Toast.makeText(
                                AdminActivity.this,
                                R.string.admin_support_response_saved,
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
        if (loading || guide == null) {
            return;
        }
        editingGuide = guide;
        inputAdminGuideHospital.setText(guide.getHospitalName());
        inputAdminGuideDepartment.setText(guide.getDepartmentName());
        inputAdminGuideSteps.setText(adminGuideCoordinator.buildEditableGuideSteps(guide));
        clearGuideErrors();
        updateGuideFormMode();
    }

    private void confirmGuideDelete(HospitalGuide guide) {
        if (currentUser == null || loading || guide == null) {
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

    @Nullable
    private HospitalGuide findGuideById(String guideId, List<HospitalGuide> guides) {
        for (HospitalGuide guide : guides) {
            if (guide.getId().equals(guideId)) {
                return guide;
            }
        }
        return null;
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

    private void markActionNotificationRead(String notificationId) {
        if (currentUser == null || loading || TextUtils.isEmpty(notificationId)) {
            return;
        }
        setLoading(true);
        adminRepository.markActionNotificationRead(
                currentUser,
                notificationId,
                new RepositoryCallback<AdminDashboard>() {
                    @Override
                    public void onSuccess(AdminDashboard result) {
                        setLoading(false);
                        Toast.makeText(
                                AdminActivity.this,
                                R.string.admin_action_center_mark_read_saved,
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

    private void updateActionNotificationResolved(String notificationId, boolean resolved) {
        if (currentUser == null || loading || TextUtils.isEmpty(notificationId)) {
            return;
        }
        setLoading(true);
        adminRepository.updateActionNotificationResolved(
                currentUser,
                notificationId,
                resolved,
                new RepositoryCallback<AdminDashboard>() {
                    @Override
                    public void onSuccess(AdminDashboard result) {
                        setLoading(false);
                        Toast.makeText(
                                AdminActivity.this,
                                resolved
                                        ? R.string.admin_action_center_resolved_saved
                                        : R.string.admin_action_center_reopened_saved,
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
        renderEmptyText(container, getString(titleResId), getString(messageResId));
    }

    private void renderEmptyText(
            LinearLayout container,
            CharSequence title,
            CharSequence message
    ) {
        View emptyPanel = LayoutInflater.from(this).inflate(
                R.layout.include_state_panel,
                container,
                false
        );
        StatePanelHelper.show(
                emptyPanel,
                StatePanelHelper.Tone.INFO,
                getString(R.string.state_badge_notice),
                title,
                message,
                null,
                null,
                null,
                null
        );
        container.addView(emptyPanel);
    }

    private void bindEmptyState() {
        adminDashboardSnapshot = null;
        monitoringFilter = AdminMonitoringFilter.ALL;
        settlementFilter = AdminSettlementFilter.ALL;
        textAdminGreeting.setText(R.string.admin_empty_greeting);
        textAdminSummary.setText(R.string.admin_empty_summary);
        textAdminManagers.setText(R.string.admin_manager_summary_empty);
        textAdminMonitoringSummary.setText(R.string.admin_monitoring_summary_empty);
        textAdminMonitoringAlert.setVisibility(View.GONE);
        textAdminSettlementSummary.setText(R.string.admin_settlement_summary_empty);
        textAdminSettlementAlert.setVisibility(View.GONE);
        adminSupportSectionController.clear();
        adminMonitoringFilterContainer.removeAllViews();
        adminMonitoringFilterContainer.setVisibility(View.GONE);
        adminSettlementFilterContainer.removeAllViews();
        adminSettlementFilterContainer.setVisibility(View.GONE);
        adminManagerDocumentSectionController.showEmptyPanel();
        adminMonitoringContainer.removeAllViews();
        adminSettlementContainer.removeAllViews();
        adminGuideListContainer.removeAllViews();
        adminRequestSectionController.clear();
        renderEmptyText(adminMonitoringContainer, R.string.admin_monitoring_title, R.string.admin_monitoring_empty);
        renderEmptyText(adminSettlementContainer, R.string.admin_settlement_title, R.string.admin_settlement_empty);
        adminSupportSectionController.showEmptyPanel();
        adminActionCenterSectionController.showEmptyPanel();
        adminActionDeliverySectionController.clear();
        renderEmptyText(adminGuideListContainer, R.string.admin_guide_list_title, R.string.admin_guide_empty);
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
        adminGuideFormBinder.bind(
                adminGuideCoordinator.createGuideFormModel(editingGuide, loading)
        );
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        progressAdmin.setVisibility(loading ? View.VISIBLE : View.GONE);
        updateGuideFormMode();
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
                getString(R.string.state_action_open_login),
                view -> signOut()
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

    private void signOut() {
        authRepository.signOut();
        openRoleSelection();
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
