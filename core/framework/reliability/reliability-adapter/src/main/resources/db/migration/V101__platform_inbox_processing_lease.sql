-- Add an atomic processing lease to the shared inbox.
-- Existing V100 installations receive this additive migration without checksum changes.
ALTER TABLE platform_inbox
    ADD COLUMN IF NOT EXISTS processing_token uuid,
    ADD COLUMN IF NOT EXISTS locked_until timestamptz;
