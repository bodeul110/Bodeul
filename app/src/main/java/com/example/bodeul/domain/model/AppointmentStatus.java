package com.example.bodeul.domain.model;

/**
 * 병원 동행 요청의 현재 처리 상태를 나타낸다.
 */
public enum AppointmentStatus {
    // 보호자 또는 환자가 동행을 신청한 직후 상태다.
    REQUESTED,
    // 매니저가 배정되어 매칭이 완료된 상태다.
    MATCHED,
    // 실제 병원 동행이 진행 중인 상태다.
    IN_PROGRESS,
    // 동행과 리포트 작성까지 모두 끝난 상태다.
    COMPLETED,
    // 사용자가 요청을 취소했거나 진행이 중단된 상태다.
    CANCELED
}
