package com.bolt.exception;

import com.bolt.util.UrlServiceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private record ApiError(String code, String message, Instant timestamp) {
    }

    @ExceptionHandler(AliasNotFoundException.class)
    public ResponseEntity<ApiError> handleAliasNotFound(AliasNotFoundException ex) {
        return buildError(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(AliasAlreadyTakenException.class)
    public ResponseEntity<ApiError> handleAliasAlreadyTaken(AliasAlreadyTakenException ex) {
        return buildError(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(ReservedAliasException.class)
    public ResponseEntity<ApiError> handleReservedAlias(ReservedAliasException ex) {
        return buildError(HttpStatus.BAD_REQUEST, "RESERVED_ALIAS", ex.getMessage());
    }

    @ExceptionHandler(QrGenerationException.class)
    public ResponseEntity<ApiError> handleQrGeneration(QrGenerationException ex) {
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "QR_ERROR", "Failed to generate QR code");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Stream<String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage());
        Stream<String> globalErrors = ex.getBindingResult()
                .getGlobalErrors()
                .stream()
                .map(ObjectError::getDefaultMessage);
        String message = Stream.concat(fieldErrors, globalErrors)
                .collect(Collectors.joining(", "));
        return buildError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedRequest(HttpMessageNotReadableException ex) {
        return buildError(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed or unreadable request body");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return buildError(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return buildError(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex) {
        log.error("Internal state error", ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            if (status != null) {
                if (status.is4xxClientError()) {
                    log.debug("Client HTTP error ({}): {}", status.value(), ex.getMessage());
                } else {
                    log.error("Server HTTP error ({})", status.value(), ex);
                }
                String detail = errorResponse.getBody().getDetail();
                return buildError(status, status.name(), detail != null ? detail : status.getReasonPhrase());
            }
        }
        log.error("Unhandled exception", ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    private ResponseEntity<ApiError> buildError(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ApiError(code, message, UrlServiceUtil.now()));
    }
}
