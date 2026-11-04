-- Invitation lifecycle is tenant-owned. No cross-capability foreign keys are assumed.
CREATE TABLE organization_invitations (
    invitation_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    recipient_email VARCHAR(320) NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('ADMIN', 'DEVELOPER', 'VIEWER')),
    invited_by UUID NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    accepted_by UUID,
    accepted_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version INTEGER NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_organization_invitation_token UNIQUE (tenant_id, token_hash),
    CONSTRAINT ck_organization_invitation_state CHECK (
        (status = 'ACCEPTED' AND accepted_by IS NOT NULL AND accepted_at IS NOT NULL AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND accepted_by IS NULL AND accepted_at IS NULL AND revoked_at IS NOT NULL)
        OR (status IN ('PENDING', 'EXPIRED') AND accepted_by IS NULL AND accepted_at IS NULL AND revoked_at IS NULL)
    ),
    CONSTRAINT ck_organization_invitation_time CHECK (updated_at >= created_at)
);

CREATE INDEX ix_organization_invitation_pending_email
    ON organization_invitations (tenant_id, organization_id, recipient_email, expires_at)
    WHERE status = 'PENDING';
CREATE INDEX ix_organization_invitation_tenant_org
    ON organization_invitations (tenant_id, organization_id, created_at DESC, invitation_id);
