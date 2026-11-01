package com.tickethub.eventshow.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Central place that turns every exception into the standard {@link ErrorResponse}.
 * Controllers and services just throw; they never build error JSON themselves.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Our own controlled failures (404, 409, ...). */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request.getRequestURI(), List.of());
    }

    /** Last line of defence for constraint violations we did not pre-check. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "The request conflicts with existing data.", request.getRequestURI(), List.of());
    }

    /** Anything unexpected: log the detail, tell the client nothing internal. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred.", request.getRequestURI(), List.of());
    }

    /** Bean-validation failures on @Valid request bodies: 400 with a list of field errors. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<ErrorResponse.FieldDetail> details = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> details.add(new ErrorResponse.FieldDetail(fe.getField(), fe.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> details.add(new ErrorResponse.FieldDetail(ge.getObjectName(), ge.getDefaultMessage())));
        ErrorResponse body = new ErrorResponse(Instant.now(), 400, "Bad Request", "VALIDATION_ERROR",
                "Request validation failed.", pathOf(request), details);
        return ResponseEntity.badRequest().body(body);
    }

    /** Malformed JSON, wrong types, unknown enum values. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ErrorResponse body = new ErrorResponse(Instant.now(), 400, "Bad Request", "MALFORMED_REQUEST",
                "Request body is missing, malformed or contains an invalid value.", pathOf(request), List.of());
        return ResponseEntity.badRequest().body(body);
    }

    /** Everything else Spring MVC raises itself (404 route, 405, missing header, bad param type...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String reason = status != null ? status.getReasonPhrase() : "Error";
        String code = reason.toUpperCase().replace(' ', '_');
        String message = statusCode.is4xxClientError() ? ex.getMessage() : "An unexpected error occurred.";
        if (statusCode.is5xxServerError()) {
            log.error("Server error on {}", pathOf(request), ex);
        }
        ErrorResponse error = new ErrorResponse(Instant.now(), statusCode.value(), reason, code, message,
                pathOf(request), List.of());
        return new ResponseEntity<>(error, headers, statusCode);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message, String path,
                                                List<ErrorResponse.FieldDetail> details) {
        ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), code,
                message, path, details);
        return ResponseEntity.status(status).body(body);
    }

    private String pathOf(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return "";
    }
}
