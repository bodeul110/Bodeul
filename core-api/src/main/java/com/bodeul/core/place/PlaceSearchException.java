package com.bodeul.core.place;

import org.springframework.http.HttpStatus;

final class PlaceSearchException extends RuntimeException {

    private final HttpStatus status;
    private final String error;

    private PlaceSearchException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    static PlaceSearchException invalidRequest(String message) {
        return new PlaceSearchException(HttpStatus.BAD_REQUEST, "invalid_place_search_request", message);
    }

    static PlaceSearchException rateLimitExceeded() {
        return new PlaceSearchException(
                HttpStatus.TOO_MANY_REQUESTS,
                "place_search_rate_limit_exceeded",
                "장소 검색 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
    }

    static PlaceSearchException kakaoNotConfigured() {
        return new PlaceSearchException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "kakao_local_not_configured",
                "장소 검색 서버 설정이 아직 완료되지 않았습니다.");
    }

    static PlaceSearchException kakaoCredentialsInvalid() {
        return new PlaceSearchException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "kakao_local_credentials_invalid",
                "장소 검색 서버 인증 설정을 확인해야 합니다.");
    }

    static PlaceSearchException kakaoQuotaExceeded() {
        return new PlaceSearchException(
                HttpStatus.TOO_MANY_REQUESTS,
                "kakao_local_quota_exceeded",
                "장소 검색 사용량 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.");
    }

    static PlaceSearchException kakaoUnavailable() {
        return new PlaceSearchException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "kakao_local_unavailable",
                "장소 검색 서비스에 일시적으로 연결할 수 없습니다.");
    }

    static PlaceSearchException kakaoResponseInvalid() {
        return new PlaceSearchException(
                HttpStatus.BAD_GATEWAY,
                "kakao_local_response_invalid",
                "장소 검색 응답을 처리할 수 없습니다.");
    }

    HttpStatus status() {
        return status;
    }

    String error() {
        return error;
    }
}
