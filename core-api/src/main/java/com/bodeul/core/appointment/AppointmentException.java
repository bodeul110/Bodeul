package com.bodeul.core.appointment;

import org.springframework.http.HttpStatus;

final class AppointmentException extends RuntimeException {

    private final HttpStatus status;
    private final String error;

    private AppointmentException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    static AppointmentException invalidRequest(String message) {
        return new AppointmentException(
                HttpStatus.BAD_REQUEST,
                "invalid_appointment_request",
                message);
    }

    static AppointmentException permissionDenied() {
        return new AppointmentException(
                HttpStatus.FORBIDDEN,
                "appointment_permission_denied",
                "이 예약을 조회하거나 변경할 권한이 없습니다.");
    }

    static AppointmentException roleNotSupported() {
        return new AppointmentException(
                HttpStatus.FORBIDDEN,
                "appointment_role_not_supported",
                "환자 또는 보호자 계정으로 접근해 주세요.");
    }

    static AppointmentException readRoleNotSupported() {
        return new AppointmentException(
                HttpStatus.FORBIDDEN,
                "appointment_read_role_not_supported",
                "환자, 보호자 또는 배정된 매니저 계정으로 접근해 주세요.");
    }

    static AppointmentException notFound() {
        return new AppointmentException(
                HttpStatus.NOT_FOUND,
                "appointment_not_found",
                "예약 정보를 찾을 수 없습니다.");
    }

    static AppointmentException profileNotReady() {
        return new AppointmentException(
                HttpStatus.CONFLICT,
                "appointment_profile_not_ready",
                "예약에 사용할 사용자 프로필이 아직 준비되지 않았습니다.");
    }

    static AppointmentException participantAmbiguous() {
        return new AppointmentException(
                HttpStatus.CONFLICT,
                "linked_participant_ambiguous",
                "입력한 연락처와 일치하는 연결 계정을 하나로 결정할 수 없습니다.");
    }

    static AppointmentException requesterLinkConflict() {
        return new AppointmentException(
                HttpStatus.CONFLICT,
                "appointment_requester_link_conflict",
                "처음 예약을 만든 사용자의 연결 정보를 유지해야 합니다.");
    }

    static AppointmentException stateConflict() {
        return new AppointmentException(
                HttpStatus.CONFLICT,
                "appointment_state_conflict",
                "현재 예약 상태에서는 요청한 변경을 적용할 수 없습니다.");
    }

    static AppointmentException versionConflict() {
        return new AppointmentException(
                HttpStatus.CONFLICT,
                "appointment_version_conflict",
                "다른 변경이 먼저 반영되었습니다. 최신 예약 정보를 다시 확인해 주세요.");
    }

    HttpStatus status() {
        return status;
    }

    String error() {
        return error;
    }
}
