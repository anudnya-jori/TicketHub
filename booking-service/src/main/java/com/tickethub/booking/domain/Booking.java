package com.tickethub.booking.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "customer_email", nullable = false, length = 254)
    private String customerEmail;

    @Column(name = "show_id", nullable = false)
    private Long showId;

    /** Price snapshot: show price x number of seats, frozen at creation. Later price changes do not touch it. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expiry_at", nullable = false)
    private Instant expiryAt;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 500)
    private String requestFingerprint;

    /** Counts this booking's own changes. Published on its events so consumers can ignore a stale one (Scenario 6). */
    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;

    /** Highest aggregate version of a Payment event already applied. Lower versions are stale and ignored. */
    @Column(name = "last_payment_event_version", nullable = false)
    private long lastPaymentEventVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false, length = 20)
    private RefundStatus refundStatus = RefundStatus.NONE;

    protected Booking() {
    }

    public Booking(String customerId, String customerEmail, Long showId, BigDecimal amount, String currency,
                   Instant now, Instant expiryAt, String idempotencyKey, String requestFingerprint) {
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.showId = showId;
        this.amount = amount;
        this.currency = currency;
        this.status = BookingStatus.PENDING_PAYMENT;
        this.createdAt = now;
        this.updatedAt = now;
        this.expiryAt = expiryAt;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
    }

    public void changeStatus(BookingStatus next, Instant now) {
        this.status = next;
        this.updatedAt = now;
        this.aggregateVersion++;
    }

    public void requestRefund() {
        this.refundStatus = RefundStatus.REQUESTED;
    }

    public void markRefunded(Instant now) {
        this.refundStatus = RefundStatus.REFUNDED;
        this.updatedAt = now;
        this.aggregateVersion++;
    }

    public void recordPaymentEventVersion(long version) {
        this.lastPaymentEventVersion = Math.max(this.lastPaymentEventVersion, version);
    }

    public Long getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getCustomerEmail() { return customerEmail; }
    public Long getShowId() { return showId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public BookingStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getExpiryAt() { return expiryAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public long getAggregateVersion() { return aggregateVersion; }
    public long getLastPaymentEventVersion() { return lastPaymentEventVersion; }
    public RefundStatus getRefundStatus() { return refundStatus; }
}
