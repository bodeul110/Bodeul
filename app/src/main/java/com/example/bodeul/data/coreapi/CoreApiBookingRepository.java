package com.example.bodeul.data.coreapi;

import android.content.Context;
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
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 예약 원본은 Core API에서 읽고, 아직 이전하지 않은 세션·채팅·후속 데이터만 Firestore에서 합성한다.
 */
public final class CoreApiBookingRepository implements BookingRepository {
    private static final String LEGACY_REQUIRED_MESSAGE =
            "이 예약의 동행 세션 기능은 PostgreSQL 전환 후 사용할 수 있습니다.";

    private final CoreApiAppointmentClient appointmentClient;
    private final FirebaseBookingRepository firebaseRepository;

    public CoreApiBookingRepository(
            Context context,
            FirebaseBookingRepository firebaseRepository
    ) {
        this.appointmentClient = new CoreApiAppointmentClient(context);
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
        appointmentClient.getAppointment(requestId, new RepositoryCallback<AppointmentRequest>() {
            @Override
            public void onSuccess(AppointmentRequest request) {
                if (stopped.get()) {
                    return;
                }
                String legacyId = appointmentClient.getKnownLegacyFirestoreId(request.getId());
                if (legacyId.isEmpty()) {
                    callback.onSuccess(toCoreOnlyDetail(request));
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
                                appointmentClient.getAppointment(
                                        request.getId(),
                                        new RepositoryCallback<AppointmentRequest>() {
                                            @Override
                                            public void onSuccess(AppointmentRequest currentRequest) {
                                                if (!stopped.get()) {
                                                    callback.onSuccess(replaceRequest(
                                                            currentRequest,
                                                            legacyDetail));
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
        withLegacyId(requestId, callback, legacyId ->
                firebaseRepository.getAppointmentFollowUp(currentUser, legacyId, callback));
    }

    @Override
    public void saveAppointmentFollowUpReview(
            User currentUser,
            String requestId,
            AppointmentFollowUpReviewRating reviewRating,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    ) {
        withLegacyId(requestId, callback, legacyId ->
                firebaseRepository.saveAppointmentFollowUpReview(
                        currentUser,
                        legacyId,
                        reviewRating,
                        callback));
    }

    @Override
    public void saveAppointmentFollowUpSettlement(
            User currentUser,
            String requestId,
            AppointmentFollowUpSettlementStatus settlementStatus,
            String settlementNote,
            RepositoryCallback<AppointmentFollowUpRecord> callback
    ) {
        withLegacyId(requestId, callback, legacyId ->
                firebaseRepository.saveAppointmentFollowUpSettlement(
                        currentUser,
                        legacyId,
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
        withLegacyId(requestId, callback, legacyId ->
                firebaseRepository.saveAppointmentFollowUpSupportEscalation(
                        currentUser,
                        legacyId,
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
        withLegacyId(requestId, callback, legacyId ->
                firebaseRepository.sendCompanionChatMessage(
                        currentUser,
                        legacyId,
                        message,
                        attachments,
                        new RepositoryCallback<AppointmentRequestDetail>() {
                            @Override
                            public void onSuccess(AppointmentRequestDetail legacyDetail) {
                                appointmentClient.getAppointment(
                                        requestId,
                                        new RepositoryCallback<AppointmentRequest>() {
                                            @Override
                                            public void onSuccess(AppointmentRequest request) {
                                                callback.onSuccess(replaceRequest(
                                                        request,
                                                        legacyDetail));
                                            }

                                            @Override
                                            public void onError(String errorMessage) {
                                                callback.onError(errorMessage);
                                            }
                                        });
                            }

                            @Override
                            public void onError(String errorMessage) {
                                callback.onError(errorMessage);
                            }
                        }));
    }

    @Override
    public void markCompanionChatRead(User currentUser, String requestId) {
        appointmentClient.resolveLegacyFirestoreId(
                requestId,
                new RepositoryCallback<String>() {
                    @Override
                    public void onSuccess(String legacyId) {
                        if (!legacyId.isEmpty()) {
                            firebaseRepository.markCompanionChatRead(currentUser, legacyId);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        // 읽음 표시는 보조 동작이므로 화면 오류로 확장하지 않는다.
                    }
                });
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
            callback.onSuccess(toCoreOnlyDetail(request));
            return;
        }
        firebaseRepository.getAppointmentRequestDetail(
                currentUser,
                legacyId,
                new RepositoryCallback<AppointmentRequestDetail>() {
                    @Override
                    public void onSuccess(AppointmentRequestDetail legacyDetail) {
                        callback.onSuccess(replaceRequest(request, legacyDetail));
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    private AppointmentRequestDetail replaceRequest(
            AppointmentRequest request,
            AppointmentRequestDetail legacyDetail
    ) {
        return new AppointmentRequestDetail(
                request,
                legacyDetail.getPatient(),
                legacyDetail.getGuardian(),
                legacyDetail.getManager(),
                legacyDetail.getSession(),
                legacyDetail.getSessionReport(),
                legacyDetail.getHospitalGuide(),
                legacyDetail.getFollowUpRecord());
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
                null,
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

    private interface LegacyOperation {
        void run(String legacyId);
    }
}
