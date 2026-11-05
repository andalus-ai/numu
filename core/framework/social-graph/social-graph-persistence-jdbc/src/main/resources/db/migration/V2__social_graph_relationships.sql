CREATE TABLE sg_relationship (
    tenant_id UUID NOT NULL,
    relationship_id UUID NOT NULL,
    relationship_type VARCHAR(24) NOT NULL CHECK (relationship_type IN ('FOLLOW', 'BLOCK', 'MUTE')),
    source_party_id UUID NOT NULL,
    target_party_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'REMOVED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    removed_at TIMESTAMPTZ,
    provenance VARCHAR(128) NOT NULL CHECK (length(trim(provenance)) > 0),
    version INTEGER NOT NULL CHECK (version >= 0),
    PRIMARY KEY (tenant_id, relationship_id),
    CHECK (source_party_id <> target_party_id),
    CHECK (updated_at >= created_at),
    CHECK (valid_from >= created_at),
    CHECK (valid_until IS NULL OR valid_until > valid_from),
    CHECK ((status = 'ACTIVE' AND removed_at IS NULL) OR
           (status = 'REMOVED' AND removed_at IS NOT NULL AND removed_at >= updated_at))
);

CREATE UNIQUE INDEX ux_sg_relationship_active_edge
    ON sg_relationship (tenant_id, relationship_type, source_party_id, target_party_id)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_sg_relationship_outgoing
    ON sg_relationship (tenant_id, source_party_id, relationship_type, updated_at DESC);

CREATE INDEX ix_sg_relationship_incoming
    ON sg_relationship (tenant_id, target_party_id, relationship_type, updated_at DESC);

CREATE INDEX ix_sg_relationship_effective
    ON sg_relationship (tenant_id, relationship_type, source_party_id, valid_from, valid_until)
    WHERE status = 'ACTIVE';

CREATE TABLE sg_relationship_metadata (
    tenant_id UUID NOT NULL,
    relationship_id UUID NOT NULL,
    metadata_key VARCHAR(64) NOT NULL CHECK (length(trim(metadata_key)) > 0),
    metadata_value VARCHAR(512) NOT NULL,
    PRIMARY KEY (tenant_id, relationship_id, metadata_key),
    CONSTRAINT fk_sg_relationship_metadata
        FOREIGN KEY (tenant_id, relationship_id)
        REFERENCES sg_relationship (tenant_id, relationship_id)
        ON DELETE CASCADE
);
