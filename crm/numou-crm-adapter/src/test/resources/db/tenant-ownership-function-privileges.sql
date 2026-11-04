-- M8 function execution privilege regression checks.
begin;

-- The deployment role names are intentionally not created by migrations.
-- This isolated test role proves explicit grants work without granting PUBLIC.
drop role if exists crm_ownership_operator_m8_test;
create role crm_ownership_operator_m8_test nologin;

do $$
declare
    helper_oid oid := 'crm_assert_related_tenant(text,uuid,uuid)'::regprocedure;
    executor_oid oid := 'crm_apply_tenant_ownership_proposal(uuid,text,text)'::regprocedure;
begin
    if exists (
        select 1
          from pg_proc p
          cross join lateral aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) a
         where p.oid in (helper_oid, executor_oid)
           and a.grantee = 0
           and a.privilege_type = 'EXECUTE'
    ) then
        raise exception 'Ownership helper or executor remains executable by PUBLIC';
    end if;

    if has_function_privilege('crm_ownership_operator_m8_test', helper_oid, 'EXECUTE')
       or has_function_privilege('crm_ownership_operator_m8_test', executor_oid, 'EXECUTE') then
        raise exception 'Unprivileged operator test role unexpectedly has EXECUTE';
    end if;
end;
$$;

grant execute on function crm_assert_related_tenant(text, uuid, uuid)
    to crm_ownership_operator_m8_test;
grant execute on function crm_apply_tenant_ownership_proposal(uuid, text, text)
    to crm_ownership_operator_m8_test;

do $$
begin
    if not has_function_privilege(
        'crm_ownership_operator_m8_test',
        'crm_assert_related_tenant(text,uuid,uuid)',
        'EXECUTE'
    ) then
        raise exception 'Explicit helper EXECUTE grant was not effective';
    end if;
    if not has_function_privilege(
        'crm_ownership_operator_m8_test',
        'crm_apply_tenant_ownership_proposal(uuid,text,text)',
        'EXECUTE'
    ) then
        raise exception 'Explicit executor EXECUTE grant was not effective';
    end if;
end;
$$;

revoke all on function crm_assert_related_tenant(text, uuid, uuid)
    from crm_ownership_operator_m8_test;
revoke all on function crm_apply_tenant_ownership_proposal(uuid, text, text)
    from crm_ownership_operator_m8_test;
drop role crm_ownership_operator_m8_test;
rollback;
