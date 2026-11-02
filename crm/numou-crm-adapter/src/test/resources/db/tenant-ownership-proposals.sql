-- PostgreSQL regression assertions for V16 CRM ownership proposals.
-- Executed after applying every CRM migration. All fixture writes roll back.

begin;

insert into crm_accounts (id, tenant_id, participant_id, name, status, active, created_at)
values ('a1600000-0000-0000-0000-000000000001', null,
        'a1600000-0000-0000-0000-000000000002',
        'Legacy account under review', 'ACTIVE', true, now());

create or replace function pg_temp.assert_sqlstate(statement text, expected_state text)
returns void language plpgsql as $$
declare
    actual_state text;
begin
    begin
        execute statement;
    exception when others then
        get stacked diagnostics actual_state = returned_sqlstate;
        if actual_state = expected_state then
            return;
        end if;
        raise exception 'Expected SQLSTATE %, got % for statement: %',
            expected_state, actual_state, statement;
    end;
    raise exception 'Expected SQLSTATE % but statement succeeded: %',
        expected_state, statement;
end;
$$;

-- New proposals must include a tenant, evidence reference, digest, rationale,
-- and submitting actor. The target record remains unowned.
insert into crm_tenant_ownership_proposals (
    proposal_id, aggregate_name, record_id, proposed_tenant_id,
    evidence_type, evidence_reference, evidence_sha256, rationale, submitted_by
) values (
    'a1600000-0000-0000-0000-000000000010',
    'accounts',
    'a1600000-0000-0000-0000-000000000001',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'SIGNED_IMPORT_MANIFEST',
    'change-ticket:CRM-RECON-001',
    repeat('a', 64),
    'Source manifest explicitly assigns this account to the tenant.',
    'operator:alice'
);

select pg_temp.assert_sqlstate($q$
    insert into crm_tenant_ownership_proposals (
        proposal_id, aggregate_name, record_id, proposed_tenant_id,
        evidence_type, evidence_reference, evidence_sha256, rationale, submitted_by
    ) values (
        'a1600000-0000-0000-0000-000000000011',
        'accounts',
        'a1600000-0000-0000-0000-000000000001',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        'IMPORT',
        'ticket:duplicate',
        repeat('b', 64),
        'Duplicate live proposal.',
        'operator:bob'
    )
$q$, '23505');

-- Approval requires a different reviewer and a review rationale.
select pg_temp.assert_sqlstate($q$
    update crm_tenant_ownership_proposals
       set status = 'APPROVED',
           reviewed_by = 'operator:alice',
           reviewed_at = now(),
           review_note = 'Self-approval must fail.'
     where proposal_id = 'a1600000-0000-0000-0000-000000000010'
$q$, '23514');

update crm_tenant_ownership_proposals
   set status = 'APPROVED',
       reviewed_by = 'reviewer:bob',
       reviewed_at = now(),
       review_note = 'Evidence checked against signed source manifest.'
 where proposal_id = 'a1600000-0000-0000-0000-000000000010';

select pg_temp.assert_sqlstate($q$
    update crm_tenant_ownership_proposals
       set evidence_reference = 'ticket:tampered'
     where proposal_id = 'a1600000-0000-0000-0000-000000000010'
$q$, 'P0001');

select pg_temp.assert_sqlstate($q$
    update crm_tenant_ownership_proposals
       set status = 'PENDING'
     where proposal_id = 'a1600000-0000-0000-0000-000000000010'
$q$, 'P0001');

-- Approval metadata cannot be replaced while marking execution complete.
select pg_temp.assert_sqlstate($q$
    update crm_tenant_ownership_proposals
       set status = 'APPLIED',
           reviewed_by = 'reviewer:mallory',
           applied_by = 'operator:carol',
           applied_at = now(),
           execution_reference = 'change-ticket:tampered'
     where proposal_id = 'a1600000-0000-0000-0000-000000000010'
$q$, 'P0001');

-- Only an approved proposal can be marked applied; the executor and durable
-- operational reference are captured. This records completion only; it does
-- not mutate the CRM account's tenant_id.
update crm_tenant_ownership_proposals
   set status = 'APPLIED',
       applied_by = 'operator:carol',
       applied_at = now(),
       execution_reference = 'change-ticket:CRM-RECON-001/deployment-42'
 where proposal_id = 'a1600000-0000-0000-0000-000000000010';

do $$
begin
    if (select count(*) from crm_tenant_ownership_proposal_audit
         where proposal_id = 'a1600000-0000-0000-0000-000000000010') <> 3 then
        raise exception 'Expected SUBMITTED, APPROVED, and APPLIED audit events';
    end if;

    if (select tenant_id from crm_accounts
         where id = 'a1600000-0000-0000-0000-000000000001') is not null then
        raise exception 'Proposal workflow unexpectedly changed CRM ownership';
    end if;

    if (select count(*) from crm_tenant_ownership_proposal_audit
         where proposal_id = 'a1600000-0000-0000-0000-000000000010'
           and event_type in ('SUBMITTED', 'APPROVED', 'APPLIED')) <> 3 then
        raise exception 'Missing expected proposal audit event';
    end if;
end;
$$;

select pg_temp.assert_sqlstate($q$
    delete from crm_tenant_ownership_proposal_audit
     where proposal_id = 'a1600000-0000-0000-0000-000000000010'
$q$, 'P0001');

rollback;
