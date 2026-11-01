package com.tickethub.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.payment.domain.Payment;
import com.tickethub.payment.domain.PaymentStatus;
import com.tickethub.payment.domain.WebhookEvent;
import com.tickethub.payment.dto.WebhookPayload;
import com.tickethub.payment.dto.WebhookResponse;
import com.tickethub.payment.exception.ApiException;
import com.tickethub.payment.metrics.PaymentMetrics;
import com.tickethub.payment.repository.PaymentRepository;
import com.tickethub.payment.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;

/**
 * Provider webhooks (ST-24 / ST-55): the provider's callback is not trusted until its HMAC signature matches
 * the RAW body; each event id is processed once; a late or conflicting event never changes a final payment.
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final byte[] secret;
    private final ObjectMapper objectMapper;
    private final PaymentRepository paymentRepository;
    private final WebhookEventRepository eventRepository;
    private final PaymentService paymentService;
    private final Clock clock;
    private final PaymentMetrics metrics;

    public WebhookService(@Value("${payment.webhook.secret}") String secret, ObjectMapper objectMapper,
                          PaymentRepository paymentRepository, WebhookEventRepository eventRepository,
                          PaymentService paymentService, Clock clock, PaymentMetrics metrics) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
        this.paymentRepository = paymentRepository;
        this.eventRepository = eventRepository;
        this.paymentService = paymentService;
        this.clock = clock;
        this.metrics = metrics;
    }

    public WebhookResponse handle(String rawBody, String signature) {
        if (!signatureMatches(rawBody, signature)) {
            log.warn("WEBHOOK_REJECTED invalid signature");
            metrics.webhook("REJECTED");
            throw ApiException.unauthorized("INVALID_SIGNATURE", "Webhook signature is missing or invalid.");
        }
        WebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, WebhookPayload.class);
        } catch (Exception e) {
            throw ApiException.badRequest("MALFORMED_REQUEST", "Webhook body is not valid.");
        }
        if (payload.eventId() == null || payload.providerReference() == null || payload.result() == null) {
            throw ApiException.badRequest("MALFORMED_REQUEST", "Webhook body is missing required fields.");
        }

        Payment payment = paymentRepository.findByProviderReference(payload.providerReference()).orElse(null);
        if (payment == null) {
            log.warn("WEBHOOK_IGNORED unknown providerReference={}", payload.providerReference());
            metrics.webhook("IGNORED");
            return new WebhookResponse("IGNORED");
        }

        // Dedupe: the primary key on event_id makes "first one wins" a database guarantee.
        try {
            eventRepository.saveAndFlush(new WebhookEvent(payload.eventId(), payment.getId(), clock.instant()));
        } catch (DataIntegrityViolationException duplicate) {
            log.info("WEBHOOK_DUPLICATE eventId={}", payload.eventId());
            metrics.webhook("DUPLICATE");
            return new WebhookResponse("DUPLICATE");
        }

        if (payment.getStatus().isFinal()) {
            log.warn("WEBHOOK_IGNORED payment {} already {} (event {} says {})", payment.getId(),
                    payment.getStatus(), payload.eventId(), payload.result());
            metrics.webhook("IGNORED");
            return new WebhookResponse("IGNORED");
        }
        if (payload.result() == WebhookPayload.WebhookResult.SUCCESS) {
            paymentService.recordOutcome(payment, PaymentStatus.SUCCESS, null);
        } else {
            paymentService.recordOutcome(payment, PaymentStatus.FAILED, "PROVIDER_REPORTED_FAILURE");
        }
        log.info("WEBHOOK_APPLIED paymentId={} -> {}", payment.getId(), payment.getStatus());
        metrics.webhook("APPLIED");
        return new WebhookResponse("APPLIED");
    }

    /** Constant-time comparison of the hex HMAC-SHA256. */
    boolean signatureMatches(String rawBody, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
