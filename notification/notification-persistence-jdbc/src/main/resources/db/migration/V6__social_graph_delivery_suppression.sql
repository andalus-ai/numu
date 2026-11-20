-- Preserve source attribution so delivery policy can be re-evaluated at dispatch time.
ALTER TABLE notification_inbox
    ADD COLUMN source_actor_party_id UUID NULL;

-- Suppression is a policy outcome, not a provider failure or dead-letter condition.
ALTER TABLE notification_delivery_job
    DROP CONSTRAINT notification_delivery_job_status_check;

ALTER TABLE notification_delivery_job
    ADD CONSTRAINT notification_delivery_job_status_check
    CHECK (status IN ('PENDING', 'IN_FLIGHT', 'RETRY_WAIT', 'DELIVERED', 'DEAD', 'SUPPRESSED'));

CREATE INDEX notification_delivery_job_suppressed
    ON notification_delivery_job (tenant_id, created_at DESC, id)
    WHERE status = 'SUPPRESSED';
