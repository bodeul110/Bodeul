package com.example.bodeul.data;

import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryCategory;

import java.util.List;

/**
 * 매니저 홈, 내 페이지, 과거 이력 화면에서 사용하는 기능 전용 저장소 계약이다.
 */
public interface ManagerRepository {
    // 매니저에게 아직 배정된 동행 세션이 없을 때 공통으로 사용하는 안내 문구다.
    String MESSAGE_NO_ACTIVE_SESSION = "현재 배정된 동행 일정이 없습니다.";

    // 매니저 홈과 가이드 화면에 필요한 묶음 데이터를 한 번에 불러온다.
    void getManagerDashboard(String managerUserId, RepositoryCallback<ManagerDashboard> callback);

    // 현재 단계 진행 상태를 다음 단계로 넘긴다.
    void advanceCurrentStep(String managerUserId, RepositoryCallback<ManagerDashboard> callback);

    // 보호자에게 공유할 현장 메시지를 저장한다.
    void saveGuardianUpdate(String managerUserId, String guardianUpdate, RepositoryCallback<ManagerDashboard> callback);

    // 현재 위치와 이동 상황 요약을 세션에 저장한다.
    void saveLocationSummary(String managerUserId, String locationSummary, RepositoryCallback<ManagerDashboard> callback);

    // 현장 사진이나 서류 확인 메모를 세션에 저장한다.
    void saveFieldPhotoNote(String managerUserId, String fieldPhotoNote, RepositoryCallback<ManagerDashboard> callback);

    // 진료 이후 특이사항과 복약 관련 메모를 저장한다.
    void saveMedicationNote(String managerUserId, String medicationNote, RepositoryCallback<ManagerDashboard> callback);

    // 내 페이지에서 보여줄 서류 및 일정 요약 정보를 불러온다.
    void getManagerHomeProfile(String managerUserId, RepositoryCallback<ManagerHomeProfile> callback);

    // 내 페이지에서 계정 정보와 서류 검토 이력을 함께 보여줄 묶음 정보를 불러온다.
    void getManagerDocumentOverview(String managerUserId, RepositoryCallback<ManagerDocumentOverview> callback);

    // 완료된 동행 이력 화면에서 예약, 세션, 리포트를 묶은 목록을 불러온다.
    void getManagerHistoryDetails(
            String managerUserId,
            RepositoryCallback<List<AppointmentRequestDetail>> callback
    );

    // 내 페이지에서 매니저 서류 등록 요약을 저장한다.
    void saveManagerDocumentSummary(
            String managerUserId,
            String documentSummary,
            RepositoryCallback<ManagerHomeProfile> callback
    );

    // 내 페이지에서 매니저 원본 서류 파일 메타데이터를 저장한다.
    void saveManagerDocumentFileMetadata(
            String managerUserId,
            ManagerDocumentFileMetadata documentFileMetadata,
            RepositoryCallback<ManagerHomeProfile> callback
    );

    // 서류 등록 전용 화면에서 심사 요청 전 단계의 원본 파일 초안을 저장한다.
    void saveManagerDocumentDraftFileMetadata(
            String managerUserId,
            ManagerDocumentFileMetadata documentFileMetadata,
            RepositoryCallback<ManagerHomeProfile> callback
    );

    // 내 페이지에서 활동 가능 일정 요약을 저장한다.
    void saveManagerAvailabilitySummary(
            String managerUserId,
            String availabilitySummary,
            RepositoryCallback<ManagerHomeProfile> callback
    );

    // 동행 종료 후 보호자용 리포트를 저장하고 최신 대시보드를 반환한다.
    void submitSessionReport(
            String managerUserId,
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String nextVisitAt,
            RepositoryCallback<ManagerDashboard> callback
    );

    // 문의하기 화면에 노출할 문의 목록을 불러온다.
    void getSupportInquiries(
            String managerUserId,
            RepositoryCallback<List<SupportInquiry>> callback
    );

    // 매니저 문의를 저장하고 최신 문의 목록을 반환한다.
    void submitSupportInquiry(
            String managerUserId,
            SupportInquiryCategory category,
            String title,
            String body,
            RepositoryCallback<List<SupportInquiry>> callback
    );

    // 화면에서 데모 및 서비스 모드 안내를 분기하기 위해 사용한다.
    boolean isFirebaseBacked();
}
