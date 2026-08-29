package com.bodeul.core.consent;

import org.springframework.http.HttpStatus;

final class GuardianSharingConsentException extends RuntimeException {

    private final HttpStatus status;
    private final String error;

    private GuardianSharingConsentException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    static GuardianSharingConsentException invalidRequest(String message) {
        return new GuardianSharingConsentException(
                HttpStatus.BAD_REQUEST,
                "invalid_guardian_sharing_consent_request",
                message);
    }

    static GuardianSharingConsentException permissionDenied() {
        return new GuardianSharingConsentException(
                HttpStatus.FORBIDDEN,
                "guardian_sharing_consent_permission_denied",
                "이 예약의 정보공유 동의를 조회하거나 변경할 권한이 없습니다.");
    }

    static GuardianSharingConsentException patientRequired() {
        return new GuardianSharingConsentException(
                HttpStatus.FORBIDDEN,
                "guardian_sharing_consent_patient_required",
                "성인 환자 본인 계정으로 정보공유 동의를 변경해 주세요.");
    }

    static GuardianSharingConsentException appointmentNotFound() {
        return new GuardianSharingConsentException(
                HttpStatus.NOT_FOUND,
                "guardian_sharing_consent_appointment_not_found",
                "동의를 설정할 예약을 찾을 수 없습니다.");
    }

    static GuardianSharingConsentException consentNotFound() {
        return new GuardianSharingConsentException(
                HttpStatus.NOT_FOUND,
                "guardian_sharing_consent_not_found",
                "저장된 정보공유 동의를 찾을 수 없습니다.");
    }

    static GuardianSharingConsentException stateConflict() {
        return new GuardianSharingConsentException(
                HttpStatus.CONFLICT,
                "guardian_sharing_consent_state_conflict",
                "현재 예약 또는 동의 상태에서는 요청을 반영할 수 없습니다.");
    }

    HttpStatus status() {
        return status;
    }

    String error() {
        return error;
    }
}
