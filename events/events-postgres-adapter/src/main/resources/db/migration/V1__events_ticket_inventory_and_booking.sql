-- Tables owned exclusively by Events and Ticketing. All capacity-affecting writes lock the inventory row.
CREATE TABLE IF NOT EXISTS event_ticket_inventory (
    tenant_id       VARCHAR(128) NOT NULL,
    event_id        VARCHAR(200) NOT NULL,
    product_id      VARCHAR(200) NOT NULL,
    currency        CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    unit_price      NUMERIC(19,4) NOT NULL CHECK (unit_price >= 0),
    capacity        INTEGER NOT NULL CHECK (capacity > 0),
    max_per_order   INTEGER NOT NULL CHECK (max_per_order > 0 AND max_per_order <= capacity),
    sold_quantity   INTEGER NOT NULL DEFAULT 0 CHECK (sold_quantity >= 0 AND sold_quantity <= capacity),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, event_id, product_id)
);

CREATE TABLE IF NOT EXISTS event_ticket_booking (
    tenant_id           VARCHAR(128) NOT NULL,
    booking_id          UUID NOT NULL,
    event_id             VARCHAR(200) NOT NULL,
    product_id           VARCHAR(200) NOT NULL,
    actor_id             VARCHAR(200) NOT NULL,
    correlation_id       VARCHAR(200) NOT NULL,
    idempotency_key      VARCHAR(200) NOT NULL,
    request_fingerprint  CHAR(64) NOT NULL CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    sales_channel        VARCHAR(32) NOT NULL,
    quantity             INTEGER NOT NULL CHECK (quantity > 0),
    unit_price           NUMERIC(19,4) NOT NULL CHECK (unit_price >= 0),
    total_amount         NUMERIC(19,4) NOT NULL CHECK (total_amount >= 0),
    currency             CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    status               VARCHAR(16) NOT NULL CHECK (status IN ('HELD', 'CONFIRMED', 'RELEASED', 'EXPIRED')),
    created_at           TIMESTAMPTZ NOT NULL,
    expires_at           TIMESTAMPTZ NOT NULL,
    payment_reference    VARCHAR(240),
    release_reason       VARCHAR(500),
    updated_at           TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, booking_id),
    UNIQUE (tenant_id, idempotency_key),
    FOREIGN KEY (tenant_id, event_id, product_id)
        REFERENCES event_ticket_inventory (tenant_id, event_id, product_id),
    CHECK (expires_at > created_at),
    CHECK (updated_at >= created_at)
);

-- Reserve the key first in the same transaction; deferred FK allows the booking to be inserted next.
CREATE TABLE IF NOT EXISTS event_ticket_idempotency (
    tenant_id           VARCHAR(128) NOT NULL,
    idempotency_key     VARCHAR(200) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    booking_id          UUID NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, idempotency_key),
    CONSTRAINT fk_event_ticket_idempotency_booking
        FOREIGN KEY (tenant_id, booking_id)
        REFERENCES event_ticket_booking (tenant_id, booking_id)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX IF NOT EXISTS ix_event_ticket_booking_active_holds
    ON event_ticket_booking (tenant_id, event_id, product_id, expires_at)
    WHERE status = 'HELD';

CREATE INDEX IF NOT EXISTS ix_event_ticket_booking_expiry
    ON event_ticket_booking (expires_at, tenant_id, booking_id)
    WHERE status = 'HELD';
