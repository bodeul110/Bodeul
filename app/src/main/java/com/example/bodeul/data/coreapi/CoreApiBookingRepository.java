package com.example.bodeul.data.coreapi;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.firebase.FirebaseBookingRepository;
import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentFollowUpReviewRating;
import com.example.bodeul.domain.model.AppointmentFollowUpSettlementStatus;
import com.example.bodeul.domain.model.AppointmentFollowUpSupportEscalationStatus;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.BookingHospitalOption;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 예약·세션·채팅·위치·리포트는 Core API에서 읽고, 병원 가이드 등 아직 남은 보조 자료만 Firebase에서 합성한다.
 */
public final class CoreApiBookingRepository implements BookingRepository {
    private static final long CORE_REFRESH_INTERVAL_MILLIS = 10_000L;
    private static final String LEGACY_REQUIRED_MESSAGE =
            "이 예약에는 아직 Firestore 채팅 연결 정보가 없습니다.";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CoreApiAppointmentClient appointmentClient;
    private final CoreApiCompanionSessionClient sessionClient;
    private final CoreApiFollowUpClient followUpClient;
    private final FirebaseBookingRepository firebaseRepository;

    public CoreApiBookingRepository(
            Context context,
            FirebaseBookingRepository firebaseRepository
    ) {
        this.appointmentClient = new CoreApiAppointmentClient(context);
        this.sessionClient = new CoreApiCompanionSessionClient(context);
        this.followUpClient = new CoreApiFollowUpClient(context);
        this.firebaseRepository = firebaseRepository;
    }

    @Override
    public void getHospitalOptions(RepositoryCallback<List<BookingHospitalOption>> callback) {
        firebaseRepository.getHospitalOptions(callback);
    }

    @Override
    public void getMyAppointmentRequests(
            User currentUser,
            RepositoryCallback<List<AppointmentRequest>> callback
    ) {
        appointmentClient.getAppointments(callback);
    }

