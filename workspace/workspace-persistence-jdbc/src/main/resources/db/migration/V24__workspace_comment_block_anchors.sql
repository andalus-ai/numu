-- Optional stable block anchors for page comment threads.
-- No FK to current blocks: page edits may remove a block while its discussion history must remain readable.
ALTER TABLE workspace_page_comment_threads
  ADD COLUMN anchor_block_id UUID NULL;

CREATE INDEX ix_workspace_comment_threads_block_anchor
  ON workspace_page_comment_threads (tenant_id, page_id, anchor_block_id, created_at DESC, id)
  WHERE anchor_block_id IS NOT NULL;
