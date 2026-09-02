package com.example.bodeul.ui.admin;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AdminDashboard;
import com.example.bodeul.domain.model.AdminRequestActionOverview;
import com.example.bodeul.domain.model.AdminRequestOverview;
import com.example.bodeul.domain.model.AdminSettlementRecord;
import com.example.bodeul.domain.model.AdminSettlementStatus;
import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentFollowUpSettlementStatus;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.BookingPaymentMethod;
import com.example.bodeul.domain.model.BookingPaymentStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.util.CompanionLocationDisplayHelper;
import com.example.bodeul.domain.model.SessionStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자 운영 대시보드에서 모니터링/정산 카드와 필터 상태를 조합한다.
 */
public final class AdminOperationsCoordinator {
    private final Context context;
    private final AdminOperationsPresentationFormatter formatter;

    public AdminOperationsCoordinator(Context context, AdminOperationsPresentationFormatter formatter) {
        this.context = context.getApplicationContext();
        this.formatter = formatter;
    }

    public AdminOperationsDashboardModel createDashboardModel(
            AdminDashboard dashboard,
            AdminMonitoringFilter selectedMonitoringFilter,
            AdminSettlementFilter selectedSettlementFilter
    ) {
        Map<String, AdminRequestActionOverview> actionsByRequestId = mapActionsByRequestId(
                dashboard.getRequestActionOverviews()
        );
        List<AdminRequestOverview> monitoringTargets = collectMonitoringTargets(
                dashboard.getManagedRequests(),
                actionsByRequestId
        );
        List<AdminRequestOverview> settlementTargets = collectSettlementTargets(
                dashboard.getManagedRequests(),
                actionsByRequestId
        );
        List<AdminRequestOverview> filteredMonitoringTargets = filterMonitoringTargets(
                monitoringTargets,
                actionsByRequestId,
                selectedMonitoringFilter
        );
        List<AdminRequestOverview> filteredSettlementTargets = filterSettlementTargets(
                settlementTargets,
                actionsByRequestId,
                selectedSettlementFilter
        );

        return new AdminOperationsDashboardModel(
                buildMonitoringSummary(monitoringTargets),
                formatter.buildMonitoringAlertSummary(
                        monitoringTargets.size(),
                        countMonitoringTargets(monitoringTargets, actionsByRequestId, AdminMonitoringFilter.PAYMENT),
                        countMonitoringTargets(monitoringTargets, actionsByRequestId, AdminMonitoringFilter.MATCHED),
                        countMonitoringTargets(monitoringTargets, actionsByRequestId, AdminMonitoringFilter.IN_PROGRESS),
                        selectedMonitoringFilter,
                        filteredMonitoringTargets.size()
                ),
                createMonitoringFilterChips(monitoringTargets, actionsByRequestId, selectedMonitoringFilter),
                buildMonitoringCards(filteredMonitoringTargets, actionsByRequestId),
                !monitoringTargets.isEmpty(),
                buildSettlementSummary(settlementTargets),
                formatter.buildSettlementAlertSummary(
                        settlementTargets.size(),
                        countSettlementTargets(settlementTargets, actionsByRequestId, AdminSettlementFilter.USER_HELP),
                        countSettlementTargets(settlementTargets, actionsByRequestId, AdminSettlementFilter.ADMIN_PENDING),
                        countSettlementTargets(settlementTargets, actionsByRequestId, AdminSettlementFilter.CONFIRMED),
                        selectedSettlementFilter,
                        filteredSettlementTargets.size()
                ),
                createSettlementFilterChips(settlementTargets, actionsByRequestId, selectedSettlementFilter),
                buildSettlementCards(filteredSettlementTargets, actionsByRequestId),
                !settlementTargets.isEmpty()
        );
    }

    private Map<String, AdminRequestActionOverview> mapActionsByRequestId(
            List<AdminRequestActionOverview> requestActionOverviews
    ) {
        Map<String, AdminRequestActionOverview> actionsByRequestId = new HashMap<>();
        for (AdminRequestActionOverview overview : requestActionOverviews) {
            actionsByRequestId.put(overview.getRequestId(), overview);
        }
        return actionsByRequestId;
    }

