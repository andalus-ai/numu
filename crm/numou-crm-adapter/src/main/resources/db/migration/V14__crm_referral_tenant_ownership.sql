-- Referral ownership is tenant-scoped. Legacy rows intentionally remain
-- unassigned until an independently verified ownership reconciliation exists.
-- Never infer ownership from referrer_account_id or target references.
alter table crm_referrals
    add column if not exists tenant_id uuid;

create index if not exists idx_crm_referrals_tenant
    on crm_referrals (tenant_id);

create index if not exists idx_crm_referrals_tenant_referrer
    on crm_referrals (tenant_id, referrer_account_id);

create index if not exists idx_crm_referrals_tenant_status
    on crm_referrals (tenant_id, status);

create index if not exists idx_crm_referrals_tenant_code
    on crm_referrals (tenant_id, code);

-- Existing global code uniqueness, if present, is intentionally preserved
-- until legacy data is reconciled and tenant-local uniqueness is designed.
