-- CRM lead tenant ownership is deliberately nullable during reconciliation.
-- Existing rows are not assigned to an arbitrary/default tenant. Tenant-scoped
-- repository operations make NULL-owned rows inaccessible until ownership is
-- established from an authoritative source.
alter table crm_leads add column if not exists tenant_id uuid;

create index if not exists idx_crm_leads_tenant_id
    on crm_leads (tenant_id);

create index if not exists idx_crm_leads_tenant_status
    on crm_leads (tenant_id, status);

create index if not exists idx_crm_leads_tenant_email
    on crm_leads (tenant_id, email);

-- crm_lead_activities is an element collection and inherits ownership through
-- crm_leads. Access it only through its tenant-scoped parent lead.
