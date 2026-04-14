package com.example.bodeul.data;

import com.example.bodeul.domain.model.GuardianReportDashboard;
import com.example.bodeul.domain.model.User;

/**
 * 보호자가 진행 현황과 최종 리포트를 조회할 때 사용하는 저장소 계약이다.
 */
public interface GuardianReportRepository {
    // 현재 로그인한 보호자 기준으로 요청, 세션, 리포트를 한 번에 조회한다.
    void getGuardianDashboard(User currentUser, RepositoryCallback<GuardianReportDashboard> callback);

    // 화면에서 데모 모드 안내를 분기할 때 사용한다.
    boolean isFirebaseBacked();
}
