CREATE TABLE IF NOT EXISTS payment_provider_event_inbox (
    inbox_id UUID PRIMARY KEY,
    tenant_id VARCHAR(200) NOT NULL,
    provider_name VARCHAR(120) NOT NULL,
    provider_event_id VARCHAR(255) NOT NULL,
    payment_id UUID NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    provider_occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_provider_event UNIQUE (tenant_id, provider_name, provider_event_id),
    CONSTRAINT fk_payment_provider_event_tenant_payment
        FOREIGN KEY (tenant_id, payment_id)
        REFERENCES payment_obligation (tenant_id, payment_id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_payment_provider_event_payment
    ON payment_provider_event_inbox (tenant_id, payment_id, received_at DESC);
