-- PostgreSQL schema for the durable M7 recovery journal.
-- Tenant IDs remain opaque strings at this boundary to match the domain contract.
CREATE TABLE warehouse_execution_attempt (
    tenant_id VARCHAR(200) NOT NULL,
    command_key VARCHAR(300) NOT NULL,
    fingerprint VARCHAR(200) NOT NULL,
    work_order_id VARCHAR(200) NOT NULL,
    line_id VARCHAR(200) NOT NULL,
    operation VARCHAR(80) NOT NULL,
    status VARCHAR(40) NOT NULL CHECK (status IN
        ('PREPARED', 'INVENTORY_POSTED', 'WAREHOUSE_COMMITTED', 'NEEDS_RECONCILIATION')),
    attempt_count INTEGER NOT NULL CHECK (attempt_count > 0),
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0),
    PRIMARY KEY (tenant_id, command_key),
    CHECK (updated_at >= created_at),
    CHECK (status <> 'NEEDS_RECONCILIATION' OR NULLIF(BTRIM(last_error), '') IS NOT NULL)
);

CREATE INDEX ix_warehouse_execution_recovery
    ON warehouse_execution_attempt (updated_at, created_at)
    WHERE status IN ('PREPARED', 'INVENTORY_POSTED', 'NEEDS_RECONCILIATION');
