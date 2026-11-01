package com.tickethub.booking.event;

/** The message itself is bad: retrying cannot help, so it goes straight to the dead-letter topic (Scenario 1). */
public class InvalidEventException extends RuntimeException {
    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
