package com.example.bodeul.ui.home;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.GuardianReportEntry;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.common.AppointmentProgressOverviewModel;

import java.util.Collections;
import java.util.List;

/**
 * 환자/보호자 메인 홈에 필요한 데이터를 한 번에 전달하는 화면 전용 모델이다.
 */
public final class ClientHomeDashboard {
    private final User user;
    private final List<AppointmentRequest> appointmentRequests;
    @Nullable
    private final GuardianReportEntry highlightGuardianEntry;
    @Nullable
    private final AppointmentRequest primaryRequest;
    @Nullable
    private final AppointmentProgressOverviewModel progressOverview;
    @Nullable
    private final AppointmentFollowUpRecord primaryFollowUpRecord;
    private final List<ClientHomeNotice> notices;

    public ClientHomeDashboard(
            User user,
            List<AppointmentRequest> appointmentRequests,
            @Nullable GuardianReportEntry highlightGuardianEntry,
            @Nullable AppointmentRequest primaryRequest,
            @Nullable AppointmentProgressOverviewModel progressOverview,
            @Nullable AppointmentFollowUpRecord primaryFollowUpRecord,
            List<ClientHomeNotice> notices
    ) {
        this.user = user;
        this.appointmentRequests = Collections.unmodifiableList(appointmentRequests);
        this.highlightGuardianEntry = highlightGuardianEntry;
        this.primaryRequest = primaryRequest;
        this.progressOverview = progressOverview;
        this.primaryFollowUpRecord = primaryFollowUpRecord;
        this.notices = Collections.unmodifiableList(notices);
    }

    public User getUser() {
        return user;
    }

    public List<AppointmentRequest> getAppointmentRequests() {
        return appointmentRequests;
    }

    @Nullable
    public GuardianReportEntry getHighlightGuardianEntry() {
        return highlightGuardianEntry;
    }

    @Nullable
    public AppointmentProgressOverviewModel getProgressOverview() {
        return progressOverview;
    }

    public List<ClientHomeNotice> getNotices() {
        return notices;
    }

    @Nullable
    public AppointmentFollowUpRecord getPrimaryFollowUpRecord() {
        return primaryFollowUpRecord;
    }

    public boolean isGuardianUser() {
        return user.getRole() == UserRole.GUARDIAN;
    }

    public boolean hasRequests() {
        return !appointmentRequests.isEmpty();
    }

    public int getRequestCount() {
        return appointmentRequests.size();
    }

    public int getActiveRequestCount() {
        int count = 0;
        for (AppointmentRequest request : appointmentRequests) {
            if (request.getStatus() != AppointmentStatus.COMPLETED
                    && request.getStatus() != AppointmentStatus.CANCELED) {
                count++;
            }
        }
        return count;
    }

    public int getCompletedRequestCount() {
        int count = 0;
        for (AppointmentRequest request : appointmentRequests) {
            if (request.getStatus() == AppointmentStatus.COMPLETED) {
                count++;
            }
        }
        return count;
    }

    public boolean hasActiveRequest() {
        AppointmentRequest primaryRequest = getPrimaryRequest();
        if (primaryRequest == null) {
            return false;
        }
        return primaryRequest.getStatus() != AppointmentStatus.COMPLETED
                && primaryRequest.getStatus() != AppointmentStatus.CANCELED;
    }

    @Nullable
    public AppointmentRequest getPrimaryRequest() {
        return primaryRequest;
    }
}
