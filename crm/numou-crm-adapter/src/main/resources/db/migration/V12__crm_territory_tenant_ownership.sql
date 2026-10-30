-- Existing territory ownership is not inferred from names or hierarchy.
-- Legacy rows remain inaccessible to tenant-scoped repositories until reconciled.
alter table crm_territories add column if not exists tenant_id uuid;

create index if not exists idx_crm_territories_tenant
    on crm_territories (tenant_id);

create index if not exists idx_crm_territories_tenant_parent
    on crm_territories (tenant_id, parent_territory_id);
