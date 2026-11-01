-- Event/Show Service owns: events, venues, seats (layout), shows.
-- NOTE: there is deliberately NO table for "seat availability". Whether a seat is reserved
-- for a show is Booking Service's data (database per service).

CREATE TABLE venues (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200)  NOT NULL,
    location    VARCHAR(300),
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL
);

CREATE TABLE seats (
    id           BIGSERIAL PRIMARY KEY,
    venue_id     BIGINT       NOT NULL REFERENCES venues (id),
    row_label    VARCHAR(10)  NOT NULL,
    seat_number  INTEGER      NOT NULL CHECK (seat_number > 0),
    category     VARCHAR(30)  NOT NULL,
    CONSTRAINT uq_seat_position UNIQUE (venue_id, row_label, seat_number)
);

CREATE TABLE events (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(200)  NOT NULL,
    description  VARCHAR(2000),
    type         VARCHAR(30)   NOT NULL,
    status       VARCHAR(30)   NOT NULL,
    poster_ref   VARCHAR(500),
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_events_status ON events (status);

CREATE TABLE shows (
    id          BIGSERIAL PRIMARY KEY,
    event_id    BIGINT         NOT NULL REFERENCES events (id),
    venue_id    BIGINT         NOT NULL REFERENCES venues (id),
    start_time  TIMESTAMPTZ    NOT NULL,
    end_time    TIMESTAMPTZ    NOT NULL,
    price       NUMERIC(12, 2) NOT NULL CHECK (price > 0),
    currency    VARCHAR(3)     NOT NULL,
    status      VARCHAR(30)    NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL,
    updated_at  TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_show_times CHECK (end_time > start_time)
);
CREATE INDEX idx_shows_event ON shows (event_id);
CREATE INDEX idx_shows_status ON shows (status);
