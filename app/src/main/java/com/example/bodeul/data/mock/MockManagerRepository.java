package com.example.bodeul.data.mock;

import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentHistoryEntry;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryCategory;
import com.example.bodeul.domain.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * 매니저 화면을 목업 데이터에 연결하는 저장소 구현이다.
 */
public class MockManagerRepository implements ManagerRepository {
    private final MockBodeulRepository repository;

    public MockManagerRepository(MockBodeulRepository repository) {
        this.repository = repository;
    }

    @Override
    public void getManagerDashboard(String managerUserId, RepositoryCallback<ManagerDashboard> callback) {
        ManagerDashboard dashboard = repository.getManagerDashboard(managerUserId);
        if (dashboard == null) {
            callback.onError(ManagerRepository.MESSAGE_NO_ACTIVE_SESSION);
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void advanceCurrentStep(String managerUserId, RepositoryCallback<ManagerDashboard> callback) {
        ManagerDashboard dashboard = repository.advanceManagerSession(managerUserId);
        if (dashboard == null) {
            callback.onError("다음 단계를 불러오지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void saveGuardianUpdate(String managerUserId, String guardianUpdate, RepositoryCallback<ManagerDashboard> callback) {
        ManagerDashboard dashboard = repository.updateGuardianMessage(managerUserId, guardianUpdate);
        if (dashboard == null) {
            callback.onError("보호자 공유 메시지를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void sendCompanionChatMessage(String managerUserId, String message, RepositoryCallback<ManagerDashboard> callback) {
        ManagerDashboard dashboard = repository.appendManagerCompanionChatMessage(managerUserId, message);
        if (dashboard == null) {
            callback.onError("안심 채팅 메시지를 전송하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void shareCurrentLocation(
            String managerUserId,
            double latitude,
            double longitude,
            String locationSummary,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        ManagerDashboard dashboard = repository.updateSharedLocation(
                managerUserId,
                latitude,
                longitude,
                locationSummary
        );
        if (dashboard == null) {
            callback.onError("?꾩옱 ?꾩튂瑜?怨듭쑀?섏? 紐삵뻽?듬땲??");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void saveLocationSummary(String managerUserId, String locationSummary, RepositoryCallback<ManagerDashboard> callback) {
        ManagerDashboard dashboard = repository.updateLocationSummary(managerUserId, locationSummary);
        if (dashboard == null) {
            callback.onError("위치 공유 메모를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void saveFieldPhotoNote(String managerUserId, String fieldPhotoNote, RepositoryCallback<ManagerDashboard> callback) {
        ManagerDashboard dashboard = repository.updateFieldPhotoNote(managerUserId, fieldPhotoNote);
        if (dashboard == null) {
            callback.onError("현장 사진 메모를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void saveMedicationNote(String managerUserId, String medicationNote, RepositoryCallback<ManagerDashboard> callback) {
        ManagerDashboard dashboard = repository.updateMedicationNote(managerUserId, medicationNote);
        if (dashboard == null) {
            callback.onError("복약 메모를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void savePharmacySummary(String managerUserId, String pharmacySummary, RepositoryCallback<ManagerDashboard> callback) {
        ManagerDashboard dashboard = repository.updatePharmacySummary(managerUserId, pharmacySummary);
        if (dashboard == null) {
            callback.onError("약국 진행 요약을 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void updatePharmacyCompleted(String managerUserId, boolean pharmacyCompleted, RepositoryCallback<ManagerDashboard> callback) {
        ManagerDashboard dashboard = repository.updatePharmacyCompleted(managerUserId, pharmacyCompleted);
        if (dashboard == null) {
            callback.onError("약국 단계 상태를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void getManagerHomeProfile(String managerUserId, RepositoryCallback<ManagerHomeProfile> callback) {
        ManagerHomeProfile profile = repository.getManagerHomeProfile(managerUserId);
        if (profile == null) {
            callback.onError("매니저 홈 요약 정보를 불러오지 못했습니다.");
            return;
        }
        callback.onSuccess(profile);
    }

    @Override
    public void getManagerDocumentOverview(
            String managerUserId,
            RepositoryCallback<ManagerDocumentOverview> callback
    ) {
        User manager = repository.findUserById(managerUserId);
        ManagerHomeProfile profile = repository.getManagerHomeProfile(managerUserId);
        if (manager == null || profile == null) {
            callback.onError("매니저 내 페이지 정보를 불러오지 못했습니다.");
            return;
        }

        List<ManagerDocumentHistoryEntry> historyEntries = repository.getManagerDocumentHistory(managerUserId);
        callback.onSuccess(new ManagerDocumentOverview(manager, profile, historyEntries));
    }

    @Override
    public void getManagerHistoryDetails(
            String managerUserId,
            RepositoryCallback<List<AppointmentRequestDetail>> callback
    ) {
        User manager = repository.findUserById(managerUserId);
        if (manager == null) {
            callback.onError("매니저 과거 동행 이력을 불러오지 못했습니다.");
            return;
        }

        List<AppointmentRequestDetail> historyDetails = new ArrayList<>();
        for (CompanionSession session : repository.getManagerSessions(managerUserId)) {
            if (session.getStatus() != SessionStatus.COMPLETED) {
                continue;
            }

            AppointmentRequest request = findRequest(session.getAppointmentRequestId());
            if (request == null) {
                continue;
            }

            SessionReport report = repository.getSessionReport(session.getId());
            historyDetails.add(new AppointmentRequestDetail(
                    request,
                    repository.findUserById(request.getPatientUserId()),
                    repository.findUserById(request.getGuardianUserId()),
                    manager,
                    session,
                    report,
                    repository.getHospitalGuide(request.getHospitalName(), request.getDepartmentName()),
                    repository.getAppointmentFollowUpRecord(request.getId())
            ));
        }
        callback.onSuccess(historyDetails);
    }

    @Override
    public void saveManagerDocumentSummary(
            String managerUserId,
            String documentSummary,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        ManagerHomeProfile profile = repository.saveManagerDocumentSummary(managerUserId, documentSummary);
        if (profile == null) {
            callback.onError("서류 등록 정보를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(profile);
    }

    @Override
    public void saveManagerDocumentFileMetadata(
            String managerUserId,
            ManagerDocumentFileMetadata documentFileMetadata,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        ManagerHomeProfile profile = repository.saveManagerDocumentFileMetadata(
                managerUserId,
                documentFileMetadata
        );
        if (profile == null) {
            callback.onError("원본 서류 파일 정보를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(profile);
    }

    @Override
    public void saveManagerDocumentDraftFileMetadata(
            String managerUserId,
            ManagerDocumentFileMetadata documentFileMetadata,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        ManagerHomeProfile profile = repository.saveManagerDocumentDraftFileMetadata(
                managerUserId,
                documentFileMetadata
        );
        if (profile == null) {
            callback.onError("원본 서류 파일 초안 정보를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(profile);
    }

    @Override
    public void saveManagerAvailabilitySummary(
            String managerUserId,
            String availabilitySummary,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        ManagerHomeProfile profile = repository.saveManagerAvailabilitySummary(managerUserId, availabilitySummary);
        if (profile == null) {
            callback.onError("선호 가능 일정을 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(profile);
    }

    @Override
    public void submitSessionReport(
            String managerUserId,
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String nextVisitAt,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        ManagerDashboard dashboard = repository.saveSessionReport(
                managerUserId,
                summary,
                treatmentNotes,
                medicationNotes,
                nextVisitAt
        );
        if (dashboard == null) {
            callback.onError("리포트를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void getSupportInquiries(
            String managerUserId,
            RepositoryCallback<List<SupportInquiry>> callback
    ) {
        callback.onSuccess(repository.getSupportInquiries(managerUserId));
    }

    @Override
    public void submitSupportInquiry(
            String managerUserId,
            SupportInquiryCategory category,
            String title,
            String body,
            RepositoryCallback<List<SupportInquiry>> callback
    ) {
        SupportInquiry inquiry = repository.saveSupportInquiry(
                managerUserId,
                category,
                title,
                body
        );
        if (inquiry == null) {
            callback.onError("문의 내용을 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(repository.getSupportInquiries(managerUserId));
    }

    @Override
    public boolean isFirebaseBacked() {
        return false;
    }

    private AppointmentRequest findRequest(String requestId) {
        for (AppointmentRequest request : repository.getAppointmentRequests()) {
            if (request.getId().equals(requestId)) {
                return request;
            }
        }
        return null;
    }
}
