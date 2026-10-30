package com.calipso.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @org.springframework.beans.factory.annotation.Value("${calipso.security.expose-error-details:true}")
    private boolean exposeErrorDetails = true;

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException exception) {
        int status = exception.getStatusCode().value();
        String message = exception.getReason() == null || exception.getReason().isBlank()
                ? exception.getStatusCode().toString()
                : exception.getReason();

        return ResponseEntity
                .status(exception.getStatusCode())
                .body(new ApiErrorResponse(status, message, message));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntimeException(RuntimeException exception) {
        if (!exposeErrorDetails) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorResponse(500, "Une erreur interne est survenue.", "Une erreur interne est survenue."));
        }
        String message = exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Erreur interne"
                : exception.getMessage();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(), message, message));
    }

    public record ApiErrorResponse(
            int status,
            String message,
            String detail
    ) {
    }
}
