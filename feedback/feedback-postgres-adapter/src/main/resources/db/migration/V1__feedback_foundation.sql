CREATE TABLE feedback_items (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    author_type VARCHAR(32) NOT NULL,
    author_id VARCHAR(200) NOT NULL,
    kind_code VARCHAR(64) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(20000) NOT NULL,
    context_application VARCHAR(100) NULL,
    context_module VARCHAR(100) NULL,
    context_screen VARCHAR(200) NULL,
    context_record_type VARCHAR(100) NULL,
    context_record_reference VARCHAR(200) NULL,
    context_application_version VARCHAR(100) NULL,
    status VARCHAR(30) NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT feedback_items_tenant_id_id_uq UNIQUE (tenant_id, id)
);

CREATE TABLE feedback_idempotency (
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    feedback_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, idempotency_key),
    CONSTRAINT feedback_idempotency_item_fk
        FOREIGN KEY (tenant_id, feedback_id)
        REFERENCES feedback_items (tenant_id, id)
        ON DELETE CASCADE
);

CREATE TABLE feedback_outbox (
    event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    event_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(2000) NULL,
    CONSTRAINT feedback_outbox_item_fk
        FOREIGN KEY (tenant_id, aggregate_id)
        REFERENCES feedback_items (tenant_id, id)
        ON DELETE CASCADE
);

CREATE INDEX feedback_items_tenant_created_idx
    ON feedback_items (tenant_id, created_at DESC);
CREATE INDEX feedback_items_tenant_status_idx
    ON feedback_items (tenant_id, status, updated_at DESC);
CREATE INDEX feedback_outbox_pending_idx
    ON feedback_outbox (created_at)
    WHERE published_at IS NULL;
