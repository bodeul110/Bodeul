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
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * 세션 진행과 리포트는 Core API를 사용하고 채팅·위치·매니저 운영 정보는 Firebase에 유지한다.
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
        firebaseRepository.getManagerDashboard(managerUserId, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                overlayDashboard(result, callback);
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
        firebaseRepository.sendCompanionChatMessage(
                managerUserId,
                message,
                attachments,
                overlayCallback(callback));
    }

    @Override
    public void markCompanionChatRead(String managerUserId) {
        firebaseRepository.markCompanionChatRead(managerUserId);
    }

    @Override
    public void saveCompanionLocationAlert(String managerUserId, CompanionLocationAlertStage stage) {
        firebaseRepository.saveCompanionLocationAlert(managerUserId, stage);
    }

    @Override
    public void shareCurrentLocation(
            String managerUserId,
            double latitude,
            double longitude,
            String locationSummary,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        firebaseRepository.shareCurrentLocation(
                managerUserId,
                latitude,
                longitude,
                locationSummary,
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        sessionClient.updateText(
                                result.getSession().getId(),
                                "locationSummary",
                                locationSummary,
                                refreshCallback(managerUserId, callback));
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    @Override
    public void updateLiveLocationSharingState(
            String managerUserId,
            boolean active,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        firebaseRepository.updateLiveLocationSharingState(
                managerUserId,
                active,
                overlayCallback(callback));
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
        firebaseRepository.getManagerHistoryDetails(
                managerUserId,
                new RepositoryCallback<List<AppointmentRequestDetail>>() {
                    @Override
                    public void onSuccess(List<AppointmentRequestDetail> result) {
                        overlayHistory(result, 0, new ArrayList<>(), callback);
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
            RepositoryCallback<ManagerDashboard> callback
    ) {
        return new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                overlayDashboard(result, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }

    private void overlayDashboard(
            ManagerDashboard dashboard,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        appointmentClient.getAppointment(
                dashboard.getAppointmentRequest().getId(),
                new RepositoryCallback<AppointmentRequest>() {
                    @Override
                    public void onSuccess(AppointmentRequest appointment) {
                        sessionClient.findSession(
                                dashboard.getSession().getId(),
                                null,
                                new RepositoryCallback<CoreApiCompanionSessionClient.SessionSnapshot>() {
                                    @Override
                                    public void onSuccess(
                                            CoreApiCompanionSessionClient.SessionSnapshot sessionSnapshot
                                    ) {
                                        if (sessionSnapshot == null) {
                                            callback.onError("PostgreSQL 동행 세션 정보를 찾지 못했습니다.");
                                            return;
                                        }
                                        CompanionSession session = sessionSnapshot.merge(
                                                dashboard.getSession(),
                                                appointment.getId());
                                        if (sessionSnapshot.getStatus() != SessionStatus.COMPLETED) {
                                            callback.onSuccess(copyDashboard(
                                                    dashboard,
                                                    appointment,
                                                    session,
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
                                                        callback.onSuccess(copyDashboard(
                                                                dashboard,
                                                                appointment,
                                                                session,
                                                                report.toModel(session.getId())));
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

    private ManagerDashboard copyDashboard(
            ManagerDashboard source,
            AppointmentRequest appointment,
            CompanionSession session,
            SessionReport report
    ) {
        return new ManagerDashboard(
                source.getManager(),
                source.getPatient(),
                source.getGuardian(),
                appointment,
                session,
                source.getHospitalGuide(),
                report);
    }

    private void overlayHistory(
            List<AppointmentRequestDetail> source,
            int index,
            List<AppointmentRequestDetail> output,
            RepositoryCallback<List<AppointmentRequestDetail>> callback
    ) {
        if (index >= source.size()) {
            callback.onSuccess(output);
            return;
        }
        AppointmentRequestDetail detail = source.get(index);
        if (detail.getSession() == null) {
            output.add(detail);
            overlayHistory(source, index + 1, output, callback);
            return;
        }
        appointmentClient.getAppointment(
                detail.getAppointmentRequest().getId(),
                new RepositoryCallback<AppointmentRequest>() {
                    @Override
                    public void onSuccess(AppointmentRequest appointment) {
                        sessionClient.findSession(
                                detail.getSession().getId(),
                                null,
                                new RepositoryCallback<CoreApiCompanionSessionClient.SessionSnapshot>() {
                                    @Override
                                    public void onSuccess(
                                            CoreApiCompanionSessionClient.SessionSnapshot sessionSnapshot
                                    ) {
                                        if (sessionSnapshot == null) {
                                            callback.onError("PostgreSQL 동행 이력을 찾지 못했습니다.");
                                            return;
                                        }
                                        CompanionSession session = sessionSnapshot.merge(
                                                detail.getSession(),
                                                appointment.getId());
                                        if (sessionSnapshot.getStatus() != SessionStatus.COMPLETED) {
                                            output.add(copyDetail(detail, appointment, session, null));
                                            overlayHistory(source, index + 1, output, callback);
                                            return;
                                        }
                                        sessionClient.getReport(
                                                sessionSnapshot,
                                                new RepositoryCallback<CoreApiCompanionSessionClient.ReportSnapshot>() {
                                                    @Override
                                                    public void onSuccess(
                                                            CoreApiCompanionSessionClient.ReportSnapshot report
                                                    ) {
                                                        output.add(copyDetail(
                                                                detail,
                                                                appointment,
                                                                session,
                                                                report.toModel(session.getId())));
                                                        overlayHistory(
                                                                source,
                                                                index + 1,
                                                                output,
                                                                callback);
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

    private AppointmentRequestDetail copyDetail(
            AppointmentRequestDetail source,
            AppointmentRequest appointment,
            CompanionSession session,
            SessionReport report
    ) {
        return new AppointmentRequestDetail(
                appointment,
                source.getPatient(),
                source.getGuardian(),
                source.getManager(),
                session,
                report,
                source.getHospitalGuide(),
                source.getFollowUpRecord());
    }

    private interface DashboardOperation {
        void run(ManagerDashboard dashboard);
    }
}
