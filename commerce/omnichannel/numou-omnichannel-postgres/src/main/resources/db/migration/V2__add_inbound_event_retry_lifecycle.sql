ALTER TABLE numou_omnichannel_inbound_event
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS lease_token UUID,
    ADD COLUMN IF NOT EXISTS last_error_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS processed_at TIMESTAMPTZ;

ALTER TABLE numou_omnichannel_inbound_event
    DROP CONSTRAINT IF EXISTS ck_numou_omnichannel_inbound_event_attempt_count;

ALTER TABLE numou_omnichannel_inbound_event
    ADD CONSTRAINT ck_numou_omnichannel_inbound_event_attempt_count
    CHECK (attempt_count >= 0);

CREATE INDEX IF NOT EXISTS ix_numou_omnichannel_inbound_event_due
    ON numou_omnichannel_inbound_event (state, next_attempt_at, received_at);

CREATE INDEX IF NOT EXISTS ix_numou_omnichannel_inbound_event_expired_lease
    ON numou_omnichannel_inbound_event (state, lease_until);
