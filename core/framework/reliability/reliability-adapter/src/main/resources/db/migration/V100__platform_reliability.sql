-- Platform reliability + audit tables (Flyway). Shared by every service in the suite.
-- Conventions: timestamptz everywhere; tenant_id is uuid; no tenant data in infra tables' keys.

-- ---------------------------------------------------------------------------
-- Transactional outbox. Written in the SAME transaction as the aggregate change.
-- A relay claims rows with a short lease (no long-running transactions while
-- publishing) and marks them published after the broker acknowledges.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS platform_outbox (
    entry_id      uuid        PRIMARY KEY,
    event_id      uuid        NOT NULL,
    tenant_id     uuid,
    event_type    text        NOT NULL,
    payload       jsonb       NOT NULL,                 -- full envelope, wire format v1
    created_at    timestamptz NOT NULL,
    available_at  timestamptz NOT NULL,
    attempts      integer     NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    locked_until  timestamptz,
    published_at  timestamptz,
    last_error    text,
    CONSTRAINT platform_outbox_event_uq UNIQUE (event_id)   -- appending the same event twice is a no-op
);

-- The relay's hot query: due, unpublished, not exhausted, not leased.
CREATE INDEX IF NOT EXISTS platform_outbox_due_idx
    ON platform_outbox (available_at, created_at)
    WHERE published_at IS NULL AND attempts < 10;

-- Dead letters (attempts >= 10, unpublished) - alert when this is non-empty.
CREATE INDEX IF NOT EXISTS platform_outbox_dead_idx
    ON platform_outbox (created_at)
    WHERE published_at IS NULL AND attempts >= 10;

-- ---------------------------------------------------------------------------
-- Inbox: deduplicates inbound events before the domain sees them.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS platform_inbox (
    entry_id     uuid        PRIMARY KEY,
    event_id     text        NOT NULL,
    event_type   text        NOT NULL,
    received_at  timestamptz NOT NULL,
    handled      boolean     NOT NULL DEFAULT false,
    CONSTRAINT platform_inbox_event_uq UNIQUE (event_id)
);

-- ---------------------------------------------------------------------------
-- Idempotency keys for caller-facing writes. `scope` MUST include the tenant
-- (e.g. '<tenantId>:reservation.create') so keys can never collide across tenants.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS platform_idempotency (
    scope          text        NOT NULL,
    idem_key       text        NOT NULL,
    first_seen_at  timestamptz NOT NULL DEFAULT now(),
    result_ref     text,                                 -- NULL while in flight / failed-unreleased
    PRIMARY KEY (scope, idem_key)
);
CREATE INDEX IF NOT EXISTS platform_idempotency_age_idx ON platform_idempotency (first_seen_at);

-- ---------------------------------------------------------------------------
-- Business audit: append-only. UPDATE/DELETE/TRUNCATE are rejected by trigger so
-- even a compromised application role cannot rewrite history.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS platform_audit (
    audit_id        bigserial   PRIMARY KEY,
    tenant_id       uuid        NOT NULL,
    actor           text        NOT NULL,
    action          text        NOT NULL,
    resource        text        NOT NULL,
    resource_id     text        NOT NULL DEFAULT '',
    occurred_at     timestamptz NOT NULL,
    correlation_id  text        NOT NULL DEFAULT '',
    changes         text        NOT NULL DEFAULT ''
);
CREATE INDEX IF NOT EXISTS platform_audit_resource_idx ON platform_audit (tenant_id, resource, resource_id, audit_id);

CREATE OR REPLACE FUNCTION platform_audit_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'platform_audit is append-only' USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS platform_audit_no_update ON platform_audit;
CREATE TRIGGER platform_audit_no_update BEFORE UPDATE OR DELETE ON platform_audit
    FOR EACH ROW EXECUTE FUNCTION platform_audit_immutable();
DROP TRIGGER IF EXISTS platform_audit_no_truncate ON platform_audit;
CREATE TRIGGER platform_audit_no_truncate BEFORE TRUNCATE ON platform_audit
    FOR EACH STATEMENT EXECUTE FUNCTION platform_audit_immutable();

-- Audit rows are tenant-visible: a tenant may only READ its own. INSERT is open to the app role
-- (background jobs write audit without a request tenant).
ALTER TABLE platform_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform_audit FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS platform_audit_read ON platform_audit;
CREATE POLICY platform_audit_read ON platform_audit FOR SELECT
    USING (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
DROP POLICY IF EXISTS platform_audit_insert ON platform_audit;
CREATE POLICY platform_audit_insert ON platform_audit FOR INSERT WITH CHECK (true);
