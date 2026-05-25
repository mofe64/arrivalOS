package com.arrivalos.web.error;

public record FieldErrorResponse(
        String field,
        String message) {
}
