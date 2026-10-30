-- Business relationship, separate from authentication linkage and participant-to-participant links.
CREATE TABLE ecosystem_party_participant_associations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    party_id UUID NOT NULL REFERENCES ecosystem_party(party_id),
    participant_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL CHECK (
        role IN ('CUSTOMER', 'PARTNER', 'SUPPLIER', 'SERVICE_PROVIDER', 'MEMBER', 'EMPLOYEE', 'OTHER')
    ),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),
    effective_from TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL,
    CONSTRAINT ck_ecosystem_party_participant_lifecycle CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL AND revoked_at >= effective_from)
    )
);

CREATE UNIQUE INDEX uq_ecosystem_party_participant_active
    ON ecosystem_party_participant_associations (tenant_id, party_id, participant_id, role)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_ecosystem_party_participant_party
    ON ecosystem_party_participant_associations (tenant_id, party_id, effective_from DESC, id)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_ecosystem_party_participant_participant
    ON ecosystem_party_participant_associations (tenant_id, participant_id, role, effective_from DESC, id)
    WHERE status = 'ACTIVE';
