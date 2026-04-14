package com.example.bodeul.data;

import com.example.bodeul.domain.model.ManagerDashboard;

/**
 * 매니저 홈과 동행 가이드 화면에서 사용하는 기능 전용 저장소 계약이다.
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

    // 진료 이후 약 수령 및 복약 관련 메모를 저장한다.
    void saveMedicationNote(String managerUserId, String medicationNote, RepositoryCallback<ManagerDashboard> callback);

    // 동행 종료 후 보호자용 리포트를 저장하고 최신 대시보드를 반환한다.
    void submitSessionReport(
            String managerUserId,
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String nextVisitAt,
            RepositoryCallback<ManagerDashboard> callback
    );

    // 화면에서 데모 모드 안내를 분기할 때 사용한다.
    boolean isFirebaseBacked();
}
