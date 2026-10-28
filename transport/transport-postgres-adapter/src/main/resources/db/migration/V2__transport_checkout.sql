-- Durable checkout correlation for the optional Transport + Payment saga.
-- Payment and inventory remain owned by their respective capabilities.
CREATE TABLE IF NOT EXISTS transport_checkout (
    tenant_id           VARCHAR(128) NOT NULL,
    checkout_id         UUID NOT NULL,
    idempotency_key     VARCHAR(128) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    departure_id        UUID NOT NULL,
    quote_id            VARCHAR(256) NOT NULL,
    from_sequence       INTEGER NOT NULL CHECK (from_sequence >= 1),
    to_sequence         INTEGER NOT NULL CHECK (to_sequence > from_sequence),
    passenger_count     INTEGER NOT NULL CHECK (passenger_count > 0),
    amount              NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency            VARCHAR(3) NOT NULL,
    hold_expires_at     TIMESTAMPTZ NOT NULL,
    hold_id             UUID NOT NULL,
    payment_id          VARCHAR(256),
    status              VARCHAR(32) NOT NULL CHECK (status IN (
        'REQUESTED', 'HOLD_CREATED', 'PAYMENT_PENDING', 'PAYMENT_ACTION_REQUIRED',
        'PAYMENT_SUCCEEDED', 'PAYMENT_FAILED', 'CANCELLED', 'RECONCILIATION_REQUIRED'
    )),
    version             INTEGER NOT NULL CHECK (version >= 0),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, checkout_id),
    CONSTRAINT uq_transport_checkout_idempotency UNIQUE (tenant_id, idempotency_key),
    CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_transport_checkout_payment
    ON transport_checkout (tenant_id, payment_id)
    WHERE payment_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_transport_checkout_reconciliation
    ON transport_checkout (tenant_id, updated_at, status)
    WHERE status IN ('PAYMENT_PENDING', 'PAYMENT_ACTION_REQUIRED', 'RECONCILIATION_REQUIRED');
