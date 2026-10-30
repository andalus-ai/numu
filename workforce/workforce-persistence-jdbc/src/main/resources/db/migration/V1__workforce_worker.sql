-- Workforce worker aggregate persistence. All tenant-owned lookups include tenant_id.
CREATE TABLE workforce_workers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    person_id UUID NOT NULL,
    worker_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version INTEGER NOT NULL CHECK (version >= 0),
    CONSTRAINT uq_workforce_worker_tenant_id UNIQUE (tenant_id, id)
);

CREATE INDEX ix_workforce_workers_tenant_person
    ON workforce_workers (tenant_id, person_id);
CREATE INDEX ix_workforce_workers_tenant_status
    ON workforce_workers (tenant_id, status);
