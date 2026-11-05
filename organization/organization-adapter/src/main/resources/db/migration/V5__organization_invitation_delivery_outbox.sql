-- Durable, encrypted invitation delivery attempts. Bearer secrets are never stored in plaintext.
CREATE TABLE organization_invitation_delivery_outbox (
    delivery_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    invitation_id UUID NOT NULL,
    recipient_email VARCHAR(320) NOT NULL,
    encryption_algorithm VARCHAR(80) NOT NULL,
    encryption_format_version INTEGER NOT NULL CHECK (encryption_format_version > 0),
    encryption_key_id VARCHAR(160) NOT NULL,
    vault_id UUID NOT NULL,
    secret_id UUID NOT NULL,
    secret_ciphertext BYTEA NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'LEASED', 'SENT', 'DEAD')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at TIMESTAMPTZ NOT NULL,
    lease_token UUID,
    lease_until TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    last_error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_org_invitation_delivery UNIQUE (tenant_id, invitation_id),
    CONSTRAINT ck_org_invitation_delivery_lease CHECK (
        (status = 'LEASED' AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'LEASED' AND lease_token IS NULL AND lease_until IS NULL)
    ),
    CONSTRAINT ck_org_invitation_delivery_terminal CHECK (
        (status = 'SENT' AND sent_at IS NOT NULL AND failed_at IS NULL)
        OR (status = 'DEAD' AND failed_at IS NOT NULL AND sent_at IS NULL)
        OR (status IN ('PENDING', 'LEASED') AND sent_at IS NULL AND failed_at IS NULL)
    )
);
CREATE INDEX ix_org_invitation_delivery_claim
    ON organization_invitation_delivery_outbox (available_at, created_at, delivery_id)
    WHERE status IN ('PENDING', 'LEASED');
CREATE INDEX ix_org_invitation_delivery_tenant_status
    ON organization_invitation_delivery_outbox (tenant_id, status, created_at DESC);
