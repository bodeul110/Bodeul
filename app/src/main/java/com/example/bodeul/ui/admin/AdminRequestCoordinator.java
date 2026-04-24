package com.example.bodeul.ui.admin;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AdminRequestOverview;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.User;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 관리자 요청 카드와 관리중 요청 섹션을 조합한다.
 */
public final class AdminRequestCoordinator {
    private final Context context;
    private final AdminRequestPresentationFormatter formatter;

    public AdminRequestCoordinator(Context context, AdminRequestPresentationFormatter formatter) {
        this.context = context.getApplicationContext();
        this.formatter = formatter;
    }

    public List<AdminRequestCardModel> createPendingCards(
            List<AdminRequestOverview> pendingRequests,
            List<User> availableManagers,
            Set<String> expandedRequestIds
    ) {
        List<AdminRequestCardModel> cards = new ArrayList<>();
        for (AdminRequestOverview overview : pendingRequests) {
            cards.add(createCardModel(
                    overview,
                    availableManagers,
                    true,
                    expandedRequestIds.contains(overview.getAppointmentRequest().getId())
            ));
        }
        return cards;
    }

    public AdminManagedRequestSectionModel createManagedSectionModel(
            List<AdminRequestOverview> managedRequests,
            AdminManagedRequestFilter selectedFilter,
            AdminManagedRequestDateFilter selectedDateFilter,
            Set<String> expandedRequestIds
    ) {
        List<AdminManagedFilterChipModel> statusChips = new ArrayList<>();
        for (AdminManagedRequestFilter filter : AdminManagedRequestFilter.values()) {
            statusChips.add(new AdminManagedFilterChipModel(
                    filter,
                    context.getString(
                            R.string.admin_managed_filter_button,
                            formatter.toManagedFilterLabel(filter),
                            countManagedRequests(managedRequests, filter)
                    ),
                    filter == selectedFilter
            ));
        }

        List<AdminManagedDateFilterChipModel> dateChips = new ArrayList<>();
        for (AdminManagedRequestDateFilter filter : AdminManagedRequestDateFilter.values()) {
            dateChips.add(new AdminManagedDateFilterChipModel(
                    filter,
                    context.getString(
                            R.string.admin_managed_date_filter_button,
                            formatter.toManagedDateFilterLabel(filter),
                            countManagedRequestsByDate(managedRequests, filter)
                    ),
                    filter == selectedDateFilter
            ));
        }

        List<AdminRequestOverview> filteredRequests = filterManagedRequests(
                managedRequests,
                selectedFilter,
                selectedDateFilter
        );
        List<AdminRequestCardModel> cards = new ArrayList<>();
        for (AdminRequestOverview overview : filteredRequests) {
            cards.add(createCardModel(
                    overview,
                    new ArrayList<>(),
                    false,
                    expandedRequestIds.contains(overview.getAppointmentRequest().getId())
            ));
        }

        String summaryText = managedRequests.isEmpty()
                ? context.getString(R.string.admin_managed_summary_empty)
                : formatter.buildManagedSummary(
                        countManagedRequests(managedRequests, AdminManagedRequestFilter.MATCHED),
                        countManagedRequests(managedRequests, AdminManagedRequestFilter.IN_PROGRESS),
                        countManagedRequests(managedRequests, AdminManagedRequestFilter.COMPLETED),
                        countManagedRequests(managedRequests, AdminManagedRequestFilter.CANCELED),
                        selectedFilter,
                        selectedDateFilter,
                        filteredRequests.size(),
                        managedRequests.size()
                );

        return new AdminManagedRequestSectionModel(
                summaryText,
                statusChips,
                dateChips,
                cards,
                !managedRequests.isEmpty()
        );
    }

    private AdminRequestCardModel createCardModel(
            AdminRequestOverview overview,
            List<User> availableManagers,
            boolean actionable,
            boolean expanded
    ) {
        String blockingReason = actionable
                ? formatter.resolveBlockingReason(overview, availableManagers)
                : "";
        List<AdminRequestAssignActionModel> assignActions = new ArrayList<>();
        if (actionable && TextUtils.isEmpty(blockingReason)) {
            for (User manager : availableManagers) {
                assignActions.add(new AdminRequestAssignActionModel(
                        manager.getId(),
                        formatter.buildAssignActionText(manager)
                ));
            }
        }

        return new AdminRequestCardModel(
                overview.getAppointmentRequest().getId(),
                formatter.buildRequestTitle(overview),
                formatter.toStatusLabel(overview.getAppointmentRequest().getStatus()),
                formatter.getStatusBackgroundColorResId(overview.getAppointmentRequest().getStatus()),
                formatter.getStatusTextColorResId(overview.getAppointmentRequest().getStatus()),
                formatter.buildParticipantsText(overview),
                formatter.buildScheduleText(overview),
                formatter.buildManagerText(overview),
                formatter.buildProgressText(overview),
                formatter.buildDetailToggleText(expanded),
                expanded,
                formatter.buildDetailPanelText(overview),
                formatter.buildNoteText(overview),
                blockingReason,
                assignActions
        );
    }

    private int countManagedRequests(
            List<AdminRequestOverview> managedRequests,
            AdminManagedRequestFilter filter
    ) {
        return filterManagedRequests(
                managedRequests,
                filter,
                AdminManagedRequestDateFilter.ALL
        ).size();
    }

    private int countManagedRequestsByDate(
            List<AdminRequestOverview> managedRequests,
            AdminManagedRequestDateFilter filter
    ) {
        int count = 0;
        for (AdminRequestOverview overview : managedRequests) {
            if (matchesManagedDateFilter(overview, filter)) {
                count++;
            }
        }
        return count;
    }

    private List<AdminRequestOverview> filterManagedRequests(
            List<AdminRequestOverview> managedRequests,
            AdminManagedRequestFilter statusFilter,
            AdminManagedRequestDateFilter dateFilter
    ) {
        List<AdminRequestOverview> filteredRequests = new ArrayList<>();
        for (AdminRequestOverview overview : managedRequests) {
            if (matchesManagedFilter(overview, statusFilter)
                    && matchesManagedDateFilter(overview, dateFilter)) {
                filteredRequests.add(overview);
            }
        }
        return filteredRequests;
    }

    private boolean matchesManagedFilter(
            AdminRequestOverview overview,
            AdminManagedRequestFilter filter
    ) {
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

    private boolean matchesManagedDateFilter(
            AdminRequestOverview overview,
            AdminManagedRequestDateFilter filter
    ) {
        if (filter == AdminManagedRequestDateFilter.ALL) {
            return true;
        }

        Calendar appointmentCalendar = parseAppointmentCalendar(
                overview.getAppointmentRequest().getAppointmentAt()
        );
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
}
