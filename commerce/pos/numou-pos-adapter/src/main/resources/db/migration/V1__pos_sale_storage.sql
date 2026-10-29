CREATE TABLE IF NOT EXISTS pos_sale (
    tenant_id UUID NOT NULL,
    sale_id UUID NOT NULL,
    version BIGINT NOT NULL CHECK (version >= 0),
    sale_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, sale_id)
);

CREATE INDEX IF NOT EXISTS ix_pos_sale_tenant_status
    ON pos_sale (tenant_id, (sale_payload ->> 'status'), updated_at);

CREATE TABLE IF NOT EXISTS pos_command_idempotency (
    tenant_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    operation VARCHAR(100) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    command_status VARCHAR(16) NOT NULL CHECK (command_status IN ('PROCESSING', 'COMPLETED')),
    result_payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, actor_id, idempotency_key),
    CHECK (
        (command_status = 'PROCESSING' AND result_payload IS NULL AND completed_at IS NULL)
        OR
        (command_status = 'COMPLETED' AND result_payload IS NOT NULL AND completed_at IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS ix_pos_command_idempotency_created_at
    ON pos_command_idempotency (created_at);
