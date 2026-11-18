-- Client authentication precedes tenant resolution. Public client IDs must be globally unique.
-- Migration intentionally fails if existing tenant-scoped IDs collide.
CREATE UNIQUE INDEX IF NOT EXISTS uq_oauth_client_credential_client_id
    ON oauth_client_credential (client_id);
