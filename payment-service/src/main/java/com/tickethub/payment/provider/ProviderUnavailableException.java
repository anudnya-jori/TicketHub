package com.tickethub.payment.provider;

/** The circuit breaker is open: the provider has been failing/timing out, so we do not even try (fail fast). */
public class ProviderUnavailableException extends RuntimeException {
    public ProviderUnavailableException(String message) {
        super(message);
    }
}
