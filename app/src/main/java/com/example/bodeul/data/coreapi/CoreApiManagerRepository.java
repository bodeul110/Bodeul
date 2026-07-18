package com.example.bodeul.data.coreapi;

import android.content.Context;

import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.firebase.FirebaseManagerRepository;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.domain.model.CompanionLocationAlertStage;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.HospitalGuideFallbackFactory;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryCategory;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import java.util.ArrayList;
import java.util.List;

/**
 * 예약과 동행 운영 데이터는 Core API를 사용하고, 매니저 서류 등 아직 이전하지 않은 기능만 Firebase에 유지한다.
 */
public final class CoreApiManagerRepository implements ManagerRepository {
    private final FirebaseManagerRepository firebaseRepository;
    private final CoreApiAppointmentClient appointmentClient;
    private final CoreApiCompanionSessionClient sessionClient;

    public CoreApiManagerRepository(
            Context context,
            FirebaseManagerRepository firebaseRepository
    ) {
        this.firebaseRepository = firebaseRepository;
        this.appointmentClient = new CoreApiAppointmentClient(context);
        this.sessionClient = new CoreApiCompanionSessionClient(context);
    }

    @Override
    public void getManagerDashboard(String managerUserId, RepositoryCallback<ManagerDashboard> callback) {
        sessionClient.getSessions(new RepositoryCallback<List<CoreApiCompanionSessionClient.SessionSnapshot>>() {
            @Override
            public void onSuccess(List<CoreApiCompanionSessionClient.SessionSnapshot> sessions) {
                CoreApiCompanionSessionClient.SessionSnapshot activeSession = findActiveSession(sessions);
                if (activeSession == null) {
                    callback.onError(ManagerRepository.MESSAGE_NO_ACTIVE_SESSION);
                    return;
                }
                loadDashboard(activeSession, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void advanceCurrentStep(String managerUserId, RepositoryCallback<ManagerDashboard> callback) {
        withDashboard(managerUserId, callback, dashboard -> sessionClient.advance(
                dashboard.getSession().getId(),
                refreshCallback(managerUserId, callback)));
    }

    @Override
    public void saveGuardianUpdate(
            String managerUserId,
            String guardianUpdate,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        updateSessionText(managerUserId, "guardianUpdate", guardianUpdate, callback);
    }

    @Override
    public void sendCompanionChatMessage(
            String managerUserId,
            String message,
            List<CompanionChatAttachment> attachments,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        withDashboard(managerUserId, callback, dashboard ->
                sessionClient.sendRealtimeMessage(
                        dashboard.getSession().getId(),
                        message,
                        attachments,
                        new RepositoryCallback<CoreApiCompanionSessionClient.RealtimeSnapshot>() {
                            @Override
                            public void onSuccess(
                                    CoreApiCompanionSessionClient.RealtimeSnapshot result
                            ) {
                                getManagerDashboard(managerUserId, callback);
                            }

                            @Override
                            public void onError(String errorMessage) {
                                callback.onError(errorMessage);
                            }
                        }));
    }

    @Override
    public void markCompanionChatRead(String managerUserId) {
        getManagerDashboard(managerUserId, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                sessionClient.markRealtimeRead(result.getSession().getId());
            }

            @Override
            public void onError(String message) {
                // 읽음 표시는 화면 오류로 확장하지 않는다.
            }
        });
    }

    @Override
    public void saveCompanionLocationAlert(String managerUserId, CompanionLocationAlertStage stage) {
        getManagerDashboard(managerUserId, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                sessionClient.updateText(
                        result.getSession().getId(),
                        "locationAlertStage",
                        stage == null ? CompanionLocationAlertStage.NONE.getValue() : stage.getValue(),
                        new RepositoryCallback<CoreApiCompanionSessionClient.SessionSnapshot>() {
                            @Override
                            public void onSuccess(CoreApiCompanionSessionClient.SessionSnapshot ignored) {
                                // 자동 알림 발송 상태는 다음 대시보드 조회에서 동기화한다.
                            }

                            @Override
                            public void onError(String message) {
                                // 알림 자체의 성공 흐름을 저장 실패로 되돌리지는 않는다.
                            }
                        });
            }

            @Override
            public void onError(String message) {
                // 활성 세션이 없으면 저장할 자동 알림 상태도 없다.
            }
        });
    }

    @Override
    public void shareCurrentLocation(
            String managerUserId,
            double latitude,
            double longitude,
            String locationSummary,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        withDashboard(managerUserId, callback, dashboard -> sessionClient.updateText(
                dashboard.getSession().getId(),
                "locationSummary",
                locationSummary,
                new RepositoryCallback<CoreApiCompanionSessionClient.SessionSnapshot>() {
                    @Override
                    public void onSuccess(CoreApiCompanionSessionClient.SessionSnapshot result) {
                        sessionClient.shareRealtimeLocation(
                                result.getCoreId(),
                                latitude,
                                longitude,
                                new RepositoryCallback<CoreApiCompanionSessionClient.RealtimeSnapshot>() {
                                    @Override
                                    public void onSuccess(
                                            CoreApiCompanionSessionClient.RealtimeSnapshot ignored
                                    ) {
                                        getManagerDashboard(managerUserId, callback);
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
                }));
    }

    @Override
    public void updateLiveLocationSharingState(
            String managerUserId,
            boolean active,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        updateSessionBoolean(
                managerUserId,
                "liveLocationSharingActive",
                active,
                callback);
    }

    @Override
    public void saveLocationSummary(
            String managerUserId,
            String locationSummary,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        updateSessionText(managerUserId, "locationSummary", locationSummary, callback);
    }

    @Override
    public void saveFieldPhotoNote(
            String managerUserId,
            String fieldPhotoNote,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        updateSessionText(managerUserId, "fieldPhotoNote", fieldPhotoNote, callback);
    }

    @Override
    public void saveMedicationNote(
            String managerUserId,
            String medicationNote,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        updateSessionText(managerUserId, "medicationNote", medicationNote, callback);
    }

    @Override
    public void savePharmacySummary(
            String managerUserId,
            String pharmacySummary,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        updateSessionText(managerUserId, "pharmacySummary", pharmacySummary, callback);
    }

    @Override
    public void updatePrescriptionCollected(
            String managerUserId,
            boolean prescriptionCollected,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        updateSessionBoolean(
                managerUserId,
                "prescriptionCollected",
                prescriptionCollected,
                callback);
    }

    @Override
    public void updatePharmacyCompleted(
            String managerUserId,
            boolean pharmacyCompleted,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        updateSessionBoolean(managerUserId, "pharmacyCompleted", pharmacyCompleted, callback);
    }

    @Override
    public void updateMedicationGuidanceCompleted(
            String managerUserId,
            boolean medicationGuidanceCompleted,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        updateSessionBoolean(
                managerUserId,
                "medicationGuidanceCompleted",
                medicationGuidanceCompleted,
                callback);
    }

    @Override
    public void getManagerHomeProfile(
            String managerUserId,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        firebaseRepository.getManagerHomeProfile(managerUserId, callback);
    }

    @Override
    public void getManagerDocumentOverview(
            String managerUserId,
            RepositoryCallback<ManagerDocumentOverview> callback
    ) {
        firebaseRepository.getManagerDocumentOverview(managerUserId, callback);
    }

    @Override
    public void getManagerHistoryDetails(
            String managerUserId,
            RepositoryCallback<List<AppointmentRequestDetail>> callback
    ) {
        sessionClient.getSessions(new RepositoryCallback<List<CoreApiCompanionSessionClient.SessionSnapshot>>() {
                    @Override
                    public void onSuccess(
                            List<CoreApiCompanionSessionClient.SessionSnapshot> sessions
                    ) {
                        List<CoreApiCompanionSessionClient.SessionSnapshot> completed = new ArrayList<>();
                        for (CoreApiCompanionSessionClient.SessionSnapshot session : sessions) {
                            if (session.getStatus() == SessionStatus.COMPLETED) {
                                completed.add(session);
                            }
                        }
                        loadHistory(completed, 0, new ArrayList<>(), callback);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    @Override
    public void saveManagerDocumentSummary(
            String managerUserId,
            String documentSummary,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        firebaseRepository.saveManagerDocumentSummary(managerUserId, documentSummary, callback);
    }

    @Override
    public void saveManagerDocumentFileMetadata(
            String managerUserId,
            ManagerDocumentFileMetadata documentFileMetadata,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        firebaseRepository.saveManagerDocumentFileMetadata(
                managerUserId,
                documentFileMetadata,
                callback);
    }

    @Override
    public void saveManagerDocumentDraftFileMetadata(
            String managerUserId,
            ManagerDocumentFileMetadata documentFileMetadata,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        firebaseRepository.saveManagerDocumentDraftFileMetadata(
                managerUserId,
                documentFileMetadata,
                callback);
    }

    @Override
    public void saveManagerAvailabilitySummary(
            String managerUserId,
            String availabilitySummary,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        firebaseRepository.saveManagerAvailabilitySummary(
                managerUserId,
                availabilitySummary,
                callback);
    }

    @Override
    public void submitSessionReport(
            String managerUserId,
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String medicationName,
            String medicationChangeSummary,
            String medicationScheduleNote,
            MedicationComparisonDecision medicationComparisonDecision,
            String medicationComparisonNote,
            String nextVisitAt,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        withDashboard(managerUserId, callback, dashboard -> sessionClient.submitReport(
                dashboard.getSession().getId(),
                summary,
                treatmentNotes,
                medicationNotes,
                medicationName,
                medicationChangeSummary,
                medicationScheduleNote,
                medicationComparisonDecision,
                medicationComparisonNote,
                nextVisitAt,
                new RepositoryCallback<CoreApiCompanionSessionClient.ReportSnapshot>() {
                    @Override
                    public void onSuccess(CoreApiCompanionSessionClient.ReportSnapshot result) {
                        getManagerDashboard(managerUserId, callback);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                }));
    }

    @Override
    public void getSupportInquiries(
            String managerUserId,
            RepositoryCallback<List<SupportInquiry>> callback
    ) {
        firebaseRepository.getSupportInquiries(managerUserId, callback);
    }

    @Override
    public void submitSupportInquiry(
            String managerUserId,
            SupportInquiryCategory category,
            String title,
            String body,
            RepositoryCallback<List<SupportInquiry>> callback
    ) {
        firebaseRepository.submitSupportInquiry(managerUserId, category, title, body, callback);
    }

    @Override
    public boolean isFirebaseBacked() {
        return true;
    }

    private void updateSessionText(
            String managerUserId,
            String field,
            String value,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        withDashboard(managerUserId, callback, dashboard -> sessionClient.updateText(
                dashboard.getSession().getId(),
                field,
                value,
                refreshCallback(managerUserId, callback)));
    }

    private void updateSessionBoolean(
            String managerUserId,
            String field,
            boolean value,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        withDashboard(managerUserId, callback, dashboard -> sessionClient.updateBoolean(
                dashboard.getSession().getId(),
                field,
                value,
                refreshCallback(managerUserId, callback)));
    }

    private void withDashboard(
            String managerUserId,
            RepositoryCallback<ManagerDashboard> callback,
            DashboardOperation operation
    ) {
        getManagerDashboard(managerUserId, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                operation.run(result);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private RepositoryCallback<CoreApiCompanionSessionClient.SessionSnapshot> refreshCallback(
            String managerUserId,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        return new RepositoryCallback<CoreApiCompanionSessionClient.SessionSnapshot>() {
            @Override
            public void onSuccess(CoreApiCompanionSessionClient.SessionSnapshot result) {
                getManagerDashboard(managerUserId, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }

    private RepositoryCallback<ManagerDashboard> overlayCallback(
            String managerUserId,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        return new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                getManagerDashboard(managerUserId, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }

    private CoreApiCompanionSessionClient.SessionSnapshot findActiveSession(
            List<CoreApiCompanionSessionClient.SessionSnapshot> sessions
    ) {
        for (CoreApiCompanionSessionClient.SessionSnapshot session : sessions) {
            if (session.getStatus() != SessionStatus.COMPLETED
                    && session.getStatus() != SessionStatus.CANCELED) {
                return session;
            }
        }
        return null;
    }

    private void loadDashboard(
            CoreApiCompanionSessionClient.SessionSnapshot sessionSnapshot,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        appointmentClient.getAppointment(
                sessionSnapshot.getCoreAppointmentId(),
                new RepositoryCallback<AppointmentRequest>() {
                    @Override
                    public void onSuccess(AppointmentRequest appointment) {
                        CompanionSession session = sessionSnapshot.merge(null, appointment.getId());
                        sessionClient.enrichWithRealtime(
                                sessionSnapshot,
                                session,
                                new RepositoryCallback<CompanionSession>() {
                                    @Override
                                    public void onSuccess(CompanionSession realtimeSession) {
                                        callback.onSuccess(new ManagerDashboard(
                                                toManager(appointment),
                                                toParticipant(
                                                        appointment.getPatientUserId(),
                                                        UserRole.PATIENT,
                                                        appointment.getPatientName(),
                                                        appointment.getPatientEmail(),
                                                        appointment.getPatientPhone()),
                                                toParticipant(
                                                        appointment.getGuardianUserId(),
                                                        UserRole.GUARDIAN,
                                                        appointment.getGuardianName(),
                                                        appointment.getGuardianEmail(),
                                                        appointment.getGuardianPhone()),
                                                appointment,
                                                realtimeSession,
                                                HospitalGuideFallbackFactory.create(
                                                        appointment.getHospitalName(),
                                                        appointment.getDepartmentName()),
                                                null));
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

    private void loadHistory(
            List<CoreApiCompanionSessionClient.SessionSnapshot> sessions,
            int index,
            List<AppointmentRequestDetail> output,
            RepositoryCallback<List<AppointmentRequestDetail>> callback
    ) {
        if (index >= sessions.size()) {
            output.sort((left, right) -> right.getAppointmentRequest()
                    .getAppointmentAt()
                    .compareTo(left.getAppointmentRequest().getAppointmentAt()));
            callback.onSuccess(output);
            return;
        }
        CoreApiCompanionSessionClient.SessionSnapshot sessionSnapshot = sessions.get(index);
        appointmentClient.getAppointment(
                sessionSnapshot.getCoreAppointmentId(),
                new RepositoryCallback<AppointmentRequest>() {
                    @Override
                    public void onSuccess(AppointmentRequest appointment) {
                        CompanionSession session = sessionSnapshot.merge(null, appointment.getId());
                        sessionClient.getReport(
                                sessionSnapshot,
                                new RepositoryCallback<CoreApiCompanionSessionClient.ReportSnapshot>() {
                                    @Override
                                    public void onSuccess(
                                            CoreApiCompanionSessionClient.ReportSnapshot report
                                    ) {
                                        output.add(toHistoryDetail(
                                                appointment,
                                                session,
                                                report.toModel(session.getId())));
                                        loadHistory(sessions, index + 1, output, callback);
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

    private AppointmentRequestDetail toHistoryDetail(
            AppointmentRequest appointment,
            CompanionSession session,
            SessionReport report
    ) {
        return new AppointmentRequestDetail(
                appointment,
                toParticipant(
                        appointment.getPatientUserId(),
                        UserRole.PATIENT,
                        appointment.getPatientName(),
                        appointment.getPatientEmail(),
                        appointment.getPatientPhone()),
                toParticipant(
                        appointment.getGuardianUserId(),
                        UserRole.GUARDIAN,
                        appointment.getGuardianName(),
                        appointment.getGuardianEmail(),
                        appointment.getGuardianPhone()),
                toManager(appointment),
                session,
                report,
                HospitalGuideFallbackFactory.create(
                        appointment.getHospitalName(),
                        appointment.getDepartmentName()),
                null);
    }

    private User toManager(AppointmentRequest appointment) {
        String name = appointment.getManagerName().isEmpty()
                ? "배정 매니저"
                : appointment.getManagerName();
        return toParticipant(
                appointment.getManagerUserId(),
                UserRole.MANAGER,
                name,
                appointment.getManagerEmail(),
                appointment.getManagerPhone());
    }

    private User toParticipant(
            String userId,
            UserRole role,
            String name,
            String email,
            String phone
    ) {
        return new User(userId, role, name, email, phone);
    }

    private interface DashboardOperation {
        void run(ManagerDashboard dashboard);
    }
}
