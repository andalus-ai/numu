-- Tenant ownership for CRM leads.
-- Existing rows are intentionally left NULL: ownership must be reconciled from
-- an authoritative source before they can be exposed to a tenant.
ALTER TABLE crm_leads
    ADD COLUMN IF NOT EXISTS tenant_id UUID;

CREATE INDEX IF NOT EXISTS idx_crm_leads_tenant_id
    ON crm_leads (tenant_id);

CREATE INDEX IF NOT EXISTS idx_crm_leads_tenant_status
    ON crm_leads (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_crm_leads_tenant_email
    ON crm_leads (tenant_id, email);

-- Lead activities are element-collection rows and inherit ownership through
-- crm_leads.lead_id. Never query or mutate them independently of that parent.
