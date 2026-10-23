-- PostgreSQL full-text indexes for tenant/workspace-scoped page search.
CREATE INDEX ix_workspace_pages_title_fts
  ON workspace_pages USING GIN (to_tsvector('simple', title));

CREATE INDEX ix_workspace_page_blocks_text_fts
  ON workspace_page_blocks USING GIN (to_tsvector('simple', text_content));
