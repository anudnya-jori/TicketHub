package com.tickethub.payment.exception;

import org.springframework.http.HttpStatus;

/**
 * A controlled, expected failure: the client sent something invalid, something was not found,
 * or a business rule was violated. Carries the HTTP status and a stable machine-readable code.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    /** Business-rule conflict (state does not allow the action, seat taken, duplicate...). */
    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    /** A dependency (another service) cannot be reached or timed out. */
    public static ApiException unavailable(String code, String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    /** A call to the payment provider took too long: the outcome is unknown. */
    public static ApiException gatewayTimeout(String code, String message) {
        return new ApiException(HttpStatus.GATEWAY_TIMEOUT, code, message);
    }
}
