# CRM Tenant Isolation: M1 Implementation Contract

Status: implementation contract; not evidence that tenant isolation is already enforced.

## Goal

Make CRM data isolation explicit and consistent in standalone and embedded deployments. A tenant identifier supplied by request JSON, query parameters, or an unchecked header is never authoritative. Tenant scope must originate from the authenticated host runtime or a verified security integration.

## Deployment contract

### Standalone CRM

The standalone runtime must either install and configure the shared security integration that authenticates the caller and binds a verified tenant, or refuse to expose tenant-owned CRM operations until a trusted tenant resolver is configured. Missing, ambiguous, or unverified tenant context must fail closed. A ThreadLocal fallback or default tenant value is not authentication.

### Embedded CRM

The host must provide a trusted tenant-scope port backed by its authenticated request/job context. CRM must not infer tenancy from payload fields. Async work must carry an explicit, validated tenant envelope rather than rely on a request thread's context.

## Application boundary

Introduce a small CRM application port/value for required tenant scope. It should expose the verified tenant ID and have no public constructor/factory that accepts untrusted HTTP input. Resolve the scope at entry boundaries and pass it to tenant-owned repository operations. For scheduled jobs, consumers, and outbox processing, require an explicit verified tenant envelope.

Avoid relying only on ambient global state: it can leak between pooled threads, is hard to test, and does not naturally propagate to asynchronous work. If existing foundation context is used, always clear it in finally and test concurrent requests.

## Persistence rules

Tenant ownership must be applied consistently to accounts and parent-account references; customers, contacts, and addresses; leads and lead activities; opportunities and opportunity activities; referrals; territories and territory assignments; outbox rows and any other CRM-owned async records.

Every tenant-owned operation must be scoped: lookup by ID, existence checks, list/search, counts, pagination, update, delete, and save/upsert. A caller must not be able to supply a record ID from another tenant and mutate it. Prefer repository methods that require a tenant argument or a required scope object over optional predicates.

Relationship checks must verify that both sides belong to the same tenant. Database constraints/indexes should reinforce these invariants where supported; application checks alone are not sufficient for all race conditions.

## Migration and legacy data

Do not assign all existing records to one invented default tenant. The initial migration must be staged:

1. Add a migration preflight/report that counts records by table and identifies records without a verified ownership mapping.
2. Establish the source of truth for tenant ownership. Existing participant_id or actor fields must not be treated as tenant IDs unless the domain contract explicitly confirms that equivalence.
3. Backfill only from a verified mapping, with an auditable mapping artifact and deterministic reruns.
4. Keep unresolved records quarantined or block tenant-required activation until reconciliation is complete.
5. Add non-null constraints, tenant-aware uniqueness, and composite indexes only after the preflight is clean.
6. Provide rollback/restore instructions and verify the migration against a copy of representative legacy data.

Do not use IF NOT EXISTS as a substitute for validating an already-deployed schema.

## Uniqueness and relationships

Review current global indexes such as customer number uniqueness. Decide whether each key is globally unique or unique per tenant; where tenant-local, use a composite key/index (for example tenant_id plus customer_number) and account for existing duplicates during reconciliation.

All child rows must be reachable only through a parent in the same tenant. Where child tables lack their own stable ID, preserve existing persistence semantics while adding safe parent/tenant constraints rather than introducing unrelated API changes.

## Acceptance tests

At minimum, integration tests must seed two tenants and verify:
- missing tenant context is rejected;
- tenant A cannot read tenant B records by direct ID;
- tenant A cannot update/delete tenant B records;
- list/search/count/pagination only return tenant A data;
- cross-tenant parent, child, and relationship IDs are rejected;
- uniqueness is enforced according to the documented tenant policy;
- activities and outbox processing cannot cross tenant boundaries;
- tenant context is cleared between reused worker threads;
- standalone and embedded compositions both provide verified scope;
- legacy migration preflight blocks unresolved ownership rather than silently assigning it.

Tests should exercise the real adapter/runtime and database behavior, not only mock repositories.

## Delivery sequence

1. Confirm security wiring and tenant source of truth in standalone and embedded runtimes.
2. Implement the required tenant-scope contract and tenant-aware repository API.
3. Apply it across all CRM aggregates and child records.
4. Add migration preflight/backfill/constraints with verified ownership mapping.
5. Add adapter integration tests and run the CRM Maven reactor plus relevant integration/CI suites.
6. Open one milestone-sized draft PR with migration notes, test results, and known deployment prerequisites.

## Current verified gap

The source audit found unscoped ID-based reads/existence checks/deletes across account, customer, lead, opportunity, referral, territory, and territory-assignment repository adapters. Existing migrations for the primary CRM tables do not declare tenant ownership consistently. The shared security filter exists in the repository, but the standalone CRM adapter's inspected dependency/configuration does not establish that it is installed and active. These findings require runtime verification; they are not a claim that every deployed host is exposed.

## Explicit non-goals

- Do not make CRM the canonical owner of party identity or contact verification; use Ecosystem references where their tenant contract is established.
- Do not trust request-provided tenant IDs.
- Do not claim production readiness until migration, integration tests, and runtime security configuration are verified.