    private List<AdminRequestOverview> collectMonitoringTargets(
            List<AdminRequestOverview> requests,
            Map<String, AdminRequestActionOverview> actionsByRequestId
    ) {
        List<AdminRequestOverview> targets = new ArrayList<>();
        for (AdminRequestOverview overview : requests) {
            AppointmentStatus status = overview.getAppointmentRequest().getStatus();
            if (status == AppointmentStatus.MATCHED || status == AppointmentStatus.IN_PROGRESS) {
                targets.add(overview);
            }
        }
        targets.sort((left, right) -> {
            int priorityCompare = Integer.compare(
                    resolveMonitoringPriority(left),
                    resolveMonitoringPriority(right)
            );
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return right.getAppointmentRequest().getAppointmentAt().compareTo(
                    left.getAppointmentRequest().getAppointmentAt()
            );
        });
        return targets;
    }

    private List<AdminRequestOverview> collectSettlementTargets(
            List<AdminRequestOverview> requests,
            Map<String, AdminRequestActionOverview> actionsByRequestId
    ) {
        List<AdminRequestOverview> targets = new ArrayList<>();
        for (AdminRequestOverview overview : requests) {
            if (shouldShowSettlement(overview)) {
                targets.add(overview);
            }
        }
        targets.sort((left, right) -> {
            int priorityCompare = Integer.compare(
                    resolveSettlementPriority(
                            left,
                            findSettlementRecord(actionsByRequestId, left),
                            findFollowUpRecord(actionsByRequestId, left)
                    ),
                    resolveSettlementPriority(
                            right,
                            findSettlementRecord(actionsByRequestId, right),
                            findFollowUpRecord(actionsByRequestId, right)
                    )
            );
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return right.getAppointmentRequest().getAppointmentAt().compareTo(
                    left.getAppointmentRequest().getAppointmentAt()
            );
        });
        return targets;
    }

    private List<AdminRequestOverview> filterMonitoringTargets(
            List<AdminRequestOverview> targets,
            Map<String, AdminRequestActionOverview> actionsByRequestId,
            AdminMonitoringFilter filter
    ) {
        List<AdminRequestOverview> filteredTargets = new ArrayList<>();
        for (AdminRequestOverview overview : targets) {
            if (matchesMonitoringFilter(
                    overview,
                    filter
            )) {
                filteredTargets.add(overview);
            }
        }
        return filteredTargets;
    }

    private List<AdminRequestOverview> filterSettlementTargets(
            List<AdminRequestOverview> targets,
            Map<String, AdminRequestActionOverview> actionsByRequestId,
            AdminSettlementFilter filter
    ) {
        List<AdminRequestOverview> filteredTargets = new ArrayList<>();
        for (AdminRequestOverview overview : targets) {
            if (matchesSettlementFilter(
                    findSettlementRecord(actionsByRequestId, overview),
                    findFollowUpRecord(actionsByRequestId, overview),
                    filter
            )) {
                filteredTargets.add(overview);
            }
        }
        return filteredTargets;
    }

    private List<AdminMonitoringFilterChipModel> createMonitoringFilterChips(
            List<AdminRequestOverview> targets,
            Map<String, AdminRequestActionOverview> actionsByRequestId,
            AdminMonitoringFilter selectedFilter
    ) {
        List<AdminMonitoringFilterChipModel> chips = new ArrayList<>();
        for (AdminMonitoringFilter filter : AdminMonitoringFilter.values()) {
            chips.add(new AdminMonitoringFilterChipModel(
                    filter,
                    context.getString(
                            R.string.admin_operation_filter_button,
                            formatter.toMonitoringFilterLabel(filter),
                            countMonitoringTargets(targets, actionsByRequestId, filter)
                    ),
                    filter == selectedFilter
            ));
        }
        return chips;
    }

    private List<AdminSettlementFilterChipModel> createSettlementFilterChips(
            List<AdminRequestOverview> targets,
            Map<String, AdminRequestActionOverview> actionsByRequestId,
            AdminSettlementFilter selectedFilter
    ) {
        List<AdminSettlementFilterChipModel> chips = new ArrayList<>();
        for (AdminSettlementFilter filter : AdminSettlementFilter.values()) {
            chips.add(new AdminSettlementFilterChipModel(
                    filter,
                    context.getString(
                            R.string.admin_operation_filter_button,
                            formatter.toSettlementFilterLabel(filter),
                            countSettlementTargets(targets, actionsByRequestId, filter)
                    ),
                    filter == selectedFilter
            ));
        }
        return chips;
    }

