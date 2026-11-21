-- M16c: receipt-posting operator permissions.
-- These permissions are deliberately catalogued but not auto-granted to every tenant role.
INSERT INTO identity_permissions (id, code) VALUES
    ('00000000-0000-4000-8000-000000000019', 'automotive-parts.receipt-posting.inspect'),
    ('00000000-0000-4000-8000-000000000020', 'automotive-parts.receipt-posting.requeue')
ON CONFLICT (code) DO NOTHING;

COMMENT ON TABLE identity_permissions IS
    'Global permission catalog. Tenant-specific grants are maintained through tenant_role_permissions.';
