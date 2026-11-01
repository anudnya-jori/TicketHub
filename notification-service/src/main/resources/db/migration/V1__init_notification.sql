CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    event_id    VARCHAR(100)  NOT NULL,
    booking_id  BIGINT        NOT NULL,
    recipient   VARCHAR(255)  NOT NULL,
    subject     VARCHAR(255)  NOT NULL,
    body        TEXT          NOT NULL,
    status      VARCHAR(20)   NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    error       VARCHAR(500),
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    -- one notification per event: a redelivered Kafka message can never send a second email
    CONSTRAINT uq_notifications_event UNIQUE (event_id)
);
CREATE INDEX idx_notifications_booking ON notifications (booking_id);