    private int countMonitoringTargets(
            List<AdminRequestOverview> targets,
            Map<String, AdminRequestActionOverview> actionsByRequestId,
            AdminMonitoringFilter filter
    ) {
        int count = 0;
        for (AdminRequestOverview overview : targets) {
            if (matchesMonitoringFilter(
                    overview,
                    filter
            )) {
                count++;
            }
        }
        return count;
    }

    private int countSettlementTargets(
            List<AdminRequestOverview> targets,
            Map<String, AdminRequestActionOverview> actionsByRequestId,
            AdminSettlementFilter filter
    ) {
        int count = 0;
        for (AdminRequestOverview overview : targets) {
            if (matchesSettlementFilter(
                    findSettlementRecord(actionsByRequestId, overview),
                    findFollowUpRecord(actionsByRequestId, overview),
                    filter
            )) {
                count++;
            }
        }
        return count;
    }

    private List<AdminOperationCardModel> buildMonitoringCards(
            List<AdminRequestOverview> targets,
            Map<String, AdminRequestActionOverview> actionsByRequestId
    ) {
        List<AdminOperationCardModel> cards = new ArrayList<>();
        for (AdminRequestOverview overview : targets) {
            AppointmentRequest request = overview.getAppointmentRequest();
            CompanionSession session = overview.getSession();
            List<AdminOperationLineItem> items = new ArrayList<>();
            items.add(new AdminOperationLineItem(
                    context.getString(R.string.admin_monitoring_line_manager),
                    formatter.formatManagerName(overview.getManager()),
                    true
            ));
            items.add(new AdminOperationLineItem(
                    context.getString(R.string.admin_monitoring_line_step),
                    buildStepValue(session),
                    false
            ));
            items.add(new AdminOperationLineItem(
                    context.getString(R.string.admin_monitoring_line_guardian_update),
                    session == null ? formatter.formatFallbackValue(null)
                            : formatter.formatFallbackValue(session.getGuardianUpdate()),
                    false
            ));
            items.add(new AdminOperationLineItem(
                    context.getString(R.string.admin_monitoring_line_location),
                    session == null ? formatter.formatFallbackValue(null)
                            : formatter.formatFallbackValue(session.getLocationSummary()),
                    false
            ));
            items.add(new AdminOperationLineItem(
                    context.getString(R.string.admin_monitoring_line_live_status),
                    CompanionLocationDisplayHelper.buildLiveSharingStatus(context, session),
                    false
            ));
            items.add(new AdminOperationLineItem(
                    context.getString(R.string.admin_monitoring_line_location_history),
                    CompanionLocationDisplayHelper.buildLocationHistory(context, session, 2),
                    false
            ));
            items.add(new AdminOperationLineItem(
                    context.getString(R.string.admin_monitoring_line_field_photo),
                    session == null ? formatter.formatFallbackValue(null)
                            : formatter.formatFallbackValue(session.getFieldPhotoNote()),
                    false
            ));
            items.add(new AdminOperationLineItem(
                    context.getString(R.string.admin_monitoring_line_medication),
                    session == null ? formatter.formatFallbackValue(null)
                            : formatter.formatFallbackValue(session.getMedicationNote()),
                    false
            ));
            cards.add(new AdminOperationCardModel(
                    request.getId(),
                    buildMonitoringStatusBadge(overview),
                    buildMonitoringPriorityBadge(overview),
                    context.getString(R.string.admin_operation_card_title, buildPatientName(overview)),
                    context.getString(
                            R.string.admin_operation_card_subtitle,
                            request.getHospitalName(),
                            request.getDepartmentName(),
                            request.getAppointmentAt()
                    ),
                    buildMonitoringCardSummary(overview),
                    "",
                    items,
                    new ArrayList<>()
            ));
        }
        return cards;
    }

