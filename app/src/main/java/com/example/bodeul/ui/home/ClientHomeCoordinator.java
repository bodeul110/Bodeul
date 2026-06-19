package com.example.bodeul.ui.home;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.ClientSupportRepository;
import com.example.bodeul.data.GuardianReportRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.ClientSupportRequest;
import com.example.bodeul.domain.model.GuardianReportDashboard;
import com.example.bodeul.domain.model.GuardianReportEntry;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.common.AttentionBannerModel;
import com.example.bodeul.ui.common.AppointmentProgressComposer;
import com.example.bodeul.ui.common.AppointmentProgressOverviewModel;
import com.example.bodeul.ui.support.ClientSupportAttentionBannerFactory;

import java.util.Collections;
import java.util.List;

/**
 * 환자/보호자 홈에 필요한 여러 저장소 호출을 조합하는 코디네이터다.
 */
public final class ClientHomeCoordinator {
    private final Context context;
    private final BookingRepository bookingRepository;
    private final GuardianReportRepository guardianReportRepository;
    private final ClientSupportRepository clientSupportRepository;
    private final ClientHomeNoticeProvider noticeProvider;
    private final AppointmentProgressComposer progressComposer;

    public ClientHomeCoordinator(
            Context context,
            BookingRepository bookingRepository,
            GuardianReportRepository guardianReportRepository,
            ClientSupportRepository clientSupportRepository,
            ClientHomeNoticeProvider noticeProvider,
            AppointmentProgressComposer progressComposer
    ) {
        this.context = context.getApplicationContext();
        this.bookingRepository = bookingRepository;
        this.guardianReportRepository = guardianReportRepository;
        this.clientSupportRepository = clientSupportRepository;
        this.noticeProvider = noticeProvider;
        this.progressComposer = progressComposer;
    }

    public boolean isFirebaseBacked() {
        return bookingRepository.isFirebaseBacked();
    }

