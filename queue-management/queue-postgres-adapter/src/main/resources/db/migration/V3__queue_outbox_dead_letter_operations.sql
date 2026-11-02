-- M5: preserve cumulative delivery and replay history while allowing an operator retry budget.
ALTER TABLE queue_outbox
    ADD COLUMN total_attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (total_attempt_count >= 0),
    ADD COLUMN replay_count INTEGER NOT NULL DEFAULT 0 CHECK (replay_count >= 0);

UPDATE queue_outbox SET total_attempt_count = attempt_count;

COMMENT ON COLUMN queue_outbox.total_attempt_count IS
    'Cumulative lease claims across original delivery and operator replays; never reset.';
COMMENT ON COLUMN queue_outbox.replay_count IS
    'Number of audited operator replays; attempt_count resets per replay cycle.';
