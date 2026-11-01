CREATE TABLE payments (
    id                 BIGSERIAL PRIMARY KEY,
    booking_id         BIGINT         NOT NULL,
    customer_id        VARCHAR(100)   NOT NULL,
    amount             NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency           VARCHAR(3)     NOT NULL,
    status             VARCHAR(20)    NOT NULL CHECK (status IN ('INITIATED', 'SUCCESS', 'FAILED', 'PENDING')),
    provider           VARCHAR(40)    NOT NULL,
    provider_reference VARCHAR(100)   NOT NULL,
    failure_reason     VARCHAR(255),
    booking_synced     BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ    NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL,
    -- ONE payment per booking: the database itself stops a double charge, even under a race.
    CONSTRAINT uq_payments_booking UNIQUE (booking_id),
    CONSTRAINT uq_payments_provider_ref UNIQUE (provider_reference)
);
CREATE INDEX idx_payments_unsynced ON payments (status) WHERE booking_synced = FALSE;

-- Every processed webhook event id: a replayed webhook is recognised and ignored.
CREATE TABLE webhook_events (
    event_id    VARCHAR(100) PRIMARY KEY,
    payment_id  BIGINT       REFERENCES payments (id),
    received_at TIMESTAMPTZ  NOT NULL
);
