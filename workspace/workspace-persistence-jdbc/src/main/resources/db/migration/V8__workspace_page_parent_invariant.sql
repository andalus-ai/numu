-- A page hierarchy cannot contain a page as its own direct parent.
-- Fail rather than silently rewrite any pre-existing invalid hierarchy.
ALTER TABLE workspace_pages
  ADD CONSTRAINT ck_workspace_page_not_own_parent
  CHECK (parent_page_id IS NULL OR parent_page_id <> id);
