# CRM tenant ownership proposal execution (M7)

## Purpose and safety boundary

V17 adds `crm_apply_tenant_ownership_proposal`, a single-record executor for an
ownership proposal that has already reached `APPROVED`. It is not an ownership
discovery mechanism and it does not infer a tenant from a participant, parent,
referrer, or related record.

The function runs as **SECURITY INVOKER**. It does not authenticate the supplied
executor string. A production caller must derive that value from a verified
operator identity, enforce a separate operational authorization policy, and
restrict database access to a dedicated trusted role. Do not expose this
function directly to ordinary application users or accept executor identity
from an HTTP request body.

The migration revokes function execution from `PUBLIC`. A deployment must
explicitly grant execution to its trusted operational role. Because the
function is SECURITY INVOKER, that role also needs only the table privileges
required by the function; do not grant broad schema ownership or superuser
access. Review the exact grants against the deployment's database role model.

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

V17 does not provide a batch executor, HTTP endpoint, UI, automatic retries, or
cross-proposal dependency orchestration. It does not protect against a database
owner/superuser who can alter triggers, grants, or table contents. Database
privileges and operational identity controls remain part of the deployment's
security boundary.
