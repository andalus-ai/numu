-- Attendee profile data is tenant-owned contextual data. Canonical Party identity
-- remains owned by Ecosystem; this table stores only the stable Party UUID reference.
CREATE TABLE attendee_profile (
    profile_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    party_id UUID NOT NULL,
    profile_kind VARCHAR(80) NOT NULL CHECK (length(btrim(profile_kind)) > 0),
    display_name VARCHAR(240) NOT NULL CHECK (length(btrim(display_name)) > 0),
    preferred_name VARCHAR(240),
    preferred_language VARCHAR(35),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    CONSTRAINT uq_attendee_profile_tenant_party_kind
        UNIQUE (tenant_id, party_id, profile_kind),
    CONSTRAINT ck_attendee_profile_updated_at
        CHECK (updated_at >= created_at)
);

-- The primary key protects global profile identifiers. This index supports
-- tenant-scoped profile listing and lookup without exposing cross-tenant rows.
CREATE INDEX ix_attendee_profile_tenant_created
    ON attendee_profile (tenant_id, created_at DESC, profile_id);
