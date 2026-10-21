-- V2: Personal access tokens, identity provider configs, and hierarchical scoped role assignments

CREATE TABLE IF NOT EXISTS personal_access_token (
    id             UUID PRIMARY KEY,
    principal_type VARCHAR(32)  NOT NULL,
    principal_id   UUID         NOT NULL,
    label          VARCHAR(255) NOT NULL,
    token_hash     VARCHAR(128) NOT NULL UNIQUE,
    display_prefix VARCHAR(16)  NOT NULL,
    status         VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ,
    last_used_at   TIMESTAMPTZ,
    revoked_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_pat_principal ON personal_access_token (principal_type, principal_id);

CREATE TABLE IF NOT EXISTS identity_provider_config (
    id                     UUID PRIMARY KEY,
    name                   VARCHAR(255) NOT NULL UNIQUE,
    type                   VARCHAR(32)  NOT NULL,
    connection_config_json JSONB        NOT NULL DEFAULT '{}'::jsonb,
    enabled                BOOLEAN      NOT NULL DEFAULT TRUE,
    scim_endpoint          VARCHAR(1024),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS role_assignment (
    id                 UUID PRIMARY KEY,
    principal_type     VARCHAR(32)   NOT NULL,
    principal_id       UUID          NOT NULL,
    role_id            UUID          NOT NULL REFERENCES tenant_roles (id) ON DELETE CASCADE,
    scope_type         VARCHAR(32)   NOT NULL,
    scope_path         VARCHAR(1024) NOT NULL,
    granted_by_user_id UUID,
    granted_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    expires_at         TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_role_assignment_principal_scope
    ON role_assignment (principal_type, principal_id, scope_path);

-- Allow system/non-multitenant service accounts as well as multitenant
ALTER TABLE service_accounts ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE service_accounts ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE service_accounts ADD COLUMN IF NOT EXISTS owner_user_id UUID REFERENCES identity_users(id);
ALTER TABLE service_accounts ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT now();

INSERT INTO tenants (id, name, status) VALUES
    ('00000000-0000-0000-0000-000000000000', 'System', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO identity_permissions (id, code) VALUES
    ('00000000-0000-4000-8000-000000000010', 'identity.user.manage'),
    ('00000000-0000-4000-8000-000000000011', 'identity.role.manage'),
    ('00000000-0000-4000-8000-000000000012', 'identity.role-assignment.manage'),
    ('00000000-0000-4000-8000-000000000013', 'identity.pat.manage'),
    ('00000000-0000-4000-8000-000000000014', 'identity.service-account.manage'),
    ('00000000-0000-4000-8000-000000000015', 'identity.service-account.read'),
    ('00000000-0000-4000-8000-000000000016', 'identity.idp.manage')
ON CONFLICT (code) DO NOTHING;
