-- Presence is ephemeral advisory state; expired rows are removed by bounded cleanup.
CREATE TABLE workspace_page_presence_sessions (
 tenant_id UUID NOT NULL,page_id UUID NOT NULL,session_id UUID NOT NULL,actor_id UUID NOT NULL,
 last_seen_at TIMESTAMPTZ NOT NULL,expires_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(tenant_id,page_id,session_id),
 CONSTRAINT fk_workspace_presence_page FOREIGN KEY(tenant_id,page_id) REFERENCES workspace_pages(tenant_id,id) ON DELETE CASCADE,
 CONSTRAINT ck_workspace_presence_expiry CHECK(expires_at>last_seen_at)
);
CREATE INDEX ix_workspace_presence_active ON workspace_page_presence_sessions(tenant_id,page_id,expires_at,actor_id);
CREATE INDEX ix_workspace_presence_expired ON workspace_page_presence_sessions(expires_at,tenant_id,page_id);
