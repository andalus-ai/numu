-- PostgreSQL schema for concurrency-safe multi-night accommodation holds.
-- Capacity rows must be provisioned for each sellable unit and local stay date.
CREATE TABLE IF NOT EXISTS hospitality_inventory_night (
    tenant_id       VARCHAR(128) NOT NULL,
    listing_id      UUID NOT NULL,
    unit_id         UUID NOT NULL,
    stay_date       DATE NOT NULL,
    capacity        INTEGER NOT NULL CHECK (capacity > 0),
    PRIMARY KEY (tenant_id, unit_id, stay_date),
    CHECK (listing_id IS NOT NULL)
);

CREATE TABLE IF NOT EXISTS hospitality_stay_booking (
    tenant_id        VARCHAR(128) NOT NULL,
    booking_id       UUID NOT NULL,
    customer_id      VARCHAR(128) NOT NULL,
    idempotency_key  VARCHAR(200) NOT NULL,
    listing_id       UUID NOT NULL,
    unit_id          UUID NOT NULL,
    check_in         DATE NOT NULL,
    check_out        DATE NOT NULL,
    guest_count      INTEGER NOT NULL CHECK (guest_count > 0),
    quote_id         UUID NOT NULL,
    currency         CHAR(3) NOT NULL,
    booking_status   VARCHAR(16) NOT NULL CHECK (booking_status IN ('HELD','CONFIRMED','CANCELLED','EXPIRED')),
    created_at       TIMESTAMPTZ NOT NULL,
    hold_expires_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, booking_id),
    CONSTRAINT uq_hospitality_booking_idempotency UNIQUE (tenant_id, customer_id, idempotency_key),
    CHECK (check_out > check_in),
    CHECK (hold_expires_at > created_at)
);

CREATE TABLE IF NOT EXISTS hospitality_stay_booking_night (
    tenant_id   VARCHAR(128) NOT NULL,
    booking_id  UUID NOT NULL,
    unit_id     UUID NOT NULL,
    stay_date   DATE NOT NULL,
    PRIMARY KEY (tenant_id, booking_id, stay_date),
    FOREIGN KEY (tenant_id, booking_id)
        REFERENCES hospitality_stay_booking (tenant_id, booking_id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id, unit_id, stay_date)
        REFERENCES hospitality_inventory_night (tenant_id, unit_id, stay_date)
);

CREATE INDEX IF NOT EXISTS ix_hospitality_booking_night_inventory
    ON hospitality_stay_booking_night (tenant_id, unit_id, stay_date);

CREATE INDEX IF NOT EXISTS ix_hospitality_booking_expiry
    ON hospitality_stay_booking (tenant_id, hold_expires_at)
    WHERE booking_status = 'HELD';
