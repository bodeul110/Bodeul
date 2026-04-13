package com.example.bodeul.data.mock;

import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.ManagerDashboard;

/**
 * 매니저 화면을 데모 데이터에 연결하는 목업 저장소 구현이다.
 */
public class MockManagerRepository implements ManagerRepository {
    private final MockBodeulRepository repository;

    public MockManagerRepository(MockBodeulRepository repository) {
        this.repository = repository;
    }

    @Override
    public void getManagerDashboard(String managerUserId, RepositoryCallback<ManagerDashboard> callback) {
        // 목업 저장소에서 바로 대시보드를 꺼내 화면에 전달한다.
        ManagerDashboard dashboard = repository.getManagerDashboard(managerUserId);
        if (dashboard == null) {
            callback.onError("매니저 세션을 찾지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void advanceCurrentStep(String managerUserId, RepositoryCallback<ManagerDashboard> callback) {
        // 단계 이동 결과를 다시 대시보드 형태로 돌려줘 화면을 즉시 갱신한다.
        ManagerDashboard dashboard = repository.advanceManagerSession(managerUserId);
        if (dashboard == null) {
            callback.onError("다음 단계를 불러오지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void saveGuardianUpdate(String managerUserId, String guardianUpdate, RepositoryCallback<ManagerDashboard> callback) {
        // 보호자 공유 문구를 저장한 뒤 최신 세션 상태를 반환한다.
        ManagerDashboard dashboard = repository.updateGuardianMessage(managerUserId, guardianUpdate);
        if (dashboard == null) {
            callback.onError("보호자 공유 메시지를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void saveMedicationNote(String managerUserId, String medicationNote, RepositoryCallback<ManagerDashboard> callback) {
        // 복약 메모 저장도 같은 대시보드 응답 구조를 유지한다.
        ManagerDashboard dashboard = repository.updateMedicationNote(managerUserId, medicationNote);
        if (dashboard == null) {
            callback.onError("복약 메모를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
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
        // 리포트 저장이 끝나면 종료 상태가 반영된 대시보드를 다시 내려준다.
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
    public boolean isFirebaseBacked() {
        return false;
    }
}
