-- Space slugs are compared case-insensitively by the application; enforce the same rule
-- in PostgreSQL so concurrent requests cannot create case-only duplicates.
--
-- If this migration fails because existing rows collide after lower(), resolve those
-- collisions explicitly before retrying. Silently renaming user-owned slugs is unsafe.
CREATE UNIQUE INDEX uq_workspace_space_slug_ci
  ON workspace_spaces (tenant_id, workspace_id, lower(slug));
