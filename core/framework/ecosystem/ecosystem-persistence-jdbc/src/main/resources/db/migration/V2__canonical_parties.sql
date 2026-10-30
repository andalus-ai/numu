-- Canonical Party identity is cross-tenant. Tenant membership and external
-- authentication links are separate associations owned by their capabilities.
CREATE TABLE ecosystem_party (
    party_id UUID PRIMARY KEY,
    kind VARCHAR(24) NOT NULL CHECK (kind IN ('PERSON', 'ORGANIZATION')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version INTEGER NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT ck_ecosystem_party_updated_at CHECK (updated_at >= created_at)
);

CREATE INDEX ix_ecosystem_party_kind_status
    ON ecosystem_party (kind, status, party_id);
