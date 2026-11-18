-- Allow multiple delivery attempts for one invitation (initial send + secure resends).
ALTER TABLE organization_invitation_delivery_outbox
    DROP CONSTRAINT uq_org_invitation_delivery;

CREATE INDEX ix_org_invitation_delivery_by_invitation
    ON organization_invitation_delivery_outbox (tenant_id, invitation_id, created_at DESC);
