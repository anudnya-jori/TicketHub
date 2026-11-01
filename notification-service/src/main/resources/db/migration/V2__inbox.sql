-- Phase 2 / Scenario 2: idempotent consumer. One row per processed event id.
-- The primary key is the guarantee: two consumer instances can never both "first-process" the same event.
CREATE TABLE inbox_events (
    event_id    VARCHAR(100) PRIMARY KEY,
    event_type  VARCHAR(60),
    booking_id  BIGINT,
    received_at TIMESTAMPTZ NOT NULL
);

-- Events handled before the inbox existed are recorded too, so a replay after the upgrade stays harmless.
INSERT INTO inbox_events (event_id, event_type, booking_id, received_at)
SELECT event_id, 'BookingConfirmed', booking_id, created_at FROM notifications;
