CREATE TABLE IF NOT EXISTS identity_users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    status VARCHAR(40) NOT NULL CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DEACTIVATED')),
    version BIGINT NOT NULL DEFAULT 0
);
ALTER TABLE identity_users ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS tenants (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tenant_memberships (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_id UUID NOT NULL REFERENCES identity_users(id),
    status VARCHAR(30) NOT NULL CHECK (status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'REVOKED')),
    invited_at TIMESTAMPTZ NOT NULL,
    invitation_expires_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_membership_tenant_user UNIQUE (tenant_id, user_id),
    CONSTRAINT ck_membership_invitation_window CHECK (invitation_expires_at > invited_at)
);

CREATE TABLE IF NOT EXISTS tenant_roles (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    code VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    built_in BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_tenant_role_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_tenant_role_code UNIQUE (tenant_id, code)
);

CREATE TABLE IF NOT EXISTS identity_permissions (
    id UUID PRIMARY KEY,
    code VARCHAR(150) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS tenant_role_permissions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL REFERENCES identity_permissions(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_role_permission UNIQUE (tenant_id, role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (tenant_id, role_id)
        REFERENCES tenant_roles(tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS tenant_user_roles (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    assigned_by UUID REFERENCES identity_users(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_tenant_user_role UNIQUE (tenant_id, user_id, role_id),
    CONSTRAINT fk_user_role_membership FOREIGN KEY (tenant_id, user_id)
        REFERENCES tenant_memberships(tenant_id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role FOREIGN KEY (tenant_id, role_id)
        REFERENCES tenant_roles(tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS service_accounts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    code VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),
    revoked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_service_account_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_service_account_tenant_id UNIQUE (tenant_id, id)
);

CREATE TABLE IF NOT EXISTS service_account_credentials (
    id UUID PRIMARY KEY,
    service_account_id UUID NOT NULL REFERENCES service_accounts(id) ON DELETE CASCADE,
    secret_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS service_account_roles (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    service_account_id UUID NOT NULL,
    role_id UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_service_account_role UNIQUE (tenant_id, service_account_id, role_id),
    CONSTRAINT fk_service_role_actor FOREIGN KEY (tenant_id, service_account_id)
        REFERENCES service_accounts(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_service_role_role FOREIGN KEY (tenant_id, role_id)
        REFERENCES tenant_roles(tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS membership_invitations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_id UUID NOT NULL REFERENCES identity_users(id),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_tenant_invitation_user UNIQUE (tenant_id, user_id),
    CONSTRAINT ck_invitation_expiry CHECK (expires_at > created_at)
);

CREATE TABLE IF NOT EXISTS authorization_policies (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    permission_code VARCHAR(150) NOT NULL REFERENCES identity_permissions(code),
    effect VARCHAR(10) NOT NULL CHECK (effect IN ('ALLOW', 'DENY')),
    condition_json JSONB NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenant_policy_id UNIQUE (tenant_id, id)
);

CREATE INDEX IF NOT EXISTS ix_membership_active_user ON tenant_memberships (tenant_id, user_id, status);
CREATE INDEX IF NOT EXISTS ix_user_roles_actor ON tenant_user_roles (tenant_id, user_id);
CREATE INDEX IF NOT EXISTS ix_role_permissions_role ON tenant_role_permissions (tenant_id, role_id);
CREATE INDEX IF NOT EXISTS ix_policy_lookup ON authorization_policies (tenant_id, permission_code, active);
CREATE INDEX IF NOT EXISTS ix_service_account_active ON service_accounts (tenant_id, id, status);
CREATE INDEX IF NOT EXISTS ix_service_credentials_active ON service_account_credentials
    (service_account_id, expires_at, revoked_at);

CREATE TABLE IF NOT EXISTS identity_outbox (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    tenant_id UUID,
    aggregate_type VARCHAR(150) NOT NULL,
    aggregate_id VARCHAR(150) NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD')),
    last_error TEXT
);

CREATE INDEX IF NOT EXISTS ix_identity_outbox_pending
    ON identity_outbox (status, next_attempt_at, created_at);

CREATE TABLE IF NOT EXISTS identity_security_audit (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    actor_id VARCHAR(150) NOT NULL,
    actor_type VARCHAR(20) NOT NULL CHECK (actor_type IN ('USER', 'SERVICE', 'SYSTEM')),
    action VARCHAR(200) NOT NULL,
    resource_type VARCHAR(150) NOT NULL,
    resource_id VARCHAR(150),
    decision VARCHAR(10) NOT NULL CHECK (decision IN ('ALLOW', 'DENY')),
    reason VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL,
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS ix_security_audit_tenant_time
    ON identity_security_audit (tenant_id, occurred_at DESC);

CREATE OR REPLACE FUNCTION prevent_last_active_owner_removal()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    owner_count BIGINT;
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM tenant_roles r
          JOIN tenant_memberships m
            ON m.tenant_id = r.tenant_id AND m.user_id = OLD.user_id
         WHERE r.tenant_id = OLD.tenant_id
           AND r.id = OLD.role_id
           AND r.code = 'owner'
           AND r.built_in = TRUE
           AND m.status = 'ACTIVE'
    ) THEN
        RETURN OLD;
    END IF;

    PERFORM 1 FROM tenants WHERE id = OLD.tenant_id FOR UPDATE;

    SELECT COUNT(DISTINCT ur.user_id)
      INTO owner_count
      FROM tenant_user_roles ur
      JOIN tenant_roles r
        ON r.tenant_id = ur.tenant_id AND r.id = ur.role_id
      JOIN tenant_memberships m
        ON m.tenant_id = ur.tenant_id AND m.user_id = ur.user_id
     WHERE ur.tenant_id = OLD.tenant_id
       AND r.code = 'owner'
       AND r.built_in = TRUE
       AND m.status = 'ACTIVE';

    IF owner_count <= 1 THEN
        RAISE EXCEPTION 'Cannot remove the last active tenant owner'
            USING ERRCODE = '23514';
    END IF;
    RETURN OLD;
END;
$$;

DROP TRIGGER IF EXISTS trg_prevent_last_owner_role_removal ON tenant_user_roles;
CREATE TRIGGER trg_prevent_last_owner_role_removal
BEFORE DELETE ON tenant_user_roles
FOR EACH ROW
EXECUTE FUNCTION prevent_last_active_owner_removal();

CREATE OR REPLACE FUNCTION prevent_last_active_owner_membership_change()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    owner_count BIGINT;
BEGIN
    IF (TG_OP = 'DELETE' AND OLD.status = 'ACTIVE')
       OR (TG_OP = 'UPDATE' AND OLD.status = 'ACTIVE' AND NEW.status <> 'ACTIVE') THEN
        PERFORM 1 FROM tenants WHERE id = OLD.tenant_id FOR UPDATE;

        SELECT COUNT(DISTINCT ur.user_id)
          INTO owner_count
          FROM tenant_user_roles ur
          JOIN tenant_roles r
            ON r.tenant_id = ur.tenant_id AND r.id = ur.role_id
          JOIN tenant_memberships m
            ON m.tenant_id = ur.tenant_id AND m.user_id = ur.user_id
         WHERE ur.tenant_id = OLD.tenant_id
           AND r.code = 'owner'
           AND r.built_in = TRUE
           AND m.status = 'ACTIVE'
           AND m.user_id <> OLD.user_id;

        IF owner_count = 0 AND EXISTS (
            SELECT 1
              FROM tenant_user_roles ur
              JOIN tenant_roles r ON r.tenant_id = ur.tenant_id AND r.id = ur.role_id
             WHERE ur.tenant_id = OLD.tenant_id
               AND ur.user_id = OLD.user_id
               AND r.code = 'owner'
               AND r.built_in = TRUE
        ) THEN
            RAISE EXCEPTION 'Cannot suspend or revoke the last active tenant owner'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_prevent_last_owner_membership_change ON tenant_memberships;
CREATE TRIGGER trg_prevent_last_owner_membership_change
BEFORE UPDATE OF status OR DELETE ON tenant_memberships
FOR EACH ROW
EXECUTE FUNCTION prevent_last_active_owner_membership_change();

INSERT INTO identity_permissions (id, code) VALUES
    ('00000000-0000-4000-8000-000000000001', 'identity.role.create'),
    ('00000000-0000-4000-8000-000000000002', 'identity.role.grant'),
    ('00000000-0000-4000-8000-000000000003', 'identity.membership.manage'),
    ('00000000-0000-4000-8000-000000000004', 'document.create'),
    ('00000000-0000-4000-8000-000000000005', 'document.read'),
    ('00000000-0000-4000-8000-000000000006', 'document.publish'),
    ('00000000-0000-4000-8000-000000000007', 'document.archive')
ON CONFLICT (code) DO NOTHING;
