ALTER TABLE numou_omnichannel_inbound_event
    ADD COLUMN IF NOT EXISTS payload_reference VARCHAR(200),
    ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ;

ALTER TABLE numou_omnichannel_inbound_event
    DROP CONSTRAINT IF EXISTS ck_numou_omnichannel_verified_payload_reference;

ALTER TABLE numou_omnichannel_inbound_event
    ADD CONSTRAINT ck_numou_omnichannel_verified_payload_reference
    CHECK (
        (state <> 'VERIFIED' OR (payload_reference IS NOT NULL AND verified_at IS NOT NULL))
    );

CREATE INDEX IF NOT EXISTS ix_numou_omnichannel_inbound_event_verified
    ON numou_omnichannel_inbound_event (state, verified_at)
    WHERE state = 'VERIFIED';