    private List<AdminOperationCardModel> buildSettlementCards(
            List<AdminRequestOverview> targets,
            Map<String, AdminRequestActionOverview> actionsByRequestId
    ) {
        List<AdminOperationCardModel> cards = new ArrayList<>();
        for (AdminRequestOverview overview : targets) {
            AppointmentRequest request = overview.getAppointmentRequest();
            SessionReport report = overview.getSessionReport();
            AdminSettlementRecord settlementRecord = findSettlementRecord(actionsByRequestId, overview);
            AppointmentFollowUpRecord followUpRecord = findFollowUpRecord(actionsByRequestId, overview);
            List<AdminOperationLineItem> items = new ArrayList<>();
            items.add(new AdminOperationLineItem(
                    context.getString(R.string.admin_settlement_line_manager),
                    formatter.formatManagerName(overview.getManager()),
                    false
            ));
            items.add(new AdminOperationLineItem(
                    context.getString(R.string.admin_settlement_line_amount),
                    formatter.formatPrice(request.getFinalPrice()),
                    true
            ));
            items.add(new AdminOperationLineItem(
                    context.getString(R.string.admin_settlement_line_method),
                    formatter.formatPaymentMethod(request.getPaymentMethodCode()),
                    false
            ));
            items.add(new AdminOperationLineItem(
                    context.getString(R.string.admin_settlement_line_status),
                    formatter.formatSettlementStatus(request),
                    false
            ));
            items.add(new AdminOperationLineItem(
                    context.getString(R.string.admin_settlement_line_admin_status),
                    formatter.formatSettlementRecordStatus(
                            settlementRecord == null ? null : settlementRecord.getStatus()
                    ),
                    false
            ));
            addOptionalLine(items, R.string.admin_settlement_line_approval, request.getPaymentApprovalCode());
            addOptionalLine(items, R.string.admin_settlement_line_approved_at, request.getPaymentApprovedAt());
            addOptionalLine(
                    items,
                    R.string.admin_settlement_line_admin_note,
                    settlementRecord == null ? "" : settlementRecord.getNote()
            );
            addOptionalLine(
                    items,
                    R.string.admin_settlement_line_admin_handled,
                    settlementRecord == null
                            ? ""
                            : context.getString(
                                    R.string.admin_operation_action_handled_value,
                                    formatter.formatFallbackValue(settlementRecord.getHandledByName()),
                                    formatter.formatTimestamp(settlementRecord.getHandledAtMillis())
                            )
            );
            if (followUpRecord != null && followUpRecord.hasSavedReview()) {
                items.add(new AdminOperationLineItem(
                        context.getString(R.string.admin_settlement_line_follow_up_review),
                        context.getString(
                                R.string.admin_follow_up_value_format,
                                formatter.formatFollowUpReview(followUpRecord.getReviewRating()),
                                formatter.formatTimestamp(followUpRecord.getReviewSavedAtMillis())
                        ),
                        false
                ));
            }
            if (followUpRecord != null && followUpRecord.hasSavedSettlement()) {
                items.add(new AdminOperationLineItem(
                        context.getString(R.string.admin_settlement_line_follow_up_settlement),
                        context.getString(
                                R.string.admin_follow_up_value_format,
                                formatter.formatFollowUpSettlementStatus(followUpRecord.getSettlementStatus()),
                                formatter.formatTimestamp(followUpRecord.getSettlementSavedAtMillis())
                        ),
                        false
                ));
                addOptionalLine(
                        items,
                        R.string.admin_settlement_line_follow_up_note,
                        followUpRecord.getSettlementNote()
                );
            }
            if (report != null && !TextUtils.isEmpty(report.getNextVisitAt())) {
                items.add(new AdminOperationLineItem(
                        context.getString(R.string.admin_settlement_line_next_visit),
                        report.getNextVisitAt(),
                        false
                ));
            }
            if (report != null && !TextUtils.isEmpty(report.getSummary())) {
                items.add(new AdminOperationLineItem(
                        context.getString(R.string.admin_settlement_line_report),
                        report.getSummary(),
                        false
                ));
            }

            cards.add(new AdminOperationCardModel(
                    request.getId(),
                    buildSettlementStatusBadge(request),
                    buildSettlementPriorityBadge(settlementRecord, followUpRecord),
                    context.getString(R.string.admin_operation_card_title, buildPatientName(overview)),
                    context.getString(
                            R.string.admin_operation_card_subtitle,
                            request.getHospitalName(),
                            request.getDepartmentName(),
                            request.getAppointmentAt()
                    ),
                    buildSettlementCardSummary(request, settlementRecord, followUpRecord),
                    formatter.buildSettlementActivityText(followUpRecord, settlementRecord),
                    items,
                    buildSettlementActions(request, settlementRecord)
            ));
        }
        return cards;
    }

