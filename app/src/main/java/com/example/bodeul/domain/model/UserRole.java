package com.example.bodeul.domain.model;

/**
 * 보들 앱에서 지원하는 사용자 역할 목록이다.
 */
public enum UserRole {
    // 병원 동행 서비스를 신청하는 환자다.
    PATIENT,
    // 환자 대신 신청과 확인을 수행하는 보호자다.
    GUARDIAN,
    // 실제 병원 동행을 수행하는 매니저다.
    MANAGER,
    // 운영용 관리 기능을 담당하는 관리자다.
    ADMIN
}
