ALTER TABLE document_outbox
    DROP CONSTRAINT IF EXISTS document_outbox_status_check;

ALTER TABLE document_outbox
    ADD CONSTRAINT ck_document_outbox_status
    CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'DEAD'));

ALTER TABLE document_outbox
    ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_error TEXT;

CREATE INDEX IF NOT EXISTS ix_document_outbox_claim
    ON document_outbox (next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'PROCESSING');