    private String buildMonitoringSummary(List<AdminRequestOverview> targets) {
        if (targets.isEmpty()) {
            return context.getString(R.string.admin_monitoring_summary_empty);
        }

        int matchedCount = 0;
        int serviceCount = 0;
        int paymentCount = 0;
        for (AdminRequestOverview overview : targets) {
            AppointmentStatus status = overview.getAppointmentRequest().getStatus();
            if (status == AppointmentStatus.MATCHED) {
                matchedCount++;
            }
            if (status == AppointmentStatus.IN_PROGRESS) {
                serviceCount++;
            }
            if (overview.getSession() != null
                    && overview.getSession().getStatus() == SessionStatus.PAYMENT) {
                paymentCount++;
            }
        }
        return context.getString(
                R.string.admin_monitoring_summary,
                targets.size(),
                matchedCount,
                serviceCount,
                paymentCount
        );
    }

    private String buildSettlementSummary(List<AdminRequestOverview> targets) {
        if (targets.isEmpty()) {
            return context.getString(R.string.admin_settlement_summary_empty);
        }

        int pendingCount = 0;
        int onSiteCount = 0;
        int completedCount = 0;
        for (AdminRequestOverview overview : targets) {
            AppointmentRequest request = overview.getAppointmentRequest();
            BookingPaymentMethod paymentMethod = BookingPaymentMethod.fromValue(request.getPaymentMethodCode());
            BookingPaymentStatus paymentStatus = BookingPaymentStatus.fromValue(request.getPaymentStatusCode());
            if (paymentMethod == BookingPaymentMethod.ON_SITE) {
                onSiteCount++;
            } else if (isCompletedPaymentStatus(paymentStatus)) {
                completedCount++;
            } else {
                pendingCount++;
            }
        }
        return context.getString(
                R.string.admin_settlement_summary,
                targets.size(),
                pendingCount,
                onSiteCount,
                completedCount
        );
    }

    private boolean shouldShowSettlement(AdminRequestOverview overview) {
        AppointmentRequest request = overview.getAppointmentRequest();
        BookingPaymentStatus paymentStatus = BookingPaymentStatus.fromValue(request.getPaymentStatusCode());
        if (isServerManagedPaymentStatus(paymentStatus)) {
            return true;
        }
        if (request.getStatus() == AppointmentStatus.CANCELED) {
            return false;
        }
        if (request.getStatus() == AppointmentStatus.COMPLETED) {
            return true;
        }
        if (overview.getSession() != null && overview.getSession().getStatus() == SessionStatus.PAYMENT) {
            return true;
        }
        if (!TextUtils.isEmpty(request.getPaymentApprovalCode())) {
            return true;
        }
        if (BookingPaymentMethod.fromValue(request.getPaymentMethodCode()) == BookingPaymentMethod.ON_SITE) {
            return true;
        }
        return paymentStatus == BookingPaymentStatus.DEFERRED
                || paymentStatus == BookingPaymentStatus.UNKNOWN;
    }

    private boolean matchesMonitoringFilter(
            AdminRequestOverview overview,
            AdminMonitoringFilter filter
    ) {
        AppointmentStatus status = overview.getAppointmentRequest().getStatus();
        CompanionSession session = overview.getSession();
        switch (filter) {
            case PAYMENT:
                return session != null && session.getStatus() == SessionStatus.PAYMENT;
            case MATCHED:
                return status == AppointmentStatus.MATCHED;
            case IN_PROGRESS:
                return status == AppointmentStatus.IN_PROGRESS;
            case ALL:
            default:
                return true;
        }
    }

    private boolean matchesSettlementFilter(
            AdminSettlementRecord settlementRecord,
            AppointmentFollowUpRecord followUpRecord,
            AdminSettlementFilter filter
    ) {
        switch (filter) {
            case USER_HELP:
                return hasUserHelpRequest(followUpRecord);
            case ADMIN_PENDING:
                return settlementRecord == null || settlementRecord.getStatus() == AdminSettlementStatus.PENDING;
            case NEEDS_REVIEW:
                return settlementRecord != null
                        && settlementRecord.getStatus() == AdminSettlementStatus.NEEDS_REVIEW;
            case CONFIRMED:
                return settlementRecord != null
                        && settlementRecord.getStatus() == AdminSettlementStatus.CONFIRMED;
            case ALL:
            default:
                return true;
        }
    }

