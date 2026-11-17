-- M9: prove the approved ownership executor and its relationship helper
-- work under a non-owner role with only the required table/function grants.
begin;

drop role if exists crm_ownership_operator_m9_test;
create role crm_ownership_operator_m9_test nologin;

do $$
declare
    tenant_a constant uuid := 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
    parent_id constant uuid := '81000000-0000-4000-8000-000000000001';
    child_id constant uuid := '81000000-0000-4000-8000-000000000002';
    v_proposal_id constant uuid := '91000000-0000-4000-8000-000000000001';
begin
    insert into crm_accounts(id, participant_id, parent_account_id, name, status, created_at, tenant_id)
    values
      (parent_id, gen_random_uuid(), null, 'M9 existing parent', 'ACTIVE', now(), tenant_a),
      (child_id, gen_random_uuid(), parent_id, 'M9 unassigned child', 'ACTIVE', now(), null);

    insert into crm_tenant_ownership_proposals(
      proposal_id, aggregate_name, record_id, proposed_tenant_id,
      evidence_type, evidence_reference, evidence_sha256, rationale, submitted_by
    ) values (
      v_proposal_id, 'accounts', child_id, tenant_a,
      'CONTRACT', 'm9/least-privilege',
      'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      'Verify SECURITY INVOKER execution under a restricted operator role',
      'm9-submitter'
    );

    update crm_tenant_ownership_proposals
       set status = 'APPROVED',
           reviewed_by = 'm9-reviewer',
           reviewed_at = now(),
           review_note = 'M9 test evidence reviewed'
     where crm_tenant_ownership_proposals.proposal_id = v_proposal_id;
end;
$$;

-- Only the executor's proposal/audit and account relationship operations.
grant execute on function crm_assert_related_tenant(text, uuid, uuid)
    to crm_ownership_operator_m9_test;
grant execute on function crm_apply_tenant_ownership_proposal(uuid, text, text)
    to crm_ownership_operator_m9_test;
grant select, update on crm_tenant_ownership_proposals
    to crm_ownership_operator_m9_test;
grant select, insert on crm_tenant_ownership_proposal_audit
    to crm_ownership_operator_m9_test;
grant select, update on crm_accounts
    to crm_ownership_operator_m9_test;

set local role crm_ownership_operator_m9_test;

do $$
declare
    result text;
    actual_tenant_id uuid;
    actual_status text;
begin
    result := crm_apply_tenant_ownership_proposal(
      '91000000-0000-4000-8000-000000000001',
      'verified:m9-test-operator',
      'm9/least-privilege-execution'
    );

    if result <> 'APPLIED' then
        raise exception 'Expected APPLIED under least-privilege role, got %', result;
    end if;

    select tenant_id into actual_tenant_id
      from crm_accounts
     where id = '81000000-0000-4000-8000-000000000002';

    if actual_tenant_id is distinct from 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'::uuid then
        raise exception 'Least-privilege execution did not update target tenant ownership';
    end if;

    select status into actual_status
      from crm_tenant_ownership_proposals
     where proposal_id = '91000000-0000-4000-8000-000000000001';

    if actual_status <> 'APPLIED' then
        raise exception 'Least-privilege execution did not transition proposal to APPLIED';
    end if;

    if not exists (
        select 1
          from crm_tenant_ownership_proposal_audit
         where proposal_id = '91000000-0000-4000-8000-000000000001'
           and event_type = 'APPLIED'
           and actor_id = 'verified:m9-test-operator'
    ) then
        raise exception 'Least-privilege execution did not write its APPLIED audit event';
    end if;
end;
$$;

reset role;

revoke all on function crm_assert_related_tenant(text, uuid, uuid)
    from crm_ownership_operator_m9_test;
revoke all on function crm_apply_tenant_ownership_proposal(uuid, text, text)
    from crm_ownership_operator_m9_test;
revoke all privileges on all tables in schema public
    from crm_ownership_operator_m9_test;
drop role crm_ownership_operator_m9_test;

rollback;
