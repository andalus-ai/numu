-- V4: SAML replay protection table + ABAC authorization policy table
--
-- SAML replay protection (distributed, replaces in-process ConcurrentHashMap)
-- -----------------------------------------------------------------------------
-- Each consumed assertion ID is stored until its NotOnOrAfter window expires.
-- INSERT … ON CONFLICT DO NOTHING in PostgresSamlReplayStore makes the
-- first-write-wins semantic atomic without advisory locks.

CREATE TABLE IF NOT EXISTS identity_saml_replay (
    assertion_id  TEXT        NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    consumed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_saml_replay PRIMARY KEY (assertion_id)
);

-- Purge expired entries efficiently (PostgresSamlReplayStore.purgeExpired()).
CREATE INDEX IF NOT EXISTS idx_saml_replay_expires
    ON identity_saml_replay (expires_at);

-- ABAC authorization policy table
-- -----------------------------------------------------------------------------
-- Policies are tenant-scoped, per-permission lists evaluated after RBAC grants.
-- Rows are never hard-deleted; set active = false to deactivate a policy.

CREATE TABLE IF NOT EXISTS identity_authorization_policy (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    permission_code TEXT        NOT NULL,
    effect          TEXT        NOT NULL CHECK (effect IN ('ALLOW', 'DENY')),
    condition_json  JSONB       NOT NULL,
    active          BOOLEAN     NOT NULL DEFAULT true,
    version         BIGINT      NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_auth_policy PRIMARY KEY (id)
);

-- Hot-path index: findActive(tenantId, permission) used on every authorization check.
CREATE INDEX IF NOT EXISTS idx_auth_policy_tenant_perm_active
    ON identity_authorization_policy (tenant_id, permission_code)
    WHERE active = true;

-- Concurrency safety: enforce monotonic version increments.
CREATE OR REPLACE FUNCTION identity_policy_bump_version()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.version <= OLD.version THEN
        RAISE EXCEPTION 'identity_authorization_policy version must increase (old=%, new=%)',
            OLD.version, NEW.version;
    END IF;
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_auth_policy_version ON identity_authorization_policy;
CREATE TRIGGER trg_auth_policy_version
    BEFORE UPDATE ON identity_authorization_policy
    FOR EACH ROW EXECUTE FUNCTION identity_policy_bump_version();
