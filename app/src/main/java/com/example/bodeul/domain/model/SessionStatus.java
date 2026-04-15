package com.example.bodeul.domain.model;

/**
 * 동행 세션이 현재 어떤 단계에 있는지 표현한다.
 */
public enum SessionStatus {
    // 동행 시작 전 준비만 된 상태다.
    READY,
    // 환자와 만나 이동하거나 접촉 중인 상태다.
    MEETING,
    // 접수 또는 진료 대기 중인 상태다.
    WAITING,
    // 진료실 또는 검사 진행 중인 상태다.
    IN_TREATMENT,
    // 수납이나 후속 행정 처리를 하는 상태다.
    PAYMENT,
    // 매칭 이후 일정이 취소되어 더 이상 진행하지 않는 상태다.
    CANCELED,
    // 전체 동행 절차가 완료된 상태다.
    COMPLETED
}
