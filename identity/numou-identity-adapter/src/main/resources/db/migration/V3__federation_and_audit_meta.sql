-- V3: audit metadata (AuditMeta), federated identity links (OIDC JIT provisioning)

-- --- AuditMeta columns -------------------------------------------------------
-- role_assignment needs no new columns: granted_by_user_id/granted_at already are its
-- created* audit fields and grants are immutable (revocation is an audit event).

ALTER TABLE service_accounts ADD COLUMN IF NOT EXISTS audit_created_by VARCHAR(128);
ALTER TABLE service_accounts ADD COLUMN IF NOT EXISTS audit_updated_by VARCHAR(128);
ALTER TABLE service_accounts ADD COLUMN IF NOT EXISTS audit_updated_at TIMESTAMPTZ;

ALTER TABLE personal_access_token ADD COLUMN IF NOT EXISTS audit_created_by VARCHAR(128);
ALTER TABLE personal_access_token ADD COLUMN IF NOT EXISTS audit_updated_by VARCHAR(128);
ALTER TABLE personal_access_token ADD COLUMN IF NOT EXISTS audit_updated_at TIMESTAMPTZ;

-- Backfill pre-existing rows from the fields we already know.
UPDATE service_accounts
SET audit_created_by = COALESCE(owner_user_id::text, 'system'),
    audit_updated_by = COALESCE(owner_user_id::text, 'system'),
    audit_updated_at = COALESCE(created_at, now())
WHERE audit_created_by IS NULL;

UPDATE personal_access_token
SET audit_created_by = principal_id::text,
    audit_updated_by = principal_id::text,
    audit_updated_at = COALESCE(revoked_at, created_at)
WHERE audit_created_by IS NULL;

-- --- Federated identity links ------------------------------------------------
CREATE TABLE IF NOT EXISTS federated_identity (
    id            UUID PRIMARY KEY,
    provider_id   UUID        NOT NULL REFERENCES identity_provider_config (id) ON DELETE CASCADE,
    subject       VARCHAR(255) NOT NULL,
    user_id       UUID        NOT NULL REFERENCES identity_users (id) ON DELETE CASCADE,
    email         VARCHAR(320),
    last_login_at TIMESTAMPTZ,
    CONSTRAINT uq_federated_identity_subject UNIQUE (provider_id, subject)
);

CREATE INDEX IF NOT EXISTS idx_federated_identity_user ON federated_identity (user_id);
