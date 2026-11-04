-- V17: transactional execution for an individually approved ownership proposal.
--
-- The executor is deliberately SECURITY INVOKER. It does not authenticate actor
-- strings: production callers must derive p_executor_id from a verified
-- operational identity and grant EXECUTE only to that trusted role. No HTTP API
-- or automatic ownership inference is introduced here.
--
-- A single record is applied per transaction. Related records must already be
-- owned by the proposed tenant; unresolved NULL or cross-tenant dependencies
-- are rejected and must be reconciled independently first.

create or replace function crm_assert_related_tenant(
    p_table_name text,
    p_record_id uuid,
    p_expected_tenant_id uuid
) returns void
language plpgsql
as $$
declare
    actual_tenant_id uuid;
    affected_rows integer;
begin
    if p_record_id is null then
        return;
    end if;

    if p_table_name not in (
        'crm_accounts', 'crm_customers', 'crm_leads',
        'crm_opportunities', 'crm_territories'
    ) then
        raise exception 'Unsupported CRM relationship table: %', p_table_name;
    end if;

    execute format(
        'select tenant_id from %I where id = $1 for key share',
        p_table_name
    ) into actual_tenant_id using p_record_id;

    get diagnostics affected_rows = row_count;
    if affected_rows <> 1 then
        raise exception 'CRM relationship target % in % does not exist',
            p_record_id, p_table_name;
    end if;

    if actual_tenant_id is distinct from p_expected_tenant_id then
        raise exception
            'CRM relationship target % in % is unassigned or belongs to another tenant',
            p_record_id, p_table_name;
    end if;
end;
$$;

create or replace function crm_apply_tenant_ownership_proposal(
    p_proposal_id uuid,
    p_executor_id text,
    p_execution_reference text
) returns text
language plpgsql
as $$
declare
    proposal crm_tenant_ownership_proposals%rowtype;
    target_table text;
    current_tenant_id uuid;
    target_exists boolean;
    affected_rows integer;
begin
    if p_executor_id is null or length(btrim(p_executor_id)) = 0 then
        raise exception 'A verified executor identity is required';
    end if;
    if p_execution_reference is null or length(btrim(p_execution_reference)) = 0 then
        raise exception 'An execution reference is required';
    end if;

    select * into proposal
      from crm_tenant_ownership_proposals
     where proposal_id = p_proposal_id
     for update;

    if not found then
        raise exception 'CRM ownership proposal % does not exist', p_proposal_id;
    end if;

    target_table := case proposal.aggregate_name
        when 'accounts' then 'crm_accounts'
        when 'customers' then 'crm_customers'
        when 'leads' then 'crm_leads'
        when 'opportunities' then 'crm_opportunities'
        when 'referrals' then 'crm_referrals'
        when 'territories' then 'crm_territories'
        when 'territory_assignments' then 'crm_territory_assignments'
        else null
    end;

    if proposal.status = 'APPLIED' then
        execute format('select tenant_id from %I where id = $1', target_table)
          into current_tenant_id using proposal.record_id;
        get diagnostics affected_rows = row_count;
        if affected_rows <> 1 or current_tenant_id is distinct from proposal.proposed_tenant_id then
            raise exception 'Applied proposal no longer matches CRM record ownership';
        end if;
        return 'ALREADY_APPLIED';
    end if;

    if proposal.status <> 'APPROVED' then
        raise exception 'Only APPROVED proposals can be applied (current status: %)',
            proposal.status;
    end if;

    execute format(
        'select tenant_id from %I where id = $1 for update',
        target_table
    ) into current_tenant_id using proposal.record_id;
    get diagnostics affected_rows = row_count;
    target_exists := affected_rows = 1;

    if not target_exists then
        raise exception 'CRM target record % in % does not exist',
            proposal.record_id, target_table;
    end if;
    if current_tenant_id is not null then
        raise exception 'CRM target record already has tenant ownership';
    end if;

    -- Validate every populated relationship explicitly. Composite FKs alone
    -- do not reject NULL tenant ownership on a referenced row.
    if proposal.aggregate_name = 'accounts' then
        perform crm_assert_related_tenant(
            'crm_accounts',
            (select parent_account_id from crm_accounts where id = proposal.record_id),
            proposal.proposed_tenant_id
        );
    elsif proposal.aggregate_name = 'opportunities' then
        perform crm_assert_related_tenant(
            'crm_customers',
            (select customer_id from crm_opportunities where id = proposal.record_id),
            proposal.proposed_tenant_id
        );
    elsif proposal.aggregate_name = 'referrals' then
        perform crm_assert_related_tenant(
            'crm_accounts',
            (select referrer_account_id from crm_referrals where id = proposal.record_id),
            proposal.proposed_tenant_id
        );
        perform crm_assert_related_tenant(
            'crm_leads',
            (select target_lead_id from crm_referrals where id = proposal.record_id),
            proposal.proposed_tenant_id
        );
        perform crm_assert_related_tenant(
            'crm_opportunities',
            (select target_opportunity_id from crm_referrals where id = proposal.record_id),
            proposal.proposed_tenant_id
        );
    elsif proposal.aggregate_name = 'territories' then
        perform crm_assert_related_tenant(
            'crm_territories',
            (select parent_territory_id from crm_territories where id = proposal.record_id),
            proposal.proposed_tenant_id
        );
    elsif proposal.aggregate_name = 'territory_assignments' then
        perform crm_assert_related_tenant(
            'crm_territories',
            (select territory_id from crm_territory_assignments where id = proposal.record_id),
            proposal.proposed_tenant_id
        );
        perform crm_assert_related_tenant(
            'crm_accounts',
            (select account_id from crm_territory_assignments where id = proposal.record_id),
            proposal.proposed_tenant_id
        );
    end if;

    execute format(
        'update %I set tenant_id = $1 where id = $2 and tenant_id is null',
        target_table
    ) using proposal.proposed_tenant_id, proposal.record_id;
    get diagnostics affected_rows = row_count;

    if affected_rows <> 1 then
        raise exception 'CRM ownership compare-and-set failed for record %',
            proposal.record_id;
    end if;

    update crm_tenant_ownership_proposals
       set status = 'APPLIED',
           applied_by = btrim(p_executor_id),
           applied_at = clock_timestamp(),
           execution_reference = btrim(p_execution_reference)
     where proposal_id = p_proposal_id
       and status = 'APPROVED';

    get diagnostics affected_rows = row_count;
    if affected_rows <> 1 then
        raise exception 'CRM ownership proposal state changed during execution';
    end if;

    return 'APPLIED';
end;
$$;

-- Do not expose an actor-asserting operation to arbitrary application roles.
-- A deployment must explicitly grant EXECUTE to its trusted operations role.
revoke all on function crm_apply_tenant_ownership_proposal(uuid, text, text) from public;
