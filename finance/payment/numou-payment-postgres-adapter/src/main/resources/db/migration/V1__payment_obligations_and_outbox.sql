CREATE TABLE IF NOT EXISTS payment_obligation (
    payment_id UUID PRIMARY KEY,
    tenant_id VARCHAR(200) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    caller_reference VARCHAR(255) NOT NULL,
    amount NUMERIC(38, 18) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    payment_status VARCHAR(32) NOT NULL CHECK (payment_status IN (
        'CREATED', 'REQUIRES_CUSTOMER_ACTION', 'PENDING', 'SUCCEEDED',
        'FAILED', 'CANCELLED', 'EXPIRED', 'PARTIALLY_REFUNDED', 'REFUNDED', 'UNKNOWN'
    )),
    provider_reference VARCHAR(255),
    action_uri TEXT,
    description TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_obligation_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT uq_payment_obligation_tenant_payment UNIQUE (tenant_id, payment_id)
);

CREATE INDEX IF NOT EXISTS ix_payment_obligation_tenant_reference
    ON payment_obligation (tenant_id, caller_reference, created_at DESC);

CREATE TABLE IF NOT EXISTS payment_refund_operation (
    refund_id UUID PRIMARY KEY,
    tenant_id VARCHAR(200) NOT NULL,
    payment_id UUID NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    amount NUMERIC(38, 18) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    refund_status VARCHAR(16) NOT NULL CHECK (refund_status IN ('REQUESTED', 'PENDING', 'SUCCEEDED', 'FAILED', 'UNKNOWN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_refund_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_payment_refund_tenant_payment FOREIGN KEY (tenant_id, payment_id)
        REFERENCES payment_obligation (tenant_id, payment_id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_payment_refund_tenant_payment
    ON payment_refund_operation (tenant_id, payment_id, created_at);

CREATE TABLE IF NOT EXISTS payment_operation_outbox (
    event_id UUID PRIMARY KEY,
    tenant_id VARCHAR(200) NOT NULL,
    aggregate_type VARCHAR(32) NOT NULL CHECK (aggregate_type IN ('PAYMENT', 'REFUND')),
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(2000),
    CONSTRAINT uq_payment_operation_outbox_event UNIQUE (tenant_id, aggregate_type, aggregate_id, event_type)
);

CREATE INDEX IF NOT EXISTS ix_payment_operation_outbox_due
    ON payment_operation_outbox (next_attempt_at, occurred_at)
    WHERE published_at IS NULL;