    private int resolveMonitoringPriority(
            AdminRequestOverview overview
    ) {
        CompanionSession session = overview.getSession();
        if (session != null && session.getStatus() == SessionStatus.PAYMENT) {
            return 0;
        }
        if (overview.getAppointmentRequest().getStatus() == AppointmentStatus.IN_PROGRESS) {
            return 1;
        }
        if (overview.getAppointmentRequest().getStatus() == AppointmentStatus.MATCHED) {
            return 2;
        }
        return 3;
    }

    private int resolveSettlementPriority(
            AdminRequestOverview overview,
            AdminSettlementRecord settlementRecord,
            AppointmentFollowUpRecord followUpRecord
    ) {
        if (needsSettlementHelp(followUpRecord)) {
            return 0;
        }
        if (settlementRecord != null && settlementRecord.getStatus() == AdminSettlementStatus.NEEDS_REVIEW) {
            return 1;
        }
        if (settlementRecord == null || settlementRecord.getStatus() == AdminSettlementStatus.PENDING) {
            return 2;
        }
        if (BookingPaymentMethod.fromValue(
                overview.getAppointmentRequest().getPaymentMethodCode()
        ) == BookingPaymentMethod.ON_SITE) {
            return 3;
        }
        return 4;
    }

    private AdminOperationBadgeModel buildMonitoringStatusBadge(AdminRequestOverview overview) {
        CompanionSession session = overview.getSession();
        if (session != null && session.getStatus() == SessionStatus.PAYMENT) {
            return new AdminOperationBadgeModel(
                    formatter.formatSessionStatus(session.getStatus()),
                    AdminOperationBadgeTone.PURPLE
            );
        }
        if (overview.getAppointmentRequest().getStatus() == AppointmentStatus.IN_PROGRESS) {
            return new AdminOperationBadgeModel(
                    context.getString(R.string.booking_status_in_progress),
                    AdminOperationBadgeTone.SUCCESS
            );
        }
        return new AdminOperationBadgeModel(
                context.getString(R.string.booking_status_matched),
                AdminOperationBadgeTone.PRIMARY
        );
    }

    private AdminOperationBadgeModel buildSettlementStatusBadge(AppointmentRequest request) {
        BookingPaymentMethod paymentMethod = BookingPaymentMethod.fromValue(request.getPaymentMethodCode());
        BookingPaymentStatus paymentStatus = BookingPaymentStatus.fromValue(request.getPaymentStatusCode());
        if (paymentMethod == BookingPaymentMethod.ON_SITE) {
            return new AdminOperationBadgeModel(
                    formatter.formatSettlementStatus(request),
                    AdminOperationBadgeTone.WARNING
            );
        }
        if (isCompletedPaymentStatus(paymentStatus)) {
            return new AdminOperationBadgeModel(
                    formatter.formatSettlementStatus(request),
                    AdminOperationBadgeTone.SUCCESS
            );
        }
        if (paymentStatus == BookingPaymentStatus.DEFERRED) {
            return new AdminOperationBadgeModel(
                    formatter.formatSettlementStatus(request),
                    AdminOperationBadgeTone.PURPLE
            );
        }
        if (paymentStatus == BookingPaymentStatus.AWAITING_DEPOSIT
                || paymentStatus == BookingPaymentStatus.REVIEW_REQUIRED
                || paymentStatus == BookingPaymentStatus.CANCELED
                || paymentStatus == BookingPaymentStatus.UNKNOWN
                || paymentMethod == BookingPaymentMethod.UNKNOWN) {
            return new AdminOperationBadgeModel(
                    formatter.formatSettlementStatus(request),
                    AdminOperationBadgeTone.WARNING
            );
        }
        return new AdminOperationBadgeModel(
                formatter.formatSettlementStatus(request),
                AdminOperationBadgeTone.PRIMARY
        );
    }

    private boolean isCompletedPaymentStatus(BookingPaymentStatus paymentStatus) {
        return paymentStatus == BookingPaymentStatus.AUTHORIZED
                || paymentStatus == BookingPaymentStatus.DEPOSIT_CONFIRMED
                || paymentStatus == BookingPaymentStatus.REFUNDED;
    }

