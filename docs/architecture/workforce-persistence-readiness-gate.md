# Workforce persistence readiness gate

- **Status:** M1 implementation prerequisite; no production persistence is claimed.
- **Repository snapshot:** `main` at `9295c65f09f9f4de752af41ca16ec34341b4d5c3`.
- **Related:** [Person/Party mapping decision](person-party-identity-mapping-decision.md).

## Verified repository evidence

1. `workforce/workforce-adapter/pom.xml` describes the module as providing in-memory adapters and does not declare a JDBC driver, persistence framework, or migration dependency.
2. The current Workforce adapter tree contains `InMemoryWorkerRepository` and related in-memory repositories. No Worker-specific JDBC/JPA adapter or Worker schema migration was found in the recursive source-tree audit.
3. `WorkerRepository` is a persistence port with `findByPersonRef(TenantId, PersonRef)`; the current port has no explicit tenant-scoped `findById` method.
4. `Worker` has a private constructor and a public `create(...)` factory that starts a new Worker in `ACTIVE` state and raises `WorkerCreated`. No persistence reconstitution factory was found in the inspected class. Reusing `create` to load an existing row would incorrectly model a load as a new creation and could emit a creation event.
5. The domain aggregate maintains lifecycle state and audit metadata. A durable adapter must preserve these values and aggregate version without raising new domain events when loading.
6. No database-level unique constraint for `(tenant_id, person_ref)` was found in the inspected Workforce tree.

## Required decisions before implementation

### A. Persistence boundary

Add a separate `workforce-persistence-jdbc` module (or use the repository's approved persistence technology if one is already mandated by the root architecture). Keep JDBC entities and SQL details out of the domain and application modules. Register the module in the Workforce reactor and the intended deployment/composition module only after dependency direction is verified.

### B. Safe aggregate reconstitution

Introduce a package/API-level persistence reconstitution mechanism reviewed against aggregate invariants. It must restore the original `WorkerId`, tenant, person reference, type, lifecycle status, audit fields, timestamps and version without raising `WorkerCreated` or other events. Do not use the public creation factory to hydrate stored rows.

### C. Database invariants

The schema must include at least:
- `worker_id` primary key;
- non-null `tenant_id`, `person_ref`, `worker_type`, `status`;
- creation/update audit fields and aggregate version;
- unique `(tenant_id, person_ref)` only if the product confirms one Worker record per person per tenant is the intended rule.

If the domain permits multiple simultaneous worker engagements for one person within a tenant, change the uniqueness key to the approved engagement/organization scope before applying a migration. Do not blindly add the unique constraint based on the current in-memory index.

### D. Tenant isolation

- Require tenant scope on person lookups and any tenant-owned record lookup.
- Resolve tenant from trusted authenticated context at the application boundary.
- For tenant-owned IDs, prefer `findById(TenantId, WorkerId)` over unscoped lookup in externally reachable use cases.
- Ensure every SQL statement that reads or mutates a tenant-owned row includes the tenant predicate, unless the operation is explicitly a privileged cross-tenant administrative path.
- Never trust a tenant ID supplied only by a request body.
- Translate uniqueness violations and missing records into stable application errors; do not leak SQL details.

### E. Transactionality and concurrency

Use a database unique constraint as the final concurrency guard. Handle two concurrent registration attempts by allowing one insert to win and mapping the other constraint violation to a duplicate-worker application result. A pre-insert read is not sufficient. Keep writes, audit updates, and any required outbox event in a single transaction if domain-event publication is part of the persistence contract.

### F. Required verification

- PostgreSQL integration tests using the repository's established Testcontainers conventions, if approved by the build.
- Reconstitution round-trip tests covering every persisted field and asserting no creation event is raised on load.
- Cross-tenant read/update/delete denial tests.
- Concurrent duplicate registration test proving exactly one insert succeeds.
- Migration upgrade and clean-install tests.
- Optimistic locking/version conflict tests if the aggregate version is persisted.
- Maven reactor and CI evidence before production readiness is claimed.

## Acceptance gate

Do not call Workforce persistence production-ready until the persistence module, deployment wiring, migration, tenant predicates, database uniqueness policy, reconstitution behavior, and integration tests are implemented and verified. This document records a verified gap; it does not implement the adapter.
