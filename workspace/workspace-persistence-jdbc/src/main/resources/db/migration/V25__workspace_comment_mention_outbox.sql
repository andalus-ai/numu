CREATE TABLE workspace_comment_mention_outbox (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL, page_id UUID NOT NULL, thread_id UUID NOT NULL, comment_id UUID NOT NULL,
 recipient_id UUID NOT NULL, actor_id UUID NOT NULL, handle VARCHAR(64) NOT NULL, actor_display_name VARCHAR(160) NOT NULL,
 page_title VARCHAR(500) NOT NULL DEFAULT '', idempotency_key VARCHAR(240) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','LEASED','DELIVERED','DEAD')),
 attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts>=0), available_at TIMESTAMPTZ NOT NULL,
 lease_owner VARCHAR(160), lease_token UUID, lease_until TIMESTAMPTZ, last_error VARCHAR(2000),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uq_workspace_mention_outbox_idempotency UNIQUE(tenant_id,idempotency_key),
 CONSTRAINT fk_workspace_mention_outbox_comment FOREIGN KEY(tenant_id,page_id,thread_id,comment_id)
 REFERENCES workspace_page_comments(tenant_id,page_id,thread_id,id) ON DELETE RESTRICT,
 CONSTRAINT ck_workspace_mention_outbox_lease CHECK(
  (status='LEASED' AND lease_owner IS NOT NULL AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
  OR (status<>'LEASED' AND lease_owner IS NULL AND lease_token IS NULL AND lease_until IS NULL))
);
CREATE INDEX ix_workspace_mention_outbox_due ON workspace_comment_mention_outbox(available_at,created_at,id) WHERE status='PENDING';
CREATE INDEX ix_workspace_mention_outbox_expired_lease ON workspace_comment_mention_outbox(lease_until,created_at,id) WHERE status='LEASED';
CREATE INDEX ix_workspace_mention_outbox_tenant_dead ON workspace_comment_mention_outbox(tenant_id,created_at,id) WHERE status='DEAD';
