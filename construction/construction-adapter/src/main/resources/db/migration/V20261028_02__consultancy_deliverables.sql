-- Tenant-scoped consultancy deliverables. The composite FK stays inside the construction capability.
CREATE TABLE IF NOT EXISTS construction_consultancy_deliverable (
    tenant_id VARCHAR(100) NOT NULL,
    deliverable_id UUID NOT NULL,
    engagement_id UUID NOT NULL,
    client_organization_id UUID NOT NULL,
    consultant_organization_id UUID NOT NULL,
    code VARCHAR(160) NOT NULL,
    title TEXT NOT NULL CHECK (length(btrim(title)) > 0),
    description TEXT NOT NULL CHECK (length(btrim(description)) > 0),
    revision INTEGER NOT NULL CHECK (revision >= 1),
    status VARCHAR(24) NOT NULL CHECK (status IN ('DRAFT', 'SUBMITTED', 'ACCEPTED', 'REJECTED')),
    reviewed_by_organization_id UUID NULL,
    review_comment TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, deliverable_id),
    CONSTRAINT uq_construction_consultancy_deliverable_code UNIQUE (tenant_id, engagement_id, code),
    CONSTRAINT fk_construction_consultancy_deliverable_engagement
        FOREIGN KEY (tenant_id, engagement_id)
        REFERENCES construction_consultancy_engagement (tenant_id, engagement_id),
    CONSTRAINT ck_construction_consultancy_deliverable_parties
        CHECK (client_organization_id <> consultant_organization_id),
    CONSTRAINT ck_construction_consultancy_deliverable_review CHECK (
        (status IN ('DRAFT', 'SUBMITTED') AND reviewed_by_organization_id IS NULL AND review_comment IS NULL)
        OR (status = 'ACCEPTED' AND reviewed_by_organization_id IS NOT NULL
            AND reviewed_by_organization_id = client_organization_id)
        OR (status = 'REJECTED' AND reviewed_by_organization_id IS NOT NULL
            AND reviewed_by_organization_id = client_organization_id
            AND review_comment IS NOT NULL AND length(btrim(review_comment)) > 0)
    )
);

CREATE INDEX IF NOT EXISTS idx_construction_consultancy_deliverable_engagement
    ON construction_consultancy_deliverable (tenant_id, engagement_id, code);
