package com.bodeul.core.auth;

public interface FirebaseTokenVerifier {

    VerifiedToken verify(String idToken);

    record VerifiedToken(String uid) {
        public VerifiedToken {
            if (uid == null || uid.isBlank()) {
                throw new InvalidTokenException();
            }
        }
    }

    final class InvalidTokenException extends RuntimeException {
        public InvalidTokenException() {
            super("Firebase ID token 검증에 실패했습니다.");
        }
    }

    final class UnavailableException extends RuntimeException {
        public UnavailableException() {
            super("Firebase ID token 검증기가 설정되지 않았습니다.");
        }
    }
}
