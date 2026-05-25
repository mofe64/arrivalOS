package com.arrivalos.web.error;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String requestId,
        List<FieldErrorResponse> fieldErrors) {

    public static ApiErrorResponse of(
            int status,
            String code,
            String message,
            String path,
            String requestId) {
        return new ApiErrorResponse(
                Instant.now(),
                status,
                code,
                message,
                path,
                requestId,
                List.of());
    }

    public static ApiErrorResponse withFieldErrors(
            int status,
            String code,
            String message,
            String path,
            String requestId,
            List<FieldErrorResponse> fieldErrors) {
        return new ApiErrorResponse(
                Instant.now(),
                status,
                code,
                message,
                path,
                requestId,
                fieldErrors);
    }
}
