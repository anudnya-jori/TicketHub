package com.tickethub.payment.dto;

/** result: APPLIED, DUPLICATE (event id seen before) or IGNORED (payment already final / unknown). */
public record WebhookResponse(String result) {
}
