package com.tickethub.payment.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Razorpay (test mode) through its REST API, no SDK. Amounts are in PAISE (rupees x 100).
 * Flow: charge() only CREATES an order; the customer pays in Razorpay Checkout; the browser then calls
 * POST /payments/{id}/verify with (order_id, payment_id, signature), checked by verifyCheckoutSignature().
 */
@Component
@ConditionalOnProperty(name = "payment.provider", havingValue = "razorpay")
public class RazorpayPaymentProvider implements PaymentProvider {

    private final String keySecret;
    private final String baseUrl;
    private final String authHeader;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public RazorpayPaymentProvider(@Value("${payment.razorpay.key-id:}") String keyId,
                                   @Value("${payment.razorpay.key-secret:}") String keySecret,
                                   @Value("${payment.razorpay.base-url:https://api.razorpay.com}") String baseUrl) {
        if (keyId.isBlank() || keySecret.isBlank()) {
            throw new IllegalStateException(
                    "RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET are required when payment.provider=razorpay");
        }
        this.keySecret = keySecret;
        this.baseUrl = baseUrl;
        this.authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String name() {
        return "RAZORPAY";
    }

    /** Creates an ORDER. Nothing is charged here: the customer pays it later in Checkout. */
    @Override
    public ChargeOutcome charge(String reference, BigDecimal amount, String currency, SimulationHints hints) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", paise(amount));
        body.put("currency", currency);
        body.put("receipt", reference);                              // max 40 characters: "pay_" + uuid is exactly 40
        body.put("notes", Map.of("reference", reference));
        Resp r = call("POST", "/v1/orders", body, null);
        if (r.status == 200) {
            return ChargeOutcome.awaitingCustomer(r.json.path("id").asText());
        }
        throw new IllegalStateException("Razorpay order error " + r.status + ": " + message(r));
    }

    /** The browser's proof of payment: hex HMAC-SHA256 of "order_id|payment_id" with the key secret. */
    @Override
    public boolean verifyCheckoutSignature(String orderId, String paymentId, String signature) {
        if (orderId == null || paymentId == null || signature == null) {
            return false;
        }
        String expected = hmacHex(orderId + "|" + paymentId);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public RefundOutcome refund(ProviderIds ids, BigDecimal amount, String currency) {
        String paymentId = ids.paymentId() != null ? ids.paymentId() : findCapturedPaymentId(ids.orderId());
        if (paymentId == null) {
            throw new IllegalStateException("No captured Razorpay payment found for " + ids.reference());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", paise(amount));
        body.put("notes", Map.of("reference", ids.reference()));
        Resp r = call("POST", "/v1/payments/" + paymentId + "/refund", body, "refund-" + ids.reference());
        if (r.status == 200) {
            return new RefundOutcome(r.json.path("id").asText());
        }
        throw new IllegalStateException("Razorpay refund error " + r.status + ": " + message(r));
    }

    /** SUCCESS only when a payment of the order is captured. A failed attempt stays PENDING: the customer may retry. */
    @Override
    public String fetchStatus(ProviderIds ids) {
        return findCapturedPaymentId(ids.orderId()) != null ? "SUCCESS" : "PENDING";
    }

    private String findCapturedPaymentId(String orderId) {
        if (orderId == null) {
            return null;
        }
        Resp r = call("GET", "/v1/orders/" + orderId + "/payments", null, null);
        if (r.status != 200) {
            return null;
        }
        for (JsonNode p : r.json.path("items")) {
            if ("captured".equals(p.path("status").asText())) {
                return p.path("id").asText();
            }
        }
        return null;
    }

    private static long paise(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static String message(Resp r) {
        return r.json.path("error").path("description").asText("no details");
    }

    private String hmacHex(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Resp(int status, JsonNode json) {
    }

    private Resp call(String method, String path, Object body, String refundIdempotencyKey) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(12)).header("Authorization", authHeader);
            if (refundIdempotencyKey != null) {
                b.header("X-Refund-Idempotency", refundIdempotencyKey);
            }
            if ("POST".equals(method)) {
                b.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            } else {
                b.GET();
            }
            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Resp(resp.statusCode(), mapper.readTree(resp.body()));
        } catch (HttpTimeoutException e) {
            throw new ProviderCallTimeoutException("Razorpay did not answer in time");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderCallTimeoutException("Interrupted while waiting for Razorpay");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot reach Razorpay: " + e.getMessage(), e);
        }
    }
}