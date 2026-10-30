-- Account ownership is nullable while legacy participant/account ownership is reconciled.
-- Never infer tenant ownership from participant_id or assign legacy rows to a default tenant.
alter table crm_accounts add column if not exists tenant_id uuid;

create index if not exists idx_crm_accounts_tenant
    on crm_accounts (tenant_id);

create index if not exists idx_crm_accounts_tenant_parent
    on crm_accounts (tenant_id, parent_account_id);

create index if not exists idx_crm_accounts_tenant_participant
    on crm_accounts (tenant_id, participant_id);

-- Parent references are enforced in the repository against the verified tenant.
-- Existing FK remains for referential integrity; legacy NULL-tenant rows remain quarantined.
