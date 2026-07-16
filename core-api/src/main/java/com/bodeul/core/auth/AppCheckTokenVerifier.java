package com.bodeul.core.auth;

public interface AppCheckTokenVerifier {

    VerifiedToken verify(String appCheckToken);

    record VerifiedToken(String appId) {
        public VerifiedToken {
            if (appId == null || appId.isBlank()) {
                throw new InvalidTokenException();
            }
        }
    }

    final class InvalidTokenException extends RuntimeException {
        public InvalidTokenException() {
            super("Firebase App Check token 검증에 실패했습니다.");
        }
    }

    final class UnavailableException extends RuntimeException {
        public UnavailableException() {
            super("Firebase App Check token 검증기가 설정되지 않았습니다.");
        }
    }
}