    @Override
    public void getAppointmentRequestDetail(
            User currentUser,
            String requestId,
            RepositoryCallback<AppointmentRequestDetail> callback
    ) {
        appointmentClient.getAppointment(requestId, new RepositoryCallback<AppointmentRequest>() {
            @Override
            public void onSuccess(AppointmentRequest request) {
                enrichWithLegacyDetail(currentUser, request, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public Runnable observeAppointmentRequestDetail(
            User currentUser,
            String requestId,
            RepositoryCallback<AppointmentRequestDetail> callback
    ) {
        AtomicBoolean stopped = new AtomicBoolean(false);
        AtomicReference<Runnable> detachListener = new AtomicReference<>(() -> {});
        AtomicReference<Runnable> pollTask = new AtomicReference<>(() -> {});
        AtomicReference<AppointmentRequestDetail> latestLegacyDetail = new AtomicReference<>();
        appointmentClient.getAppointment(requestId, new RepositoryCallback<AppointmentRequest>() {
            @Override
            public void onSuccess(AppointmentRequest request) {
                if (stopped.get()) {
                    return;
                }
                String legacyId = appointmentClient.getKnownLegacyFirestoreId(request.getId());
                if (legacyId.isEmpty()) {
                    AppointmentRequestDetail coreOnlyDetail = toCoreOnlyDetail(request);
                    latestLegacyDetail.set(coreOnlyDetail);
                    enrichWithCoreSession(request, coreOnlyDetail, callback);
                    scheduleCoreRefresh(
                            requestId,
                            stopped,
                            latestLegacyDetail,
                            pollTask,
                            callback);
                    return;
                }

                Runnable detach = firebaseRepository.observeAppointmentRequestDetail(
                        currentUser,
                        legacyId,
                        new RepositoryCallback<AppointmentRequestDetail>() {
                            @Override
                            public void onSuccess(AppointmentRequestDetail legacyDetail) {
                                if (stopped.get()) {
                                    return;
                                }
                                latestLegacyDetail.set(legacyDetail);
                                appointmentClient.getAppointment(
                                        request.getId(),
                                        new RepositoryCallback<AppointmentRequest>() {
                                            @Override
                                            public void onSuccess(AppointmentRequest currentRequest) {
                                                if (!stopped.get()) {
                                                    enrichWithCoreSession(
                                                            currentRequest,
                                                            legacyDetail,
                                                            new RepositoryCallback<AppointmentRequestDetail>() {
                                                                @Override
                                                                public void onSuccess(
                                                                        AppointmentRequestDetail result
                                                                ) {
                                                                    if (!stopped.get()) {
                                                                        callback.onSuccess(result);
                                                                    }
                                                                }

                                                                @Override
                                                                public void onError(String message) {
                                                                    if (!stopped.get()) {
                                                                        callback.onError(message);
                                                                    }
                                                                }
                                                            });
                                                }
                                            }

                                            @Override
                                            public void onError(String message) {
                                                if (!stopped.get()) {
                                                    callback.onError(message);
                                                }
                                            }
                                        });
                            }

                            @Override
                            public void onError(String message) {
                                if (!stopped.get()) {
                                    callback.onError(message);
                                }
                            }
                        });
                detachListener.set(detach);
                scheduleCoreRefresh(
                        requestId,
                        stopped,
                        latestLegacyDetail,
                        pollTask,
                        callback);
                if (stopped.get()) {
                    detach.run();
                }
            }

            @Override
            public void onError(String message) {
                if (!stopped.get()) {
                    callback.onError(message);
                }
            }
        });

        return () -> {
            stopped.set(true);
            detachListener.get().run();
            mainHandler.removeCallbacks(pollTask.get());
        };
    }

    @Override
    public void createAppointmentRequest(
            User currentUser,
            BookingRequestDraft bookingRequestDraft,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        appointmentClient.createAppointment(bookingRequestDraft, callback);
    }

    @Override
    public void updateAppointmentRequest(
            User currentUser,
            String requestId,
            BookingRequestDraft bookingRequestDraft,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        appointmentClient.updateAppointment(requestId, bookingRequestDraft, callback);
    }

    @Override
    public void cancelAppointmentRequest(
            User currentUser,
            String requestId,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        appointmentClient.cancelAppointment(requestId, callback);
    }

    @Override
    public void getAppointmentFollowUp(
            User currentUser,
            String requestId,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    ) {
        withCoreId(requestId, callback, coreId ->
                followUpClient.getFollowUp(coreId, requestId, callback));
    }

    @Override
    public void saveAppointmentFollowUpReview(
            User currentUser,
            String requestId,
            AppointmentFollowUpReviewRating reviewRating,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    ) {
        withCoreId(requestId, callback, coreId ->
                followUpClient.saveReview(coreId, requestId, reviewRating, callback));
    }

    @Override
    public void saveAppointmentFollowUpSettlement(
            User currentUser,
            String requestId,
            AppointmentFollowUpSettlementStatus settlementStatus,
            String settlementNote,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    ) {
        withCoreId(requestId, callback, coreId ->
                followUpClient.saveSettlement(
                        coreId,
                        requestId,
                        settlementStatus,
                        settlementNote,
                        callback));
    }

    @Override
    public void saveAppointmentFollowUpSupportEscalation(
            User currentUser,
            String requestId,
            AppointmentFollowUpSupportEscalationStatus escalationStatus,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    ) {
        withCoreId(requestId, callback, coreId ->
                followUpClient.saveSupportEscalation(
                        coreId,
                        requestId,
                        escalationStatus,
                        callback));
    }

    @Override
    public void sendCompanionChatMessage(
            User currentUser,
            String requestId,
            String message,
            List<CompanionChatAttachment> attachments,
            RepositoryCallback<AppointmentRequestDetail> callback
    ) {
        sessionClient.sendRealtimeMessage(
                resolveKnownSessionId(requestId),
                message,
                attachments,
                new RepositoryCallback<CoreApiCompanionSessionClient.RealtimeSnapshot>() {
                    @Override
                    public void onSuccess(CoreApiCompanionSessionClient.RealtimeSnapshot result) {
                        getAppointmentRequestDetail(currentUser, requestId, callback);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        callback.onError(errorMessage);
                    }
                });
    }

    @Override
    public void markCompanionChatRead(User currentUser, String requestId) {
        sessionClient.markRealtimeRead(resolveKnownSessionId(requestId));
    }

    @Override
    public boolean isFirebaseBacked() {
        return true;
    }

    private void enrichWithLegacyDetail(
            User currentUser,
            AppointmentRequest request,
            RepositoryCallback<AppointmentRequestDetail> callback
    ) {
        String legacyId = appointmentClient.getKnownLegacyFirestoreId(request.getId());
        if (legacyId.isEmpty()) {
            enrichWithCoreSession(request, toCoreOnlyDetail(request), callback);
            return;
        }
        firebaseRepository.getAppointmentRequestDetail(
                currentUser,
                legacyId,
                new RepositoryCallback<AppointmentRequestDetail>() {
                    @Override
                    public void onSuccess(AppointmentRequestDetail legacyDetail) {
                        enrichWithCoreSession(request, legacyDetail, callback);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    private void enrichWithCoreSession(
            AppointmentRequest request,
            AppointmentRequestDetail legacyDetail,
            RepositoryCallback<AppointmentRequestDetail> callback
    ) {
        CompanionSession legacySession = legacyDetail.getSession();
        sessionClient.findSession(
                legacySession == null ? null : legacySession.getId(),
                appointmentClient.getKnownCoreId(request.getId()),
                new RepositoryCallback<CoreApiCompanionSessionClient.SessionSnapshot>() {
                    @Override
                    public void onSuccess(
                            CoreApiCompanionSessionClient.SessionSnapshot sessionSnapshot
                    ) {
                        if (sessionSnapshot == null) {
                            if (legacySession != null) {
                                callback.onError("PostgreSQL 동행 세션 정보를 찾지 못했습니다.");
                                return;
                            }
                            callback.onSuccess(copyDetail(request, legacyDetail, null, null));
                            return;
                        }
                        CompanionSession session = sessionSnapshot.merge(
                                legacySession,
                                request.getId());
                        sessionClient.enrichWithRealtime(
                                sessionSnapshot,
                                session,
                                new RepositoryCallback<CompanionSession>() {
                                    @Override
                                    public void onSuccess(CompanionSession realtimeSession) {
                                        if (sessionSnapshot.getStatus() != SessionStatus.COMPLETED) {
                                            callback.onSuccess(copyDetail(
                                                    request,
                                                    legacyDetail,
                                                    realtimeSession,
                                                    null));
                                            return;
                                        }
                                        sessionClient.getReport(
                                                sessionSnapshot,
                                                new RepositoryCallback<CoreApiCompanionSessionClient.ReportSnapshot>() {
                                                    @Override
                                                    public void onSuccess(
                                                            CoreApiCompanionSessionClient.ReportSnapshot report
                                                    ) {
                                                        callback.onSuccess(copyDetail(
                                                                request,
                                                                legacyDetail,
                                                                realtimeSession,
                                                                report.toModel(realtimeSession.getId())));
                                                    }

                                                    @Override
                                                    public void onError(String message) {
                                                        callback.onError(message);
                                                    }
                                                });
                                    }

                                    @Override
                                    public void onError(String message) {
                                        callback.onError(message);
                                    }
                                });
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    private String resolveKnownSessionId(String requestId) {
        String coreAppointmentId = appointmentClient.getKnownCoreId(requestId);
        CoreApiCompanionSessionClient.SessionSnapshot session = sessionClient.findKnown(
                null,
                coreAppointmentId);
        return session == null ? requestId : session.getCoreId();
    }

    private AppointmentRequestDetail copyDetail(
            AppointmentRequest request,
            AppointmentRequestDetail legacyDetail,
            @Nullable CompanionSession session,
            @Nullable SessionReport report
    ) {
        return new AppointmentRequestDetail(
                request,
                legacyDetail.getPatient(),
                legacyDetail.getGuardian(),
                legacyDetail.getManager(),
                session,
                report,
                legacyDetail.getHospitalGuide(),
                legacyDetail.getFollowUpRecord());
    }

    private void scheduleCoreRefresh(
            String requestId,
            AtomicBoolean stopped,
            AtomicReference<AppointmentRequestDetail> latestLegacyDetail,
            AtomicReference<Runnable> pollTask,
            RepositoryCallback<AppointmentRequestDetail> callback
    ) {
        Runnable refresh = new Runnable() {
            @Override
            public void run() {
                if (stopped.get()) {
                    return;
                }
                AppointmentRequestDetail legacyDetail = latestLegacyDetail.get();
                if (legacyDetail == null) {
                    mainHandler.postDelayed(this, CORE_REFRESH_INTERVAL_MILLIS);
                    return;
                }
                appointmentClient.getAppointment(
                        requestId,
                        new RepositoryCallback<AppointmentRequest>() {
                            @Override
                            public void onSuccess(AppointmentRequest request) {
                                if (stopped.get()) {
                                    return;
                                }
                                enrichWithCoreSession(
                                        request,
                                        legacyDetail,
                                        new RepositoryCallback<AppointmentRequestDetail>() {
                                            @Override
                                            public void onSuccess(AppointmentRequestDetail result) {
                                                if (!stopped.get()) {
                                                    callback.onSuccess(result);
                                                    mainHandler.postDelayed(
                                                            pollTask.get(),
                                                            CORE_REFRESH_INTERVAL_MILLIS);
                                                }
                                            }

                                            @Override
                                            public void onError(String message) {
                                                if (!stopped.get()) {
                                                    callback.onError(message);
                                                    mainHandler.postDelayed(
                                                            pollTask.get(),
                                                            CORE_REFRESH_INTERVAL_MILLIS);
                                                }
                                            }
                                        });
                            }

                            @Override
                            public void onError(String message) {
                                if (!stopped.get()) {
                                    callback.onError(message);
                                    mainHandler.postDelayed(
                                            pollTask.get(),
                                            CORE_REFRESH_INTERVAL_MILLIS);
                                }
                            }
                        });
            }
        };
        pollTask.set(refresh);
        mainHandler.postDelayed(refresh, CORE_REFRESH_INTERVAL_MILLIS);
    }

    private AppointmentRequestDetail toCoreOnlyDetail(AppointmentRequest request) {
        return new AppointmentRequestDetail(
                request,
                toParticipant(
                        request.getPatientUserId(),
                        UserRole.PATIENT,
                        request.getPatientName(),
                        request.getPatientEmail(),
                        request.getPatientPhone()),
                toParticipant(
                        request.getGuardianUserId(),
                        UserRole.GUARDIAN,
                        request.getGuardianName(),
                        request.getGuardianEmail(),
                        request.getGuardianPhone()),
                toParticipant(
                        request.getManagerUserId(),
                        UserRole.MANAGER,
                        request.getManagerName().isEmpty()
                                ? "배정 매니저"
                                : request.getManagerName(),
                        request.getManagerEmail(),
                        request.getManagerPhone()),
                null,
                null,
                null);
    }

    @Nullable
    private User toParticipant(
            String userId,
            UserRole role,
            String name,
            String email,
            String phone
    ) {
        if (TextUtils.isEmpty(userId) && TextUtils.isEmpty(name)) {
            return null;
        }
        return new User(userId, role, name, email, phone);
    }

    private <T> void withLegacyId(
            String requestId,
            RepositoryCallback<T> callback,
            LegacyOperation operation
    ) {
        appointmentClient.resolveLegacyFirestoreId(
                requestId,
                new RepositoryCallback<String>() {
                    @Override
                    public void onSuccess(String legacyId) {
                        if (legacyId.isEmpty()) {
                            callback.onError(LEGACY_REQUIRED_MESSAGE);
                            return;
                        }
                        operation.run(legacyId);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    private <T> void withCoreId(
            String requestId,
            RepositoryCallback<T> callback,
            CoreOperation operation
    ) {
        appointmentClient.resolveCoreId(
                requestId,
                new RepositoryCallback<String>() {
                    @Override
                    public void onSuccess(String coreId) {
                        if (coreId.isEmpty()) {
                            callback.onError("예약의 Core API 식별자를 확인하지 못했습니다.");
                            return;
                        }
                        operation.run(coreId);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    private interface LegacyOperation {
        void run(String legacyId);
    }

    private interface CoreOperation {
        void run(String coreId);
    }
}
