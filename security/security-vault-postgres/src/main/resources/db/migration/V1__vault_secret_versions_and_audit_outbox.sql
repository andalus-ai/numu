CREATE TABLE IF NOT EXISTS vault_secret_version (
    tenant_id UUID NOT NULL,
    vault_id UUID NOT NULL,
    secret_id UUID NOT NULL,
    secret_version INTEGER NOT NULL CHECK (secret_version > 0),
    encryption_algorithm VARCHAR(80) NOT NULL,
    format_version INTEGER NOT NULL CHECK (format_version > 0),
    key_id VARCHAR(160) NOT NULL,
    ciphertext BYTEA NOT NULL CHECK (octet_length(ciphertext) > 0),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, vault_id, secret_id, secret_version),
    CHECK (expires_at IS NULL OR expires_at > created_at)
);

CREATE INDEX IF NOT EXISTS ix_vault_secret_latest
    ON vault_secret_version (tenant_id, vault_id, secret_id, secret_version DESC);

-- Immutable audit/outbox facts are committed atomically with each secret version.
-- Payload intentionally contains metadata only: never plaintext, ciphertext, or key material.
CREATE TABLE IF NOT EXISTS vault_audit_outbox (
    event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    vault_id UUID NOT NULL,
    secret_id UUID NOT NULL,
    secret_version INTEGER NOT NULL CHECK (secret_version > 0),
    actor VARCHAR(200) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    details VARCHAR(1000) NOT NULL DEFAULT '',
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(2000),
    CONSTRAINT fk_vault_audit_secret_version FOREIGN KEY
        (tenant_id, vault_id, secret_id, secret_version)
        REFERENCES vault_secret_version
        (tenant_id, vault_id, secret_id, secret_version) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_vault_audit_outbox_due
    ON vault_audit_outbox (next_attempt_at, occurred_at)
    WHERE published_at IS NULL;


-- Logical secret revocation is tenant-scoped and irreversible in this schema.
CREATE TABLE IF NOT EXISTS vault_secret_lifecycle (
    tenant_id UUID NOT NULL,
    vault_id UUID NOT NULL,
    secret_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('REVOKED')),
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, vault_id, secret_id)
);
