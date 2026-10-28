# CRM tenant isolation implementation contract

Status: implementation contract for CRM M1; this document is not evidence that runtime isolation is implemented.

## Goal

Every CRM operation must execute within one verified tenant boundary in standalone, modular-monolith, and distributed deployments. The same application and domain contracts should work in each composition. A caller-provided tenant string, an arbitrary HTTP header, an entity ID, or a thread-local fallback is not sufficient evidence of tenant identity.

## Security invariants

1. **Fail closed.** If the adapter cannot resolve a verified tenant for an operation, it must reject the operation before touching CRM persistence. No implicit default tenant and no nullable-tenant fallback for live operations.
2. **Trusted source.** HTTP deployments resolve tenant identity from the authenticated server-side security context after the host's authentication and tenant-membership policy succeeds. A header may be a tenant claim only if the authentication layer validates it against the principal. Application code must not trust request JSON or raw headers.
3. **Embedded composition.** The host supplies a documented tenant-scope port backed by its verified request/job context. CRM must not depend on a private host persistence implementation. Background jobs and event consumers must carry a verified tenant in their execution envelope.
4. **Scope every query.** ID lookup, existence checks, unique-key lookups, list/search/count, updates, and deletes must include tenant scope. Possession of a UUID is not authorization. Cross-tenant IDs should behave as not-found at public boundaries.
5. **Scope writes.** New rows are stamped with the trusted tenant. Updates and deletes must match both the row ID and trusted tenant; an update must never change ownership. A caller cannot select the tenant by setting an entity field.
6. **Validate relationships.** Referenced customers, leads, opportunities, territories, assignments, and other CRM relationships must belong to the same tenant before mutation. Child activity/contact/address rows are reached only through a tenant-scoped parent.
7. **Outbox parity.** An outbox event's tenant must come from the same verified execution scope as the aggregate mutation. Publishing or consuming an event must not broaden access to another tenant.
8. **Identity boundary.** Ecosystem Party owns canonical party identity and canonical contact/address records. CRM owns sales/account relationship, lifecycle, qualification, commercial terms, ownership, and CRM interactions. A CRM customer may reference a Party through a stable opaque ID; it must not silently duplicate or replace Party as the system of record.

## Intended application contract

Introduce a CRM-owned TenantScope port/value contract at the application boundary. It should expose a required tenant identifier for the current command/query execution and make missing context explicit (exception/result, not null or a magic value). The adapter implementation is composition-specific:

- Standalone HTTP: bind it only after the shared security filter has authenticated the principal and verified tenant membership.
- Modular monolith: adapt the host's already-verified tenant context to the CRM port.
- Distributed worker: derive it from a validated message/job envelope and verify the event's tenant against the operation being performed.

Do not use the static TenantContext thread-local fallback as proof of authentication. Ensure scope lifecycle is cleared/restored for reused worker threads and reactive execution. Do not assume request-scoped CDI state automatically propagates through asynchronous callbacks.

Repository APIs should either take a required tenant value explicitly or receive it through a narrow, testable repository execution scope. Prefer explicit application-port inputs when that fits existing handler contracts; avoid adding tenant parameters to domain entity behavior where tenancy is an execution/security concern. All seven repository adapters must follow one consistent approach.

## Persistence rollout

Use an expand/reconcile/contract migration sequence; never assign every legacy row to an arbitrary tenant.

### Expand

- Add nullable tenant_id columns to CRM-owned root tables and appropriate composite indexes.
- Keep the outbox's existing tenant field but reconcile its semantics and constraints with the same trusted tenant identifier.
- Add supporting indexes for tenant-scoped lookup/search and tenant-aware uniqueness.
- Do not immediately add NOT NULL or a default tenant while legacy rows remain unresolved.

Root tables to inventory and migrate: crm_accounts, crm_customers, crm_leads, crm_opportunities, crm_referrals, crm_territories, and crm_territory_assignments. Child tables such as lead/opportunity activities and customer contacts/addresses must remain reachable only through a correctly scoped parent; add direct tenant columns only if access patterns or database constraints require them.

### Reconcile

- Provide a read-only preflight report of row counts by table, null tenant values, duplicate keys that become duplicates under tenant-aware uniqueness, orphaned child rows, and relationship mismatches.
- Resolve tenant ownership from authoritative deployment/business records with an auditable mapping.
- Make ambiguous rows an explicit operator decision. Do not infer ownership from email/domain or silently put them in a default tenant.
- Support a dry run and an idempotent, resumable backfill; record counts and reconciliation evidence.

### Contract

Only after the preflight is clean and all rows are reconciled:
- enforce tenant_id NOT NULL on live tenant-owned rows;
- add tenant-aware uniqueness and foreign-key constraints where supported by the existing schema;
- remove transitional compatibility behavior in a separately reviewed migration;
- document rollback/restore procedure and production rollout ordering.

## Required regression coverage

Use PostgreSQL-backed adapter integration tests (not only mocked repositories) with tenants A and B. Cover:

- no verified tenant: reads and writes fail before database access;
- tenant A cannot read, find by email/customer number, search, count, update, or delete tenant B rows;
- duplicate business keys across tenants are permitted only where the business contract requires tenant-local uniqueness;
- relationship IDs from another tenant are rejected without leaking their existence;
- child activities, contacts, and addresses cannot be reached through an unscoped parent;
- outbox events carry the exact tenant of the aggregate transaction and consumers reject mismatches;
- async/reactive and background execution do not leak tenant scope between tasks;
- standalone runtime rejects anonymous/tenantless access, and embedded composition uses the host's verified tenant provider;
- migration preflight reports legacy rows without assigning a default tenant.

## Delivery sequence

1. Add the required tenant-scope application contract and bind it in each supported composition.
2. Add tenant ownership to persistence entities and implement tenant-scoped repository operations across all seven repositories in one coordinated change.
3. Add relationship/outbox enforcement and PostgreSQL integration tests.
4. Add expand migration plus preflight/backfill tooling; do not contract until reconciliation evidence exists.
5. Reconcile Party references separately with a compatibility/backfill plan; do not bulk-convert CRM customers as part of the tenant migration.
6. Run the CRM Maven reactor and relevant host composition tests, record exact commands/results, and open a milestone PR only with demonstrated coverage.

## Acceptance criteria

- No live CRM repository path can read or mutate data without a verified tenant scope.
- Every repository operation is tenant constrained, including ID-based delete/existence and search/count.
- No cross-tenant relationship or outbox mismatch can commit.
- Legacy tenant ownership is reconciled with an auditable report before mandatory constraints are enabled.
- Tests prove isolation against a real PostgreSQL database and at the standalone/embedded composition boundaries.
- Existing customer integrations remain compatible or have explicit versioned migration steps.
