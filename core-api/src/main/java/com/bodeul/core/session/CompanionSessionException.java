package com.bodeul.core.session;

import org.springframework.http.HttpStatus;

final class CompanionSessionException extends RuntimeException {

    private final HttpStatus status;
    private final String error;

    private CompanionSessionException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    static CompanionSessionException invalidRequest(String message) {
        return new CompanionSessionException(
                HttpStatus.BAD_REQUEST,
                "invalid_companion_session_request",
                message);
    }

    static CompanionSessionException permissionDenied() {
        return new CompanionSessionException(
                HttpStatus.FORBIDDEN,
                "companion_session_permission_denied",
                "이 동행 세션을 조회하거나 변경할 권한이 없습니다.");
    }

    static CompanionSessionException roleNotSupported() {
        return new CompanionSessionException(
                HttpStatus.FORBIDDEN,
                "companion_session_role_not_supported",
                "환자, 보호자 또는 배정된 매니저 계정으로 접근해 주세요.");
    }

    static CompanionSessionException managerRequired() {
        return new CompanionSessionException(
                HttpStatus.FORBIDDEN,
                "companion_session_manager_required",
                "배정된 매니저만 동행 진행 정보를 변경할 수 있습니다.");
    }

    static CompanionSessionException notFound() {
        return new CompanionSessionException(
                HttpStatus.NOT_FOUND,
                "companion_session_not_found",
                "동행 세션 정보를 찾을 수 없습니다.");
    }

    static CompanionSessionException reportNotFound() {
        return new CompanionSessionException(
                HttpStatus.NOT_FOUND,
                "companion_session_report_not_found",
                "동행 리포트를 찾을 수 없습니다.");
    }

    static CompanionSessionException stateConflict() {
        return new CompanionSessionException(
                HttpStatus.CONFLICT,
                "companion_session_state_conflict",
                "현재 동행 상태에서는 요청한 변경을 적용할 수 없습니다.");
    }

    static CompanionSessionException idempotencyConflict() {
        return new CompanionSessionException(
                HttpStatus.CONFLICT,
                "companion_message_idempotency_conflict",
                "같은 재시도 식별자에 다른 메시지 내용이 이미 저장되어 있습니다.");
    }

    static CompanionSessionException chatMessageNotFound() {
        return new CompanionSessionException(
                HttpStatus.NOT_FOUND,
                "companion_chat_message_not_found",
                "동행 채팅 메시지를 찾을 수 없습니다.");
    }

    static CompanionSessionException versionConflict() {
        return new CompanionSessionException(
                HttpStatus.CONFLICT,
                "companion_session_version_conflict",
                "다른 변경이 먼저 반영되었습니다. 최신 동행 정보를 다시 확인해 주세요.");
    }

    HttpStatus status() {
        return status;
    }

    String error() {
        return error;
    }
}
