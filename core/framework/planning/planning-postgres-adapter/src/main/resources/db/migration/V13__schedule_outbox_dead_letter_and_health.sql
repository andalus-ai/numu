-- Make retry exhaustion explicit and observable instead of leaving rows pending forever.
ALTER TABLE schedule_change_outbox
    ADD COLUMN IF NOT EXISTS dead_lettered_at TIMESTAMPTZ;

-- Mark rows that exhausted retries before this migration was deployed.
UPDATE schedule_change_outbox
SET dead_lettered_at = created_at, claim_token = NULL, claimed_until = NULL
WHERE published_at IS NULL
  AND attempt_count >= 10
  AND dead_lettered_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_schedule_change_outbox_dead_lettered
    ON schedule_change_outbox(dead_lettered_at)
    WHERE dead_lettered_at IS NOT NULL;
