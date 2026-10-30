-- CRM opportunity tenant ownership is nullable during controlled reconciliation.
-- Existing rows are not assigned to an inferred or default tenant.
alter table crm_opportunities add column if not exists tenant_id uuid;

create index if not exists idx_crm_opportunities_tenant_id
    on crm_opportunities (tenant_id);

create index if not exists idx_crm_opportunities_tenant_customer
    on crm_opportunities (tenant_id, customer_id);

create index if not exists idx_crm_opportunities_tenant_stage
    on crm_opportunities (tenant_id, stage);

-- crm_opportunity_activities is an element collection. Its ownership is
-- inherited through crm_opportunities and it must only be accessed through
-- a tenant-scoped parent opportunity.
