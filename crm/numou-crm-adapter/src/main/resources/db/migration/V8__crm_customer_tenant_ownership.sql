-- CRM customer tenant ownership is deliberately nullable during reconciliation.
-- Existing rows are not assigned to an arbitrary/default tenant. Because all
-- repository operations are tenant-scoped, rows with NULL tenant_id remain
-- inaccessible until a verified ownership mapping is applied.
alter table crm_customers add column if not exists tenant_id uuid;

create index if not exists idx_crm_customers_tenant_id
    on crm_customers (tenant_id);

create index if not exists idx_crm_customers_tenant_email
    on crm_customers (tenant_id, email);

create index if not exists idx_crm_customers_tenant_industry
    on crm_customers (tenant_id, industry);

create index if not exists idx_crm_customers_tenant_company
    on crm_customers (tenant_id, company_name);

-- Do not replace uq_customer_number with tenant-local uniqueness in this
-- migration: legacy duplicates and ownership must be reconciled first.
