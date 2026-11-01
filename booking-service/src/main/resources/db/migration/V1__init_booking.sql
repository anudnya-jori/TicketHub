-- Booking Service owns: bookings and the seat holds (reservation state).
-- show_id / seat_id / customer_id are plain values referring to OTHER services' data:
-- there is deliberately no foreign key across services.

CREATE TABLE bookings (
    id                   BIGSERIAL PRIMARY KEY,
    customer_id          VARCHAR(64)    NOT NULL,
    customer_email       VARCHAR(254)   NOT NULL,   -- where the confirmation email goes (placeholder until the token carries it, S7)
    show_id              BIGINT         NOT NULL,
    amount               NUMERIC(12, 2) NOT NULL CHECK (amount > 0),   -- price snapshot taken at booking time (ST-46)
    currency             VARCHAR(3)     NOT NULL,
    status               VARCHAR(30)    NOT NULL,
    created_at           TIMESTAMPTZ    NOT NULL,
    updated_at           TIMESTAMPTZ    NOT NULL,
    expiry_at            TIMESTAMPTZ    NOT NULL,
    idempotency_key      VARCHAR(100)   NOT NULL,
    request_fingerprint  VARCHAR(500)   NOT NULL,
    -- the same customer re-sending the same key must never create a second booking (ST-53)
    CONSTRAINT uq_booking_customer_idempotency UNIQUE (customer_id, idempotency_key)
);
CREATE INDEX idx_bookings_status_expiry ON bookings (status, expiry_at);

CREATE TABLE booking_seats (
    id          BIGSERIAL PRIMARY KEY,
    booking_id  BIGINT      NOT NULL REFERENCES bookings (id),
    show_id     BIGINT      NOT NULL,
    seat_id     BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL CHECK (status IN ('RESERVED', 'BOOKED', 'RELEASED')),
    created_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_booking_seats_booking ON booking_seats (booking_id);

-- THE concurrency guarantee (ST-08). At most ONE active (RESERVED or BOOKED) hold per (show, seat).
-- RELEASED rows are ignored by the index, so a released/expired seat can be booked again while we keep history.
-- Two simultaneous requests for the same seat: the database lets exactly one INSERT commit.
CREATE UNIQUE INDEX uq_booking_seat_active
    ON booking_seats (show_id, seat_id)
    WHERE status IN ('RESERVED', 'BOOKED');
