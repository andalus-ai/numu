-- Assignment ownership is explicit. It is not inferred from account or territory
-- rows because legacy relationships may be inconsistent and must be reconciled.
alter table crm_territory_assignments add column if not exists tenant_id uuid;

create index if not exists idx_crm_territory_assignments_tenant
    on crm_territory_assignments (tenant_id);

create index if not exists idx_crm_territory_assignments_tenant_territory
    on crm_territory_assignments (tenant_id, territory_id);

create index if not exists idx_crm_territory_assignments_tenant_account
    on crm_territory_assignments (tenant_id, account_id);
