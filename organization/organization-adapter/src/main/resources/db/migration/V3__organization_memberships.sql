-- Membership records are tenant-owned. Organization and Identity remain the owners
-- of their aggregates; this table stores stable identifiers, not cross-capability FKs.
CREATE TABLE organization_memberships (
    membership_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    user_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'REVOKED')),
    role VARCHAR(16) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'DEVELOPER', 'VIEWER')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_organization_membership_tenant_org_user
        UNIQUE (tenant_id, organization_id, user_id),
    CONSTRAINT ck_organization_membership_owner_active
        CHECK (role <> 'OWNER' OR status = 'ACTIVE'),
    CONSTRAINT ck_organization_membership_updated_at
        CHECK (updated_at >= created_at)
);

-- The owner is unique per tenant + organization, including across concurrent writers.
CREATE UNIQUE INDEX uq_organization_membership_single_owner
    ON organization_memberships (tenant_id, organization_id)
    WHERE role = 'OWNER' AND status = 'ACTIVE';

CREATE INDEX ix_organization_membership_tenant_org_status
    ON organization_memberships (tenant_id, organization_id, status, created_at, membership_id);

CREATE INDEX ix_organization_membership_tenant_user
    ON organization_memberships (tenant_id, user_id, status, organization_id);
