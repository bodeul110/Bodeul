package com.example.bodeul.domain.model;

/**
 * 매니저가 제출한 서류 요약의 검토 상태를 나타낸다.
 */
public enum ManagerDocumentStatus {
    NOT_SUBMITTED,
    PENDING_REVIEW,
    APPROVED,
    REJECTED
}
