package com.example.bodeul.data;

import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.User;

import java.util.List;

/**
 * 앱 전반에서 공통으로 사용하는 기본 조회용 데이터 저장소 계약이다.
 */
public interface BodeulRepository {
    // 앱에 등록된 사용자 목록을 조회한다.
    List<User> getUsers();

    // 환자/보호자의 병원 동행 신청 목록을 조회한다.
    List<AppointmentRequest> getAppointmentRequests();

    // 특정 매니저에게 배정된 동행 세션 목록을 조회한다.
    List<CompanionSession> getManagerSessions(String managerUserId);

    // 병원과 진료과 조합에 맞는 동행 가이드를 조회한다.
    HospitalGuide getHospitalGuide(String hospitalName, String departmentName);

    // 세션 종료 후 저장된 리포트를 조회한다.
    SessionReport getSessionReport(String sessionId);
}
