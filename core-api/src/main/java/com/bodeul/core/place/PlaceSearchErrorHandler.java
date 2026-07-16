package com.bodeul.core.place;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PlaceSearchController.class)
class PlaceSearchErrorHandler {

    @ExceptionHandler(PlaceSearchException.class)
    ResponseEntity<ApiError> handlePlaceSearchException(PlaceSearchException exception) {
        return ResponseEntity.status(exception.status())
                .cacheControl(CacheControl.noStore())
                .body(new ApiError(exception.error(), exception.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException exception) {
        return ResponseEntity.badRequest()
                .cacheControl(CacheControl.noStore())
                .body(new ApiError(
                        "invalid_place_search_request",
                        "검색어와 검색 범주가 필요합니다."));
    }

    private record ApiError(String error, String message) {
    }
}
