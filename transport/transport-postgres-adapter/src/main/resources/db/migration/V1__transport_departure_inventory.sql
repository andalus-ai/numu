-- PostgreSQL schema owned by the Transport capability.
-- All writes to one inventory and its holds must occur in one transaction.
CREATE TABLE IF NOT EXISTS transport_departure_inventory (
    tenant_id       VARCHAR(128) NOT NULL,
    departure_id    UUID NOT NULL,
    capacity        INTEGER NOT NULL CHECK (capacity > 0),
    stop_count      INTEGER NOT NULL CHECK (stop_count >= 2),
    version         BIGINT NOT NULL CHECK (version >= 0),
    PRIMARY KEY (tenant_id, departure_id)
);

CREATE TABLE IF NOT EXISTS transport_passenger_hold (
    tenant_id           VARCHAR(128) NOT NULL,
    departure_id        UUID NOT NULL,
    hold_id             UUID NOT NULL,
    idempotency_key     TEXT NOT NULL,
    request_fingerprint TEXT NOT NULL,
    from_sequence       INTEGER NOT NULL CHECK (from_sequence >= 1),
    to_sequence         INTEGER NOT NULL CHECK (to_sequence > from_sequence),
    passenger_count     INTEGER NOT NULL CHECK (passenger_count > 0),
    expires_at          TIMESTAMPTZ NOT NULL,
    status              VARCHAR(16) NOT NULL CHECK (status IN ('HELD', 'CONFIRMED', 'RELEASED', 'EXPIRED')),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, departure_id, hold_id),
    CONSTRAINT uq_transport_inventory_idempotency
        UNIQUE (tenant_id, departure_id, idempotency_key),
    CONSTRAINT fk_transport_hold_inventory
        FOREIGN KEY (tenant_id, departure_id)
        REFERENCES transport_departure_inventory (tenant_id, departure_id),
    CHECK (to_sequence <= 1000000),
    CHECK (expires_at > created_at),
    CHECK (updated_at >= created_at)
);

CREATE INDEX IF NOT EXISTS ix_transport_hold_expiry
    ON transport_passenger_hold (tenant_id, expires_at)
    WHERE status = 'HELD';

CREATE INDEX IF NOT EXISTS ix_transport_hold_segment
    ON transport_passenger_hold (tenant_id, departure_id, from_sequence, to_sequence);
