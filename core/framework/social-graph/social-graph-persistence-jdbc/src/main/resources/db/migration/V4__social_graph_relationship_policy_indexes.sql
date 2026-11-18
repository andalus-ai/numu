-- Cover both orientations of BLOCK lookup and the directed MUTE lookup.
-- Policy decisions remain on authoritative PostgreSQL state.
CREATE INDEX ix_sg_relationship_policy_source
    ON sg_relationship (tenant_id, relationship_type, source_party_id, target_party_id, valid_from, valid_until)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_sg_relationship_policy_target
    ON sg_relationship (tenant_id, relationship_type, target_party_id, source_party_id, valid_from, valid_until)
    WHERE status = 'ACTIVE';