    public void loadDashboard(User currentUser, RepositoryCallback<ClientHomeDashboard> callback) {
        bookingRepository.getMyAppointmentRequests(currentUser, new RepositoryCallback<List<AppointmentRequest>>() {
            @Override
            public void onSuccess(List<AppointmentRequest> appointmentRequests) {
                if (currentUser.getRole() != UserRole.GUARDIAN) {
                    loadPrimaryFollowUpAndDeliver(currentUser, appointmentRequests, null, callback);
                    return;
                }

                guardianReportRepository.getGuardianDashboard(currentUser, new RepositoryCallback<GuardianReportDashboard>() {
                    @Override
                    public void onSuccess(GuardianReportDashboard result) {
                        loadPrimaryFollowUpAndDeliver(
                                currentUser,
                                appointmentRequests,
                                findHighlightEntry(result.getEntries()),
                                callback
                        );
                    }

                    @Override
                    public void onError(String message) {
                        // 보호자 진행 현황 조회가 실패해도 기본 예약 요청 목록으로 홈을 그린다.
                        loadPrimaryFollowUpAndDeliver(currentUser, appointmentRequests, null, callback);
                    }
                });
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private void loadPrimaryFollowUpAndDeliver(
            User currentUser,
            List<AppointmentRequest> appointmentRequests,
            @Nullable GuardianReportEntry highlightEntry,
            RepositoryCallback<ClientHomeDashboard> callback
    ) {
        List<AppointmentRequest> safeRequests = appointmentRequests == null
                ? Collections.emptyList()
                : appointmentRequests;
        AppointmentRequest primaryRequest = resolvePrimaryRequest(safeRequests, highlightEntry);
        if (primaryRequest == null || primaryRequest.getStatus() != AppointmentStatus.COMPLETED) {
            loadSupportRequestsAndDeliver(currentUser, safeRequests, highlightEntry, null, callback);
            return;
        }

        bookingRepository.getAppointmentFollowUp(
                currentUser,
                primaryRequest.getId(),
                new RepositoryCallback<AppointmentFollowUpRecord>() {
                    @Override
                    public void onSuccess(AppointmentFollowUpRecord result) {
                        loadSupportRequestsAndDeliver(currentUser, safeRequests, highlightEntry, result, callback);
                    }

                    @Override
                    public void onError(String message) {
                        loadSupportRequestsAndDeliver(
                                currentUser,
                                safeRequests,
                                highlightEntry,
                                AppointmentFollowUpRecord.empty(primaryRequest.getId()),
                                callback
                        );
                    }
                }
        );
    }

    private void loadSupportRequestsAndDeliver(
            User currentUser,
            List<AppointmentRequest> appointmentRequests,
            @Nullable GuardianReportEntry highlightEntry,
            @Nullable AppointmentFollowUpRecord followUpRecord,
            RepositoryCallback<ClientHomeDashboard> callback
    ) {
        clientSupportRepository.getClientSupportRequests(currentUser, new RepositoryCallback<List<ClientSupportRequest>>() {
            @Override
            public void onSuccess(List<ClientSupportRequest> result) {
                callback.onSuccess(buildDashboard(
                        currentUser,
                        appointmentRequests,
                        highlightEntry,
                        followUpRecord,
                        countUnreadSupportResponses(result),
                        countStaleUnreadSupportResponses(result)
                ));
            }

            @Override
            public void onError(String message) {
                callback.onSuccess(buildDashboard(
                        currentUser,
                        appointmentRequests,
                        highlightEntry,
                        followUpRecord,
                        0,
                        0
                ));
            }
        });
    }

    @NonNull
    private ClientHomeDashboard buildDashboard(
            User currentUser,
            List<AppointmentRequest> appointmentRequests,
            @Nullable GuardianReportEntry highlightEntry,
            @Nullable AppointmentFollowUpRecord followUpRecord,
            int unreadSupportResponseCount,
            int staleUnreadSupportResponseCount
    ) {
        List<AppointmentRequest> safeRequests = appointmentRequests == null
                ? Collections.emptyList()
                : appointmentRequests;
        AppointmentRequest primaryRequest = resolvePrimaryRequest(safeRequests, highlightEntry);
        GuardianReportEntry progressEntry = resolveProgressEntry(primaryRequest, highlightEntry);
        AppointmentProgressOverviewModel progressOverview = primaryRequest == null
                ? null
                : progressComposer.create(
                        currentUser.getRole(),
                        primaryRequest,
                        progressEntry == null ? null : progressEntry.getManager(),
                        progressEntry == null ? null : progressEntry.getSession(),
                        progressEntry == null ? null : progressEntry.getSessionReport(),
                        progressEntry == null ? null : progressEntry.getHospitalGuide()
                );
        AttentionBannerModel supportBanner = ClientSupportAttentionBannerFactory.create(
                context,
                unreadSupportResponseCount,
                staleUnreadSupportResponseCount
        );
        return new ClientHomeDashboard(
                currentUser,
                safeRequests,
                highlightEntry,
                primaryRequest,
                progressOverview,
                followUpRecord,
                supportBanner,
                unreadSupportResponseCount,
                staleUnreadSupportResponseCount,
                noticeProvider.createNotices(currentUser.getRole())
        );
    }

    private int countUnreadSupportResponses(@Nullable List<ClientSupportRequest> supportRequests) {
        if (supportRequests == null) {
            return 0;
        }
        int unreadCount = 0;
        for (ClientSupportRequest request : supportRequests) {
            if (request.hasUnreadResponse()) {
                unreadCount++;
            }
        }
        return unreadCount;
    }

    private int countStaleUnreadSupportResponses(@Nullable List<ClientSupportRequest> supportRequests) {
        if (supportRequests == null) {
            return 0;
        }
        int unreadCount = 0;
        long nowMillis = System.currentTimeMillis();
        long thresholdMillis = 24L * 60L * 60L * 1000L;
        for (ClientSupportRequest request : supportRequests) {
            if (request.hasStaleUnreadResponse(nowMillis, thresholdMillis)) {
                unreadCount++;
            }
        }
        return unreadCount;
    }

    @Nullable
    private GuardianReportEntry findHighlightEntry(List<GuardianReportEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        for (GuardianReportEntry entry : entries) {
            AppointmentStatus status = entry.getAppointmentRequest().getStatus();
            if (status != AppointmentStatus.COMPLETED && status != AppointmentStatus.CANCELED) {
                return entry;
            }
        }
        return entries.get(0);
    }

    @Nullable
    private AppointmentRequest resolvePrimaryRequest(
            List<AppointmentRequest> appointmentRequests,
            @Nullable GuardianReportEntry highlightEntry
    ) {
        if (highlightEntry != null) {
            return highlightEntry.getAppointmentRequest();
        }
        for (AppointmentRequest request : appointmentRequests) {
            if (request.getStatus() != AppointmentStatus.COMPLETED
                    && request.getStatus() != AppointmentStatus.CANCELED) {
                return request;
            }
        }
        return appointmentRequests.isEmpty() ? null : appointmentRequests.get(0);
    }

    @Nullable
    private GuardianReportEntry resolveProgressEntry(
            @Nullable AppointmentRequest primaryRequest,
            @Nullable GuardianReportEntry highlightEntry
    ) {
        if (primaryRequest == null || highlightEntry == null) {
            return null;
        }
        if (!primaryRequest.getId().equals(highlightEntry.getAppointmentRequest().getId())) {
            return null;
        }
        return highlightEntry;
    }
}
