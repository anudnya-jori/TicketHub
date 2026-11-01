-- Phase 2 (Scenarios 3, 4, 5, 6): Payment tells Booking through EVENTS written to an outbox, not through a REST call.

CREATE TABLE outbox_events (
    id            BIGSERIAL PRIMARY KEY,
    event_id      VARCHAR(100) NOT NULL,
    event_type    VARCHAR(60)  NOT NULL,
    topic         VARCHAR(100) NOT NULL,
    aggregate_id  BIGINT       NOT NULL,          -- the BOOKING id: it is the Kafka message key (Scenario 6)
    payload       TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    published_at  TIMESTAMPTZ,
    CONSTRAINT uq_outbox_event_id UNIQUE (event_id)
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (id) WHERE published_at IS NULL;

CREATE TABLE inbox_events (
    event_id    VARCHAR(100) PRIMARY KEY,
    event_type  VARCHAR(60) NOT NULL,
    booking_id  BIGINT,
    received_at TIMESTAMPTZ NOT NULL
);

-- The outbox replaces the "booking_synced" flag and its retry scheduler.
DROP INDEX IF EXISTS idx_payments_unsynced;
ALTER TABLE payments DROP COLUMN booking_synced;

ALTER TABLE payments ADD COLUMN aggregate_version           BIGINT       NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN last_booking_event_version  BIGINT       NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN refund_id                   VARCHAR(100);

-- Scenario 5: SUCCESS -> REFUNDED
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check
    CHECK (status IN ('INITIATED', 'SUCCESS', 'FAILED', 'PENDING', 'REFUNDED'));
-- One refund id per payment at most: the database also refuses a second refund record.
CREATE UNIQUE INDEX uq_payments_refund_id ON payments (refund_id) WHERE refund_id IS NOT NULL;
