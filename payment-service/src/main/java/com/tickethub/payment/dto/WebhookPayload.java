package com.tickethub.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WebhookPayload(@NotBlank String eventId, @NotBlank String providerReference,
                             @NotNull WebhookResult result) {
    public enum WebhookResult {SUCCESS, FAILED}
}
