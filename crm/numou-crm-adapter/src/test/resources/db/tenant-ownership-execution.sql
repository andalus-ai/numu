-- M7 transactional ownership execution regression checks.
begin;

do $$
declare
    tenant_a constant uuid := 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
    tenant_b constant uuid := 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
    parent_account constant uuid := '10000000-0000-4000-8000-000000000001';
    account_id constant uuid := '10000000-0000-4000-8000-000000000002';
    customer_id constant uuid := '20000000-0000-4000-8000-000000000001';
    lead_id constant uuid := '30000000-0000-4000-8000-000000000001';
    opportunity_id constant uuid := '40000000-0000-4000-8000-000000000001';
    referral_id constant uuid := '50000000-0000-4000-8000-000000000001';
    parent_territory constant uuid := '60000000-0000-4000-8000-000000000001';
    territory_id constant uuid := '60000000-0000-4000-8000-000000000002';
    assignment_id constant uuid := '70000000-0000-4000-8000-000000000001';
    null_customer constant uuid := '20000000-0000-4000-8000-000000000002';
    blocked_opportunity constant uuid := '40000000-0000-4000-8000-000000000002';
    p uuid;
    result text;
    audit_count integer;
begin
    insert into crm_accounts(id, participant_id, name, status, created_at, tenant_id)
    values
      (parent_account, gen_random_uuid(), 'Existing parent', 'ACTIVE', now(), tenant_a),
      (account_id, gen_random_uuid(), 'Legacy child', 'ACTIVE', now(), null);

    insert into crm_customers(id, created_at, tenant_id)
    values (customer_id, now(), tenant_a), (null_customer, now(), null);

    insert into crm_leads(id, first_name, last_name, status, created_at)
    values (lead_id, 'Legacy', 'Lead', 'NEW', now());

    insert into crm_opportunities(id, name, stage, created_at, customer_id)
    values
      (opportunity_id, 'Legacy opportunity', 'QUALIFICATION', now(), customer_id),
      (blocked_opportunity, 'Blocked opportunity', 'QUALIFICATION', now(), null_customer);

    insert into crm_referrals(id, referrer_account_id, target, type, status, code, created_at)
    values (referral_id, parent_account, 'LEAD', 'CUSTOMER', 'PENDING', 'M7-REF', now());

    insert into crm_territories(id, name, parent_territory_id, created_at, tenant_id)
    values (parent_territory, 'Existing parent territory', null, now(), tenant_a),
           (territory_id, 'Legacy child territory', parent_territory, now(), null);

    insert into crm_territory_assignments(
      id, territory_id, account_id, origin, status, assigned_by_user_id, assigned_at, created_at
    ) values (assignment_id, territory_id, parent_account, 'MANUAL', 'ACTIVE',
              gen_random_uuid(), now(), now());

    -- Assignment ownership is unassigned; its two related records must already
    -- be owned by the proposed tenant before it can be applied.
    update crm_territory_assignments set tenant_id = null where id = assignment_id;

    -- The seven supported aggregates each receive a distinct approved proposal.
    insert into crm_tenant_ownership_proposals(
      proposal_id, aggregate_name, record_id, proposed_tenant_id,
      evidence_type, evidence_reference, evidence_sha256, rationale, submitted_by
    ) values
      ('90000000-0000-4000-8000-000000000001','accounts',account_id,tenant_a,'CONTRACT','m7/account','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa','Verified account ownership','submitter'),
      ('90000000-0000-4000-8000-000000000002','customers',customer_id,tenant_a,'CONTRACT','m7/customer','bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb','Verified customer ownership','submitter'),
      ('90000000-0000-4000-8000-000000000003','leads',lead_id,tenant_a,'CONTRACT','m7/lead','cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc','Verified lead ownership','submitter'),
      ('90000000-0000-4000-8000-000000000004','opportunities',opportunity_id,tenant_a,'CONTRACT','m7/opportunity','dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd','Verified opportunity ownership','submitter'),
      ('90000000-0000-4000-8000-000000000005','referrals',referral_id,tenant_a,'CONTRACT','m7/referral','eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee','Verified referral ownership','submitter'),
      ('90000000-0000-4000-8000-000000000006','territories',territory_id,tenant_a,'CONTRACT','m7/territory','ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff','Verified territory ownership','submitter'),
      ('90000000-0000-4000-8000-000000000007','territory_assignments',assignment_id,tenant_a,'CONTRACT','m7/assignment','1111111111111111111111111111111111111111111111111111111111111111','Verified assignment ownership','submitter');

    update crm_tenant_ownership_proposals
       set status='APPROVED', reviewed_by='reviewer', reviewed_at=now(), review_note='Evidence reviewed'
     where proposal_id in (
       '90000000-0000-4000-8000-000000000001','90000000-0000-4000-8000-000000000002',
       '90000000-0000-4000-8000-000000000003','90000000-0000-4000-8000-000000000004',
       '90000000-0000-4000-8000-000000000005','90000000-0000-4000-8000-000000000006',
       '90000000-0000-4000-8000-000000000007'
     );

    for p in
      select proposal_id from crm_tenant_ownership_proposals
       where proposal_id between '90000000-0000-4000-8000-000000000001'::uuid
                             and '90000000-0000-4000-8000-000000000007'::uuid
       order by proposal_id
    loop
      result := crm_apply_tenant_ownership_proposal(p, 'operator', 'm7-test/' || p::text);
      if result <> 'APPLIED' then
        raise exception 'Expected APPLIED for %, got %', p, result;
      end if;
    end loop;

    result := crm_apply_tenant_ownership_proposal(
      '90000000-0000-4000-8000-000000000001', 'operator', 'm7-test/replay'
    );
    if result <> 'ALREADY_APPLIED' then
      raise exception 'Expected idempotent replay, got %', result;
    end if;

    -- A populated relationship pointing to a NULL-owned row is not accepted,
    -- even though PostgreSQL composite FKs permit NULL-key bypass semantics.
    insert into crm_tenant_ownership_proposals(
      proposal_id, aggregate_name, record_id, proposed_tenant_id,
      evidence_type, evidence_reference, evidence_sha256, rationale, submitted_by
    ) values (
      '90000000-0000-4000-8000-000000000008','opportunities',blocked_opportunity,tenant_a,
      'CONTRACT','m7/blocked','2222222222222222222222222222222222222222222222222222222222222222',
      'Must reject unresolved customer ownership','submitter'
    );
    update crm_tenant_ownership_proposals
       set status='APPROVED', reviewed_by='reviewer', reviewed_at=now(), review_note='Evidence reviewed'
     where proposal_id='90000000-0000-4000-8000-000000000008';

    begin
      perform crm_apply_tenant_ownership_proposal(
        '90000000-0000-4000-8000-000000000008', 'operator', 'm7-test/blocked'
      );
      raise exception 'Expected unresolved relationship rejection';
    exception when raise_exception then
      if sqlerrm = 'Expected unresolved relationship rejection' then
        raise;
      end if;
      if sqlerrm not like '%unassigned or belongs to another tenant%' then
        raise;
      end if;
    end;

    select count(*) into audit_count
      from crm_tenant_ownership_proposal_audit
     where proposal_id between '90000000-0000-4000-8000-000000000001'::uuid
                           and '90000000-0000-4000-8000-000000000007'::uuid
       and event_type = 'APPLIED';
    if audit_count <> 7 then
      raise exception 'Expected seven APPLIED audit events, got %', audit_count;
    end if;
end;
$$;

rollback;
