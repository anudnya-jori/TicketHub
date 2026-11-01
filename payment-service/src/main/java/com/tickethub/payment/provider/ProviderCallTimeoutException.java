package com.tickethub.payment.provider;

public class ProviderCallTimeoutException extends RuntimeException {
    public ProviderCallTimeoutException(String message) {
        super(message);
    }
}
