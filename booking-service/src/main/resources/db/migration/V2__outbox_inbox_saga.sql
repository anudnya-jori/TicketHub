-- Phase 2 (Scenarios 3, 4, 5, 6): transactional outbox, idempotent inbox, saga state on the booking.

-- Scenario 3: an event is written HERE, in the same transaction as the state change, and a relay publishes it.
CREATE TABLE outbox_events (
    id            BIGSERIAL PRIMARY KEY,
    event_id      VARCHAR(100) NOT NULL,
    event_type    VARCHAR(60)  NOT NULL,
    topic         VARCHAR(100) NOT NULL,
    aggregate_id  BIGINT       NOT NULL,          -- the booking id: it is also the Kafka message key
    payload       TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    published_at  TIMESTAMPTZ,
    CONSTRAINT uq_outbox_event_id UNIQUE (event_id)
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (id) WHERE published_at IS NULL;

-- Scenario 2/4: every consumed event id, written in the same transaction as its effect.
CREATE TABLE inbox_events (
    event_id    VARCHAR(100) PRIMARY KEY,
    event_type  VARCHAR(60) NOT NULL,
    booking_id  BIGINT,
    received_at TIMESTAMPTZ NOT NULL
);

-- Scenario 6: versions. aggregate_version counts the booking's own changes (published on its events);
-- last_payment_event_version is the highest Payment version applied, so a stale payment event is ignored.
ALTER TABLE bookings ADD COLUMN aggregate_version          BIGINT      NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD COLUMN last_payment_event_version BIGINT      NOT NULL DEFAULT 0;

-- Scenario 5: money back. NONE -> REQUESTED (rejected/cancelled after payment) -> REFUNDED (Payment confirmed it).
ALTER TABLE bookings ADD COLUMN refund_status VARCHAR(20) NOT NULL DEFAULT 'NONE';
