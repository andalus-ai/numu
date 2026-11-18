-- M4b: tenant- and application-bound OAuth client credential persistence.
CREATE TABLE IF NOT EXISTS oauth_client_credential (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    application_id UUID NOT NULL,
    client_id VARCHAR(96) NOT NULL,
    secret_hash CHAR(64) NOT NULL,
    secret_prefix VARCHAR(32) NOT NULL,
    scopes TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    credential_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    rotated_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_oauth_client_credential_tenant_client UNIQUE (tenant_id, client_id),
    CONSTRAINT ck_oauth_client_credential_hash CHECK (secret_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_oauth_client_credential_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    CONSTRAINT ck_oauth_client_credential_version CHECK (credential_version > 0),
    CONSTRAINT ck_oauth_client_credential_revoked CHECK (
        (status = 'REVOKED' AND revoked_at IS NOT NULL) OR status <> 'REVOKED'
    )
);
CREATE INDEX IF NOT EXISTS idx_oauth_client_credential_tenant_application
    ON oauth_client_credential (tenant_id, application_id);
CREATE INDEX IF NOT EXISTS idx_oauth_client_credential_tenant_status
    ON oauth_client_credential (tenant_id, status);
