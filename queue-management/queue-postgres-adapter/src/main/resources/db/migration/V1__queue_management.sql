-- Queue Management owns queue coordination only. Source business records remain in their
-- owning capability; source_ref_* columns are opaque references, not foreign keys.
CREATE TABLE queue_definition (
    tenant_id       UUID NOT NULL,
    queue_id        UUID NOT NULL,
    code            VARCHAR(64) NOT NULL,
    display_name    VARCHAR(120) NOT NULL,
    profile         VARCHAR(32) NOT NULL CHECK (profile IN ('WAITING_SERVICE', 'DISPATCH', 'OPERATIONAL_WORK')),
    ordering_policy VARCHAR(32) NOT NULL CHECK (ordering_policy IN ('FIFO', 'PRIORITY_THEN_ARRIVAL')),
    status          VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'PAUSED', 'CLOSED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY (tenant_id, queue_id),
    CONSTRAINT uq_queue_definition_tenant_code UNIQUE (tenant_id, code)
);

CREATE TABLE queue_entry (
    tenant_id          UUID NOT NULL,
    queue_id           UUID NOT NULL,
    entry_id           UUID NOT NULL,
    profile            VARCHAR(32) NOT NULL CHECK (profile IN ('WAITING_SERVICE', 'DISPATCH', 'OPERATIONAL_WORK')),
    source_capability  VARCHAR(80) NOT NULL,
    source_resource_type VARCHAR(80) NOT NULL,
    source_resource_id VARCHAR(200) NOT NULL,
    priority           SMALLINT NOT NULL CHECK (priority BETWEEN 0 AND 100),
    status             VARCHAR(16) NOT NULL CHECK (status IN ('WAITING', 'CALLED', 'SERVING', 'OFFERED', 'ACCEPTED', 'REJECTED', 'COMPLETED', 'NO_SHOW', 'ABANDONED', 'TRANSFERRED')),
    entered_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    called_at          TIMESTAMPTZ,
    serving_started_at TIMESTAMPTZ,
    completed_at       TIMESTAMPTZ,
    call_count         INTEGER NOT NULL DEFAULT 0 CHECK (call_count >= 0),
    version            BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    PRIMARY KEY (tenant_id, queue_id, entry_id),
    FOREIGN KEY (tenant_id, queue_id) REFERENCES queue_definition (tenant_id, queue_id),
    CONSTRAINT ck_queue_entry_timestamps CHECK (updated_at >= entered_at)
);

-- Partial indexes keep claim scans bounded and tenant/queue scoped.
CREATE INDEX ix_queue_entry_waiting_fifo
    ON queue_entry (tenant_id, queue_id, entered_at, entry_id)
    WHERE status = 'WAITING';
CREATE INDEX ix_queue_entry_waiting_priority
    ON queue_entry (tenant_id, queue_id, priority DESC, entered_at, entry_id)
    WHERE status = 'WAITING';
CREATE INDEX ix_queue_entry_source
    ON queue_entry (tenant_id, source_capability, source_resource_type, source_resource_id);

CREATE TABLE queue_audit (
    audit_id       UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    actor_id       VARCHAR(200) NOT NULL,
    action         VARCHAR(100) NOT NULL,
    resource_type  VARCHAR(80) NOT NULL,
    resource_id    VARCHAR(200) NOT NULL,
    correlation_id VARCHAR(200) NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL,
    details        JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX ix_queue_audit_tenant_resource_time
    ON queue_audit (tenant_id, resource_type, resource_id, occurred_at DESC);

CREATE TABLE queue_outbox (
    event_id       UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    event_type     VARCHAR(160) NOT NULL,
    schema_version INTEGER NOT NULL CHECK (schema_version > 0),
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id   VARCHAR(200) NOT NULL,
    correlation_id VARCHAR(200) NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL,
    payload        JSONB NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at   TIMESTAMPTZ,
    attempt_count  INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    last_error     VARCHAR(2000)
);
CREATE INDEX ix_queue_outbox_unpublished
    ON queue_outbox (created_at, event_id)
    WHERE published_at IS NULL;

-- Idempotency result and queue mutation must be committed in the same DB transaction.
CREATE TABLE queue_idempotency (
    tenant_id       UUID NOT NULL,
    operation_scope VARCHAR(160) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_hash    VARCHAR(128) NOT NULL,
    state           VARCHAR(16) NOT NULL CHECK (state IN ('IN_PROGRESS', 'COMPLETED')),
    result_type     VARCHAR(160),
    result_payload  JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, operation_scope, idempotency_key),
    CONSTRAINT ck_queue_idempotency_completion CHECK (
        (state = 'IN_PROGRESS' AND completed_at IS NULL)
        OR (state = 'COMPLETED' AND completed_at IS NOT NULL AND result_payload IS NOT NULL)
    )
);

-- Defense-in-depth tenant RLS. Runtime roles must not own these tables or have BYPASSRLS.
-- The application transaction must SET LOCAL numou.tenant_id to the authenticated tenant UUID.
ALTER TABLE queue_definition ENABLE ROW LEVEL SECURITY;
ALTER TABLE queue_definition FORCE ROW LEVEL SECURITY;
CREATE POLICY queue_definition_tenant_isolation ON queue_definition
    USING (tenant_id = NULLIF(current_setting('numou.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('numou.tenant_id', true), '')::uuid);

ALTER TABLE queue_entry ENABLE ROW LEVEL SECURITY;
ALTER TABLE queue_entry FORCE ROW LEVEL SECURITY;
CREATE POLICY queue_entry_tenant_isolation ON queue_entry
    USING (tenant_id = NULLIF(current_setting('numou.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('numou.tenant_id', true), '')::uuid);

ALTER TABLE queue_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE queue_audit FORCE ROW LEVEL SECURITY;
CREATE POLICY queue_audit_tenant_isolation ON queue_audit
    USING (tenant_id = NULLIF(current_setting('numou.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('numou.tenant_id', true), '')::uuid);

ALTER TABLE queue_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE queue_outbox FORCE ROW LEVEL SECURITY;
CREATE POLICY queue_outbox_tenant_isolation ON queue_outbox
    USING (tenant_id = NULLIF(current_setting('numou.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('numou.tenant_id', true), '')::uuid);

ALTER TABLE queue_idempotency ENABLE ROW LEVEL SECURITY;
ALTER TABLE queue_idempotency FORCE ROW LEVEL SECURITY;
CREATE POLICY queue_idempotency_tenant_isolation ON queue_idempotency
    USING (tenant_id = NULLIF(current_setting('numou.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('numou.tenant_id', true), '')::uuid);
