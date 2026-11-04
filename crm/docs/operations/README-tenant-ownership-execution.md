# CRM tenant ownership proposal execution (M8)

## Purpose and safety boundary

V17 adds `crm_apply_tenant_ownership_proposal`, a single-record executor for an
ownership proposal that has already reached `APPROVED`. V18 removes the
default PostgreSQL `PUBLIC EXECUTE` privilege from the internal
`crm_assert_related_tenant` helper as well. Neither function is an ownership
discovery mechanism and neither infers a tenant from a participant, parent,
referrer, or related record.

Both functions run as **SECURITY INVOKER**. They do not authenticate the
supplied executor string. A production caller must derive that value from a
verified operator identity, enforce a separate operational authorization
policy, and restrict database access to a dedicated trusted role. Do not expose
either function directly to ordinary application users or accept executor
identity from an HTTP request body.

The migrations revoke function execution from `PUBLIC`. They intentionally
do not create deployment roles or grant execution to a guessed role name.
Create a dedicated role in the deployment and explicitly grant both functions.
For example:

```sql
create role crm_ownership_operator nologin;

grant execute on function crm_assert_related_tenant(text, uuid, uuid)
    to crm_ownership_operator;
grant execute on function crm_apply_tenant_ownership_proposal(uuid, text, text)
    to crm_ownership_operator;

-- SECURITY INVOKER means the role also needs the table rights used by the
-- executor and its audit trigger. Scope these grants to the CRM schema.
grant select, update on crm_tenant_ownership_proposals
    to crm_ownership_operator;
grant select, insert on crm_tenant_ownership_proposal_audit
    to crm_ownership_operator;
grant select, update on
    crm_accounts,
    crm_customers,
    crm_leads,
    crm_opportunities,
    crm_referrals,
    crm_territories,
    crm_territory_assignments
    to crm_ownership_operator;
```

The role must not be granted to ordinary application roles. The example table
grants cover the supported aggregate set; a deployment may narrow them further
only after verifying every relationship read, target update, and audit-trigger
write required by its chosen operations. Do not grant schema ownership or
superuser access.

## Execution semantics

Call the function inside an explicit transaction and commit only after the
returned result is checked:

```sql
begin;
select crm_apply_tenant_ownership_proposal(
    '<approved-proposal-uuid>',
    '<verified-operator-subject>',
    '<change-ticket-or-runbook-reference>'
);
commit;
```

- `APPROVED` is required. `PENDING` and `REJECTED` proposals are refused.
- The proposal row is locked `FOR UPDATE`; the target CRM row is locked
  `FOR UPDATE`; populated relationship targets are locked `FOR KEY SHARE`.
- The target update is compare-and-set: it only changes a row whose
  `tenant_id IS NULL`, and exactly one affected row is required.
- The proposal moves to `APPLIED` in the same transaction. The V16 trigger
  writes the immutable APPLIED audit snapshot in that transaction.
- Repeating an already-applied proposal returns `ALREADY_APPLIED` only if the
  target still has the proposal's tenant. It does not write another audit event.
- If any check fails, the caller must roll back. Target ownership, proposal
  state, and audit insertion are atomic.

## Relationship policy

The first executor intentionally applies one record at a time. Any populated
relationship must already point to a record owned by the proposed tenant.
Missing, NULL-owned, and cross-tenant related records are rejected. This
explicitly closes the NULL-key bypass behavior of composite foreign keys.
Resolve related records through their own independently reviewed proposals
first, then retry the dependent proposal.

Checked relationships:
- accounts: parent account;
- opportunities: customer;
- referrals: referrer account, target lead, and target opportunity;
- territories: parent territory;
- territory assignments: territory and account.

Customers and leads have no direct aggregate relationship checked by this
executor. Activity and element-collection rows inherit ownership through their
parent aggregate and are not independently reassigned.

## Operational controls

1. Verify evidence and approval independently before execution.
2. Ensure submitter and reviewer separation (enforced by V16); use a separate
   operator identity for execution according to organizational policy.
3. Use a durable change ticket/runbook reference as the execution reference.
4. Execute one proposal per transaction; inspect the returned result.
5. Verify the target `tenant_id`, proposal status, and audit event after commit.
6. Preserve database audit logs and restrict direct DML on proposal/audit tables.
7. Never edit immutable proposal evidence to make a failed execution pass.
8. Keep the trusted operator role assignment and its membership changes under
   deployment change control; database role membership is part of the security
   boundary.

V17/V18 do not provide a batch executor, HTTP endpoint, UI, automatic retries,
or cross-proposal dependency orchestration. They do not protect against a
database owner/superuser who can alter triggers, grants, or table contents.
Application authorization must still use Numou's verified identity and tenant
context; a database function grant is not a substitute for actor authorization.
