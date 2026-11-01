package com.tickethub.notification.event;

/** The mail provider failed (down, rate-limited, breaker open). RETRYABLE: goes to the delayed retry topics (Scenario 10). */
public class EmailSendException extends RuntimeException {
    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
