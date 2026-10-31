CREATE TABLE organization_party_associations (
    association_id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    party_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (
        status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'SUPERSEDED', 'REVOKED')
    ),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ NULL,
    closed_at TIMESTAMPTZ NULL,
    created_by VARCHAR(200) NOT NULL,
    updated_by VARCHAR(200) NOT NULL,
    evidence_reference VARCHAR(500) NULL,
    reason VARCHAR(1000) NULL,
    version BIGINT NOT NULL CHECK (version >= 0),
    CONSTRAINT ck_org_party_association_lifecycle CHECK (
        (status = 'PENDING_VERIFICATION' AND verified_at IS NULL AND closed_at IS NULL)
        OR (status = 'ACTIVE' AND verified_at IS NOT NULL AND closed_at IS NULL)
        OR (status = 'SUSPENDED' AND verified_at IS NOT NULL AND closed_at IS NULL)
        OR (status IN ('SUPERSEDED', 'REVOKED') AND closed_at IS NOT NULL)
    ),
    CONSTRAINT ck_org_party_association_timestamps CHECK (
        updated_at >= created_at
        AND (verified_at IS NULL OR verified_at >= created_at)
        AND (closed_at IS NULL OR closed_at >= created_at)
    )
);

-- A pending, active, or suspended link reserves both identities. Terminal history remains queryable.
CREATE UNIQUE INDEX uq_org_party_association_current_organization
    ON organization_party_associations (organization_id)
    WHERE status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED');

CREATE UNIQUE INDEX uq_org_party_association_current_party
    ON organization_party_associations (party_id)
    WHERE status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED');

CREATE INDEX ix_org_party_association_history_lookup
    ON organization_party_associations (organization_id, created_at DESC, association_id);

CREATE TABLE organization_party_association_history (
    event_id UUID PRIMARY KEY,
    association_id UUID NOT NULL REFERENCES organization_party_associations(association_id),
    event_type VARCHAR(64) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    actor_id VARCHAR(200) NOT NULL,
    reason VARCHAR(1000) NULL
);

CREATE INDEX ix_org_party_association_events
    ON organization_party_association_history (association_id, occurred_at, event_id);
