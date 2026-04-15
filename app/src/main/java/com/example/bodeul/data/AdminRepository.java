package com.example.bodeul.data;

import com.example.bodeul.domain.model.AdminDashboard;
import com.example.bodeul.domain.model.ManagerDocumentStatus;
import com.example.bodeul.domain.model.User;

import java.util.List;

/**
 * 관리자 화면에서 수동 매칭과 병원 가이드 관리를 담당하는 저장소 계약이다.
 */
public interface AdminRepository {
    // 현재 관리자 계정 기준으로 운영 현황과 가이드 목록을 조회한다.
    void getAdminDashboard(User currentUser, RepositoryCallback<AdminDashboard> callback);

    // 요청 한 건에 매니저를 배정하고 최신 운영 현황을 다시 반환한다.
    void assignManager(
            User currentUser,
            String requestId,
            String managerUserId,
            RepositoryCallback<AdminDashboard> callback
    );

    // 병원과 진료과 조합의 가이드를 저장하고 최신 운영 현황을 다시 반환한다.
    void saveHospitalGuide(
            User currentUser,
            String hospitalName,
            String departmentName,
            List<String> stepLines,
            RepositoryCallback<AdminDashboard> callback
    );

    // 등록된 병원 가이드 한 건을 삭제하고 최신 운영 현황을 다시 반환한다.
    void deleteHospitalGuide(
            User currentUser,
            String guideId,
            RepositoryCallback<AdminDashboard> callback
    );

    // 매니저 서류 검토 결과를 승인 또는 반려 상태로 저장하고 최신 운영 현황을 반환한다.
    void reviewManagerDocument(
            User currentUser,
            String managerUserId,
            ManagerDocumentStatus status,
            String reviewNote,
            RepositoryCallback<AdminDashboard> callback
    );

    // 화면에서 데모 모드 안내를 분기할 때 사용한다.
    boolean isFirebaseBacked();
}
