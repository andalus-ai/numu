-- V15: enforce tenant consistency for CRM relationships at the database boundary.
--
-- Repository checks remain the first line of defense. These composite foreign
-- keys close race conditions and protect writes performed outside the
-- application. Existing rows are intentionally not rewritten or inferred:
-- NOT VALID skips historical validation but PostgreSQL enforces each
-- constraint for all new/updated rows. NULL tenant_id legacy rows remain
-- quarantined until authoritative reconciliation.
--
-- The primary keys already guarantee global uniqueness of id. These unique
-- indexes provide the referenced (tenant_id, id) keys required by PostgreSQL.

create unique index if not exists uq_crm_customers_tenant_id_id
    on crm_customers (tenant_id, id);

create unique index if not exists uq_crm_leads_tenant_id_id
    on crm_leads (tenant_id, id);

create unique index if not exists uq_crm_opportunities_tenant_id_id
    on crm_opportunities (tenant_id, id);

create unique index if not exists uq_crm_accounts_tenant_id_id
    on crm_accounts (tenant_id, id);

create unique index if not exists uq_crm_territories_tenant_id_id
    on crm_territories (tenant_id, id);

-- Account hierarchy must not cross tenant boundaries.
alter table crm_accounts
    add constraint fk_crm_accounts_parent_same_tenant
    foreign key (tenant_id, parent_account_id)
    references crm_accounts (tenant_id, id)
    not valid;

-- An opportunity may only reference a customer owned by the same tenant.
alter table crm_opportunities
    add constraint fk_crm_opportunities_customer_same_tenant
    foreign key (tenant_id, customer_id)
    references crm_customers (tenant_id, id)
    not valid;

-- Referral ownership and all optional conversion targets must agree.
alter table crm_referrals
    add constraint fk_crm_referrals_referrer_same_tenant
    foreign key (tenant_id, referrer_account_id)
    references crm_accounts (tenant_id, id)
    not valid;

alter table crm_referrals
    add constraint fk_crm_referrals_lead_same_tenant
    foreign key (tenant_id, target_lead_id)
    references crm_leads (tenant_id, id)
    not valid;

alter table crm_referrals
    add constraint fk_crm_referrals_opportunity_same_tenant
    foreign key (tenant_id, target_opportunity_id)
    references crm_opportunities (tenant_id, id)
    not valid;

-- Territory hierarchy and assignments are tenant-local as well.
alter table crm_territories
    add constraint fk_crm_territories_parent_same_tenant
    foreign key (tenant_id, parent_territory_id)
    references crm_territories (tenant_id, id)
    not valid;

alter table crm_territory_assignments
    add constraint fk_crm_assignments_territory_same_tenant
    foreign key (tenant_id, territory_id)
    references crm_territories (tenant_id, id)
    not valid;

alter table crm_territory_assignments
    add constraint fk_crm_assignments_account_same_tenant
    foreign key (tenant_id, account_id)
    references crm_accounts (tenant_id, id)
    not valid;