    private boolean isServerManagedPaymentStatus(BookingPaymentStatus paymentStatus) {
        switch (paymentStatus) {
            case AWAITING_DEPOSIT:
            case DEPOSIT_CONFIRMED:
            case REVIEW_REQUIRED:
            case REFUND_REQUESTED:
            case REFUNDED:
            case CANCELED:
                return true;
            default:
                return false;
        }
    }

    private AdminOperationBadgeModel buildMonitoringPriorityBadge(
            AdminRequestOverview overview
    ) {
        if (overview.getSession() != null && overview.getSession().getStatus() == SessionStatus.PAYMENT) {
            return new AdminOperationBadgeModel(
                    context.getString(R.string.admin_monitoring_priority_payment),
                    AdminOperationBadgeTone.PURPLE
            );
        }
        if (overview.getAppointmentRequest().getStatus() == AppointmentStatus.IN_PROGRESS) {
            return new AdminOperationBadgeModel(
                    context.getString(R.string.admin_monitoring_priority_in_progress),
                    AdminOperationBadgeTone.PRIMARY
            );
        }
        return new AdminOperationBadgeModel(
                context.getString(R.string.admin_monitoring_priority_matched),
                AdminOperationBadgeTone.PRIMARY
        );
    }

    private AdminOperationBadgeModel buildSettlementPriorityBadge(
            AdminSettlementRecord settlementRecord,
            AppointmentFollowUpRecord followUpRecord
    ) {
        if (needsSettlementHelp(followUpRecord)) {
            return new AdminOperationBadgeModel(
                    resolveSettlementHelpPriorityLabel(followUpRecord),
                    AdminOperationBadgeTone.WARNING
            );
        }
        if (settlementRecord != null && settlementRecord.getStatus() == AdminSettlementStatus.NEEDS_REVIEW) {
            return new AdminOperationBadgeModel(
                    context.getString(R.string.admin_settlement_priority_recheck),
                    AdminOperationBadgeTone.PURPLE
            );
        }
        if (settlementRecord == null || settlementRecord.getStatus() == AdminSettlementStatus.PENDING) {
            return new AdminOperationBadgeModel(
                    context.getString(R.string.admin_settlement_priority_pending),
                    AdminOperationBadgeTone.PRIMARY
            );
        }
        return new AdminOperationBadgeModel(
                context.getString(R.string.admin_settlement_priority_confirmed),
                AdminOperationBadgeTone.SUCCESS
        );
    }

    private boolean hasUserHelpRequest(AppointmentFollowUpRecord followUpRecord) {
        return needsSettlementHelp(followUpRecord);
    }

    private boolean needsSettlementHelp(AppointmentFollowUpRecord followUpRecord) {
        return followUpRecord != null
                && followUpRecord.hasSavedSettlement()
                && followUpRecord.getSettlementStatus() != null
                && followUpRecord.getSettlementStatus().requiresAdminFollowUp();
    }

    private String buildMonitoringCardSummary(
            AdminRequestOverview overview
    ) {
        CompanionSession session = overview.getSession();
        SessionReport report = overview.getSessionReport();
        if (session != null && !TextUtils.isEmpty(session.getGuardianUpdate())) {
            return context.getString(
                    R.string.admin_monitoring_card_summary_guardian,
                    session.getGuardianUpdate()
            );
        }
        if (session != null && !TextUtils.isEmpty(session.getLocationSummary())) {
            return context.getString(
                    R.string.admin_monitoring_card_summary_location,
                    session.getLocationSummary()
            );
        }
        if (session != null && !TextUtils.isEmpty(session.getFieldPhotoNote())) {
            return context.getString(
                    R.string.admin_monitoring_card_summary_field_photo,
                    session.getFieldPhotoNote()
            );
        }
        if (report != null && !TextUtils.isEmpty(report.getSummary())) {
            return context.getString(
                    R.string.admin_monitoring_card_summary_report,
                    report.getSummary()
            );
        }
        return context.getString(R.string.admin_monitoring_card_summary_empty);
    }

