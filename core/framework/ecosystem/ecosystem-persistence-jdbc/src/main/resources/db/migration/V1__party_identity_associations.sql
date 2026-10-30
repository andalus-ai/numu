-- Party identity links are tenant-scoped business associations, not authorization grants.
CREATE TABLE ecosystem_party_identity_associations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    party_id UUID NOT NULL,
    issuer TEXT NOT NULL CHECK (length(btrim(issuer)) > 0),
    subject TEXT NOT NULL CHECK (length(btrim(subject)) > 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),
    linked_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_ecosystem_party_identity_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_ecosystem_party_identity_revocation CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL AND revoked_at >= linked_at)
    )
);

-- One active mapping per external subject in a tenant. Revoked rows remain as history
-- and do not prevent a later, explicitly authorized re-link operation.
CREATE UNIQUE INDEX uq_ecosystem_party_identity_active_subject
    ON ecosystem_party_identity_associations (tenant_id, issuer, subject)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_ecosystem_party_identity_party_history
    ON ecosystem_party_identity_associations (tenant_id, party_id, linked_at DESC, id);

CREATE INDEX ix_ecosystem_party_identity_subject_history
    ON ecosystem_party_identity_associations (tenant_id, issuer, subject, linked_at DESC);
