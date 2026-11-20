-- Stable newest-first FOLLOW pagination by (created_at, relationship_id).
CREATE INDEX ix_sg_relationship_follow_outgoing_page
    ON sg_relationship (tenant_id, source_party_id, created_at DESC, relationship_id DESC)
    WHERE relationship_type = 'FOLLOW' AND status = 'ACTIVE';

CREATE INDEX ix_sg_relationship_follow_incoming_page
    ON sg_relationship (tenant_id, target_party_id, created_at DESC, relationship_id DESC)
    WHERE relationship_type = 'FOLLOW' AND status = 'ACTIVE';
