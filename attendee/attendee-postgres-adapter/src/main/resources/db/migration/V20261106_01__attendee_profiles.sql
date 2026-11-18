-- Attendee owns contextual participation profiles; canonical Party identity
-- remains owned by Ecosystem. Tenant ownership is local to this capability.
CREATE TABLE attendee_profile (
    profile_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    party_id UUID NOT NULL REFERENCES ecosystem_party (party_id) ON DELETE RESTRICT,
    profile_kind VARCHAR(80) NOT NULL,
    display_name VARCHAR(240) NOT NULL,
    preferred_name VARCHAR(240),
    preferred_language VARCHAR(35),
    status VARCHAR(16) NOT NULL
        CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    CONSTRAINT ck_attendee_profile_kind_nonblank CHECK (length(btrim(profile_kind)) > 0),
    CONSTRAINT ck_attendee_profile_display_name_nonblank CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT ck_attendee_profile_updated_at CHECK (updated_at >= created_at),
    CONSTRAINT uq_attendee_profile_tenant_party_kind
        UNIQUE (tenant_id, party_id, profile_kind)
);

CREATE INDEX ix_attendee_profile_tenant_status_updated
    ON attendee_profile (tenant_id, status, updated_at DESC, profile_id);

CREATE INDEX ix_attendee_profile_tenant_party
    ON attendee_profile (tenant_id, party_id);
