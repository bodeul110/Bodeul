package com.bodeul.core.consent;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = GuardianSharingConsentController.class)
@Profile({"database", "guardian-sharing-consent-test"})
class GuardianSharingConsentErrorHandler {

    @ExceptionHandler(GuardianSharingConsentException.class)
    ResponseEntity<ApiError> handleConsentException(GuardianSharingConsentException exception) {
        return ResponseEntity.status(exception.status())
                .cacheControl(CacheControl.noStore())
                .body(new ApiError(exception.error(), exception.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .cacheControl(CacheControl.noStore())
                .body(new ApiError(
                        "invalid_guardian_sharing_consent_request",
                        "정보공유 동의 요청 형식을 확인해 주세요."));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<ApiError> handleDatabaseFailure(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .body(new ApiError(
                        "guardian_sharing_consent_database_failure",
                        "정보공유 동의를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."));
    }

    private record ApiError(String error, String message) {
    }
}
