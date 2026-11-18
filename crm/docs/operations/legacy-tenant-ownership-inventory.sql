-- Read-only CRM legacy tenant ownership inventory.
--
-- Run against a database after CRM migrations V8-V15. This script does not
-- update data, infer ownership, validate constraints, or change migration
-- state. It emits:
--   1. Per-aggregate total / owned / unowned counts.
--   2. Existing relationships whose source and target ownership differ or
--      where either side is still unassigned.
--
-- Treat NULL ownership as unresolved. Do not backfill from relationship
-- targets, participant IDs, UUID patterns, names, or other indirect clues.
-- A remediation mapping must come from an independently authoritative source
-- and receive explicit review before any UPDATE is executed.

-- Result set 1: ownership coverage by aggregate.
select aggregate_name,
       total_rows,
       owned_rows,
       unowned_rows
from (
    select 'accounts'::text as aggregate_name,
           count(*)::bigint as total_rows,
           count(*) filter (where tenant_id is not null)::bigint as owned_rows,
           count(*) filter (where tenant_id is null)::bigint as unowned_rows
      from crm_accounts
    union all
    select 'customers',
           count(*)::bigint,
           count(*) filter (where tenant_id is not null)::bigint,
           count(*) filter (where tenant_id is null)::bigint
      from crm_customers
    union all
    select 'leads',
           count(*)::bigint,
           count(*) filter (where tenant_id is not null)::bigint,
           count(*) filter (where tenant_id is null)::bigint
      from crm_leads
    union all
    select 'opportunities',
           count(*)::bigint,
           count(*) filter (where tenant_id is not null)::bigint,
           count(*) filter (where tenant_id is null)::bigint
      from crm_opportunities
    union all
    select 'referrals',
           count(*)::bigint,
           count(*) filter (where tenant_id is not null)::bigint,
           count(*) filter (where tenant_id is null)::bigint
      from crm_referrals
    union all
    select 'territories',
           count(*)::bigint,
           count(*) filter (where tenant_id is not null)::bigint,
           count(*) filter (where tenant_id is null)::bigint
      from crm_territories
    union all
    select 'territory_assignments',
           count(*)::bigint,
           count(*) filter (where tenant_id is not null)::bigint,
           count(*) filter (where tenant_id is null)::bigint
      from crm_territory_assignments
) ownership
order by aggregate_name;

-- Result set 2: relationship rows requiring manual investigation.
-- A missing related row is not expected because the legacy global foreign
-- keys remain in place; this report focuses on ownership mismatches.
with relationship_rows as (
    select 'account.parent_account'::text as relationship_name,
           source.id as source_id,
           source.tenant_id as source_tenant_id,
           target.id as target_id,
           target.tenant_id as target_tenant_id
      from crm_accounts source
      join crm_accounts target on target.id = source.parent_account_id
    union all
    select 'opportunity.customer',
           source.id, source.tenant_id, target.id, target.tenant_id
      from crm_opportunities source
      join crm_customers target on target.id = source.customer_id
    union all
    select 'referral.referrer_account',
           source.id, source.tenant_id, target.id, target.tenant_id
      from crm_referrals source
      join crm_accounts target on target.id = source.referrer_account_id
    union all
    select 'referral.target_lead',
           source.id, source.tenant_id, target.id, target.tenant_id
      from crm_referrals source
      join crm_leads target on target.id = source.target_lead_id
    union all
    select 'referral.target_opportunity',
           source.id, source.tenant_id, target.id, target.tenant_id
      from crm_referrals source
      join crm_opportunities target on target.id = source.target_opportunity_id
    union all
    select 'territory.parent_territory',
           source.id, source.tenant_id, target.id, target.tenant_id
      from crm_territories source
      join crm_territories target on target.id = source.parent_territory_id
    union all
    select 'territory_assignment.territory',
           source.id, source.tenant_id, target.id, target.tenant_id
      from crm_territory_assignments source
      join crm_territories target on target.id = source.territory_id
    union all
    select 'territory_assignment.account',
           source.id, source.tenant_id, target.id, target.tenant_id
      from crm_territory_assignments source
      join crm_accounts target on target.id = source.account_id
)
select relationship_name,
       source_id,
       source_tenant_id,
       target_id,
       target_tenant_id,
       case
           when source_tenant_id is null and target_tenant_id is null
               then 'BOTH_UNASSIGNED'
           when source_tenant_id is null
               then 'SOURCE_UNASSIGNED'
           when target_tenant_id is null
               then 'TARGET_UNASSIGNED'
           else 'CROSS_TENANT'
       end as issue
  from relationship_rows
 where source_tenant_id is null
    or target_tenant_id is null
    or source_tenant_id <> target_tenant_id
 order by relationship_name, source_id, target_id;
