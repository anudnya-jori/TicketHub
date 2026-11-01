package com.tickethub.notification.event;

/**
 * The message itself is bad (unreadable JSON, missing recipient...). Retrying can never fix it, so the Kafka error
 * handler treats this as NON-RETRYABLE and sends the record straight to the dead-letter topic (Scenario 1).
 */
public class InvalidEventException extends RuntimeException {
    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
