-- Service-account operations use explicit tenant-scoped verbs in the Security permission model.
-- Preserve existing grants by expanding the legacy manage permission to create/update.
INSERT INTO identity_permissions (id, code) VALUES
    ('00000000-0000-4000-8000-000000000017', 'identity.service-account.create'),
    ('00000000-0000-4000-8000-000000000018', 'identity.service-account.update')
ON CONFLICT (code) DO NOTHING;

INSERT INTO tenant_role_permissions (id, tenant_id, role_id, permission_id)
SELECT gen_random_uuid(), grants.tenant_id, grants.role_id, target_permission.id
  FROM tenant_role_permissions grants
  JOIN identity_permissions legacy_permission
    ON legacy_permission.id = grants.permission_id
 CROSS JOIN identity_permissions target_permission
 WHERE legacy_permission.code = 'identity.service-account.manage'
   AND target_permission.code IN (
       'identity.service-account.create',
       'identity.service-account.update'
   )
ON CONFLICT (tenant_id, role_id, permission_id) DO NOTHING;