    private String buildSettlementCardSummary(
            AppointmentRequest request,
            AdminSettlementRecord settlementRecord,
            AppointmentFollowUpRecord followUpRecord
    ) {
        if (needsSettlementHelp(followUpRecord)) {
            return context.getString(
                    resolveSettlementHelpSummaryText(followUpRecord),
                    formatter.formatPrice(request.getFinalPrice()),
                    formatter.formatFallbackValue(followUpRecord.getSettlementNote())
            );
        }
        String adminSummary = settlementRecord == null
                ? context.getString(R.string.admin_settlement_follow_up_empty)
                : formatter.formatSettlementRecordStatus(settlementRecord.getStatus());
        return context.getString(
                R.string.admin_settlement_card_summary,
                formatter.formatPrice(request.getFinalPrice()),
                formatter.formatPaymentMethod(request.getPaymentMethodCode()),
                adminSummary
        );
    }

    private String resolveSettlementHelpPriorityLabel(AppointmentFollowUpRecord followUpRecord) {
        AppointmentFollowUpSettlementStatus status = followUpRecord == null
                ? null
                : followUpRecord.getSettlementStatus();
        if (status == AppointmentFollowUpSettlementStatus.OVERTIME_REVIEW) {
            return context.getString(R.string.admin_settlement_priority_overtime);
        }
        if (status == AppointmentFollowUpSettlementStatus.REFUND_REVIEW) {
            return context.getString(R.string.admin_settlement_priority_refund);
        }
        return context.getString(R.string.admin_settlement_priority_help);
    }

    private int resolveSettlementHelpSummaryText(AppointmentFollowUpRecord followUpRecord) {
        AppointmentFollowUpSettlementStatus status = followUpRecord == null
                ? null
                : followUpRecord.getSettlementStatus();
        if (status == AppointmentFollowUpSettlementStatus.OVERTIME_REVIEW) {
            return R.string.admin_settlement_card_summary_overtime;
        }
        if (status == AppointmentFollowUpSettlementStatus.REFUND_REVIEW) {
            return R.string.admin_settlement_card_summary_refund;
        }
        return R.string.admin_settlement_card_summary_help;
    }

    private String buildStepValue(CompanionSession session) {
        if (session == null) {
            return formatter.formatFallbackValue(null);
        }
        return context.getString(
                R.string.admin_monitoring_line_step_value,
                session.getCurrentStepOrder(),
                formatter.formatSessionStatus(session.getStatus())
        );
    }

    private String buildPatientName(AdminRequestOverview overview) {
        if (overview.getPatient() != null && !TextUtils.isEmpty(overview.getPatient().getName())) {
            return overview.getPatient().getName();
        }
        if (!TextUtils.isEmpty(overview.getAppointmentRequest().getPatientName())) {
            return overview.getAppointmentRequest().getPatientName();
        }
        return context.getString(R.string.admin_participant_patient_missing);
    }

    private AdminSettlementRecord findSettlementRecord(
            Map<String, AdminRequestActionOverview> actionsByRequestId,
            AdminRequestOverview overview
    ) {
        AdminRequestActionOverview actionOverview =
                actionsByRequestId.get(overview.getAppointmentRequest().getId());
        return actionOverview == null ? null : actionOverview.getSettlementRecord();
    }

    private AppointmentFollowUpRecord findFollowUpRecord(
            Map<String, AdminRequestActionOverview> actionsByRequestId,
            AdminRequestOverview overview
    ) {
        AdminRequestActionOverview actionOverview =
                actionsByRequestId.get(overview.getAppointmentRequest().getId());
        return actionOverview == null ? null : actionOverview.getFollowUpRecord();
    }

    private List<AdminOperationActionModel> buildSettlementActions(
            AppointmentRequest request,
            AdminSettlementRecord settlementRecord
    ) {
        List<AdminOperationActionModel> actions = new ArrayList<>();
        if (!AdminSettlementActionPolicy.canUseLegacyFirestoreAction(request)) {
            return actions;
        }
        if (settlementRecord != null && settlementRecord.getStatus() == AdminSettlementStatus.CONFIRMED) {
            actions.add(new AdminOperationActionModel(
                    AdminOperationActionType.SAVE_SETTLEMENT_RECHECK,
                    context.getString(R.string.admin_settlement_action_recheck)
            ));
            return actions;
        }
        actions.add(new AdminOperationActionModel(
                AdminOperationActionType.SAVE_SETTLEMENT_CONFIRMED,
                context.getString(R.string.admin_settlement_action_confirm)
        ));
        return actions;
    }

    private void addOptionalLine(List<AdminOperationLineItem> items, int labelResId, String value) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        items.add(new AdminOperationLineItem(
                context.getString(labelResId),
                value,
                false
        ));
    }
}
