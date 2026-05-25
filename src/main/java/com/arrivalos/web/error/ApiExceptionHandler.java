package com.arrivalos.web.error;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.arrivalos.email.EmailDeliveryException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> handleApiException(
            ApiException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiErrorResponse.of(
                        exception.getStatus().value(),
                        exception.getCode(),
                        exception.getMessage(),
                        request.getRequestURI(),
                        requestId(request)));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> handleResponseStatus(
            ResponseStatusException exception,
            HttpServletRequest request) {
        String message = exception.getReason() == null ? "Request failed" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode())
                .body(ApiErrorResponse.of(
                        exception.getStatusCode().value(),
                        codeFor(message),
                        message,
                        request.getRequestURI(),
                        requestId(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<FieldErrorResponse> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> new FieldErrorResponse(
                        fieldError.getField(),
                        fieldError.getDefaultMessage() == null ? "Invalid value" : fieldError.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldErrorResponse::field))
                .toList();

        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.withFieldErrors(
                        400,
                        "VALIDATION_FAILED",
                        "Validation failed",
                        request.getRequestURI(),
                        requestId(request),
                        fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(
                        400,
                        "INVALID_REQUEST_BODY",
                        "Invalid request body",
                        request.getRequestURI(),
                        requestId(request)));
    }

    @ExceptionHandler(EmailDeliveryException.class)
    ResponseEntity<ApiErrorResponse> handleEmailDelivery(
            EmailDeliveryException exception,
            HttpServletRequest request) {
        String requestId = requestId(request);
        log.error(
                "Email delivery failed for path={} requestId={}: {}",
                request.getRequestURI(),
                requestId,
                exception.getMessage(),
                exception);
        return ResponseEntity.status(502)
                .body(ApiErrorResponse.of(
                        502,
                        "EMAIL_DELIVERY_FAILED",
                        "Email delivery failed",
                        request.getRequestURI(),
                        requestId));
    }

    private String codeFor(String message) {
        return message
                .trim()
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("(^_+|_+$)", "")
                .toUpperCase(Locale.ROOT);
    }

    private String requestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            return java.util.UUID.randomUUID().toString();
        }
        return requestId;
    }
}
