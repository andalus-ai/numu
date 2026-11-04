ALTER TABLE vault_audit_outbox
    ADD COLUMN IF NOT EXISTS lease_owner VARCHAR(160),
    ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS dead_lettered_at TIMESTAMPTZ;

ALTER TABLE vault_audit_outbox
    ADD CONSTRAINT ck_vault_audit_lease_pair
    CHECK ((lease_owner IS NULL) = (lease_until IS NULL));

CREATE INDEX IF NOT EXISTS ix_vault_audit_outbox_claim
    ON vault_audit_outbox (next_attempt_at, occurred_at)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL;
