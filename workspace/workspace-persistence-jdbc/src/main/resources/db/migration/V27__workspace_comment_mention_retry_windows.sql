-- Preserve lifetime delivery attempts while allowing an operator replay a fresh retry budget.
ALTER TABLE workspace_comment_mention_outbox
    ADD COLUMN retry_attempts INTEGER NOT NULL DEFAULT 0
        CHECK (retry_attempts >= 0);

-- Existing rows have attempts as their historical count; before this migration all rows
-- shared one retry window, so initialize the new window counter from that count.
UPDATE workspace_comment_mention_outbox
SET retry_attempts = attempts;
