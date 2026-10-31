-- PostgreSQL integration assertions for V15 tenant-local CRM relationships.
-- Run after applying all crm/numou-crm-adapter/src/main/resources/db/migration/V*.sql.
-- This file is executed by psql with ON_ERROR_STOP=1.

begin;

insert into crm_accounts (id, tenant_id, participant_id, name, status, active, created_at)
values
 ('10000000-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '20000000-0000-0000-0000-000000000001', 'Tenant A root', 'ACTIVE', true, now()),
 ('10000000-0000-0000-0000-000000000002', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '20000000-0000-0000-0000-000000000002', 'Tenant B root', 'ACTIVE', true, now()),
 ('10000000-0000-0000-0000-000000000003', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '20000000-0000-0000-0000-000000000003', 'Tenant A child', 'ACTIVE', true, now());

insert into crm_customers (id, tenant_id, company_name, active, created_at)
values
 ('30000000-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Tenant A customer', true, now()),
 ('30000000-0000-0000-0000-000000000002', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'Tenant B customer', true, now());

insert into crm_leads (id, tenant_id, first_name, last_name, status, active, created_at)
values
 ('40000000-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Lead', 'A', 'NEW', true, now()),
 ('40000000-0000-0000-0000-000000000002', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'Lead', 'B', 'NEW', true, now());

insert into crm_opportunities (id, tenant_id, name, customer_id, stage, active, created_at)
values
 ('50000000-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Tenant A opportunity', '30000000-0000-0000-0000-000000000001', 'QUALIFICATION', true, now()),
 ('50000000-0000-0000-0000-000000000002', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'Tenant B opportunity', '30000000-0000-0000-0000-000000000002', 'QUALIFICATION', true, now());

insert into crm_territories (id, tenant_id, name, territory_active, active, created_at)
values
 ('60000000-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Tenant A territory', true, true, now()),
 ('60000000-0000-0000-0000-000000000002', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'Tenant B territory', true, true, now()),
 ('60000000-0000-0000-0000-000000000003', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Tenant A child territory', true, true, now());

insert into crm_territory_assignments
 (id, tenant_id, territory_id, account_id, origin, status, assigned_by_user_id, assigned_at, active, created_at)
values
 ('70000000-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  '60000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001',
  'MANUAL', 'ACTIVE', '80000000-0000-0000-0000-000000000001', now(), true, now());

-- Positive cases: same-tenant parent and conversion references are accepted.
update crm_accounts set parent_account_id = '10000000-0000-0000-0000-000000000001'
where id = '10000000-0000-0000-0000-000000000003';
update crm_territories set parent_territory_id = '60000000-0000-0000-0000-000000000001'
where id = '60000000-0000-0000-0000-000000000003';

-- The helper succeeds only when the supplied statement is rejected by a
-- PostgreSQL foreign-key violation. Unexpected success fails the test.
create or replace function pg_temp.assert_fk_rejected(statement text)
returns void language plpgsql as $$
begin
    execute statement;
    raise exception 'Expected foreign-key rejection, but statement succeeded: %', statement;
exception
    when foreign_key_violation then
        null;
end;
$$;

select pg_temp.assert_fk_rejected($q$
 insert into crm_accounts (id, tenant_id, participant_id, parent_account_id, name, status, active, created_at)
 values ('10000000-0000-0000-0000-000000000004',
         'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         '20000000-0000-0000-0000-000000000004',
         '10000000-0000-0000-0000-000000000002',
         'Cross-tenant child', 'ACTIVE', true, now())
$q$);

select pg_temp.assert_fk_rejected($q$
 insert into crm_opportunities (id, tenant_id, name, customer_id, stage, active, created_at)
 values ('50000000-0000-0000-0000-000000000002',
         'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Cross-tenant opportunity',
         '30000000-0000-0000-0000-000000000002', 'QUALIFICATION', true, now())
$q$);

select pg_temp.assert_fk_rejected($q$
 insert into crm_referrals (id, tenant_id, referrer_account_id, target, type, status, code, active, created_at)
 values ('90000000-0000-0000-0000-000000000001',
         'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         '10000000-0000-0000-0000-000000000002',
         'LEAD', 'CUSTOMER', 'PENDING', 'REF-A-1', true, now())
$q$);

insert into crm_referrals (id, tenant_id, referrer_account_id, target, type, status, code, active, created_at)
values ('90000000-0000-0000-0000-000000000002',
        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
        '10000000-0000-0000-0000-000000000001',
        'LEAD', 'CUSTOMER', 'PENDING', 'REF-A-2', true, now());

update crm_referrals set target_lead_id = '40000000-0000-0000-0000-000000000001'
where id = '90000000-0000-0000-0000-000000000002';
update crm_referrals set target_lead_id = null
where id = '90000000-0000-0000-0000-000000000002';
update crm_referrals set target_opportunity_id = '50000000-0000-0000-0000-000000000001'
where id = '90000000-0000-0000-0000-000000000002';
update crm_referrals set target_opportunity_id = null
where id = '90000000-0000-0000-0000-000000000002';

select pg_temp.assert_fk_rejected($q$
 update crm_referrals set target_lead_id = '40000000-0000-0000-0000-000000000002'
 where id = '90000000-0000-0000-0000-000000000002'
$q$);

select pg_temp.assert_fk_rejected($q$
 update crm_referrals set target_opportunity_id = '50000000-0000-0000-0000-000000000002'
 where id = '90000000-0000-0000-0000-000000000002'
$q$);

select pg_temp.assert_fk_rejected($q$
 insert into crm_territories (id, tenant_id, name, parent_territory_id, territory_active, active, created_at)
 values ('60000000-0000-0000-0000-000000000004',
         'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Cross-tenant territory',
         '60000000-0000-0000-0000-000000000002', true, true, now())
$q$);

select pg_temp.assert_fk_rejected($q$
 insert into crm_territory_assignments
 (id, tenant_id, territory_id, account_id, origin, status, assigned_by_user_id, assigned_at, active, created_at)
 values ('70000000-0000-0000-0000-000000000002',
         'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         '60000000-0000-0000-0000-000000000002',
         '10000000-0000-0000-0000-000000000001',
         'MANUAL', 'ACTIVE', '80000000-0000-0000-0000-000000000002', now(), true, now())
$q$);

select pg_temp.assert_fk_rejected($q$
 insert into crm_territory_assignments
 (id, tenant_id, territory_id, account_id, origin, status, assigned_by_user_id, assigned_at, active, created_at)
 values ('70000000-0000-0000-0000-000000000003',
         'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         '60000000-0000-0000-0000-000000000001',
         '10000000-0000-0000-0000-000000000002',
         'MANUAL', 'ACTIVE', '80000000-0000-0000-0000-000000000003', now(), true, now())
$q$);

-- Historical rows with NULL tenant ownership are not assigned a tenant by V15.
insert into crm_accounts (id, tenant_id, participant_id, name, status, active, created_at)
values ('10000000-0000-0000-0000-000000000005', null,
        '20000000-0000-0000-0000-000000000005', 'Legacy unowned account', 'ACTIVE', true, now());

do $$
begin
    if (select tenant_id from crm_accounts where id = '10000000-0000-0000-0000-000000000005') is not null then
        raise exception 'V15 unexpectedly assigned tenant ownership to a legacy row';
    end if;
    if (select count(*) from crm_territory_assignments where id = '70000000-0000-0000-0000-000000000001') <> 1 then
        raise exception 'Valid same-tenant relationship was not preserved';
    end if;
end;
$$;

rollback;
