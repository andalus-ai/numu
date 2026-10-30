-- Tenant-scoped page discussions. Comment entries are append-only; thread resolution is revisioned.
CREATE TABLE workspace_page_comment_threads (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  page_id UUID NOT NULL,
  resolved BOOLEAN NOT NULL DEFAULT FALSE,
  revision BIGINT NOT NULL DEFAULT 1 CHECK (revision >= 1),
  resolved_by UUID NULL,
  resolved_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_workspace_comment_thread_scope UNIQUE (tenant_id, page_id, id),
  CONSTRAINT fk_workspace_comment_thread_page
    FOREIGN KEY (tenant_id, page_id)
    REFERENCES workspace_pages (tenant_id, id) ON DELETE RESTRICT,
  CONSTRAINT ck_workspace_comment_thread_resolution
    CHECK ((resolved AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL)
        OR (NOT resolved AND resolved_by IS NULL AND resolved_at IS NULL))
);

CREATE INDEX ix_workspace_comment_threads_page
  ON workspace_page_comment_threads (tenant_id, page_id, created_at DESC, id);

CREATE TABLE workspace_page_comments (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  page_id UUID NOT NULL,
  thread_id UUID NOT NULL,
  parent_comment_id UUID NULL,
  author_id UUID NOT NULL,
  body VARCHAR(10000) NOT NULL CHECK (length(btrim(body)) > 0),
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_workspace_page_comment_scope UNIQUE (tenant_id, page_id, thread_id, id),
  CONSTRAINT fk_workspace_page_comment_thread
    FOREIGN KEY (tenant_id, page_id, thread_id)
    REFERENCES workspace_page_comment_threads (tenant_id, page_id, id) ON DELETE RESTRICT,
  CONSTRAINT fk_workspace_page_comment_parent
    FOREIGN KEY (tenant_id, page_id, thread_id, parent_comment_id)
    REFERENCES workspace_page_comments (tenant_id, page_id, thread_id, id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_workspace_page_comment_root
  ON workspace_page_comments (tenant_id, page_id, thread_id)
  WHERE parent_comment_id IS NULL;

CREATE INDEX ix_workspace_page_comments_thread
  ON workspace_page_comments (tenant_id, page_id, thread_id, created_at, id);

CREATE FUNCTION workspace_reject_comment_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'workspace page comments are append-only';
END;
$$;

CREATE TRIGGER trg_workspace_page_comments_append_only
  BEFORE UPDATE OR DELETE ON workspace_page_comments
  FOR EACH ROW EXECUTE FUNCTION workspace_reject_comment_mutation();
