-- Tenant-scoped consultancy engagements. Project and organization IDs are opaque references
-- owned by their respective capabilities; no cross-capability persistence foreign keys are assumed.
CREATE TABLE IF NOT EXISTS construction_consultancy_engagement (
    tenant_id VARCHAR(100) NOT NULL,
    engagement_id UUID NOT NULL,
    project_id UUID NOT NULL,
    client_organization_id UUID NOT NULL,
    consultant_organization_id UUID NOT NULL,
    engagement_number VARCHAR(160) NOT NULL,
    title TEXT NOT NULL CHECK (length(btrim(title)) > 0),
    scope_statement TEXT NOT NULL CHECK (length(btrim(scope_statement)) > 0),
    scope_revision INTEGER NOT NULL CHECK (scope_revision >= 1),
    status VARCHAR(24) NOT NULL CHECK (status IN ('DRAFT', 'PROPOSED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    cancellation_reason TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, engagement_id),
    CONSTRAINT uq_construction_consultancy_engagement_number UNIQUE (tenant_id, engagement_number),
    CONSTRAINT ck_construction_consultancy_distinct_parties CHECK (client_organization_id <> consultant_organization_id),
    CONSTRAINT ck_construction_consultancy_cancellation CHECK (
        (status = 'CANCELLED' AND cancellation_reason IS NOT NULL AND length(btrim(cancellation_reason)) > 0)
        OR (status <> 'CANCELLED' AND cancellation_reason IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_construction_consultancy_project
    ON construction_consultancy_engagement (tenant_id, project_id, created_at DESC);

