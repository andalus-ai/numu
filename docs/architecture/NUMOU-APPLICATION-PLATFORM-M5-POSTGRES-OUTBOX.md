# M5 — PostgreSQL Installation Store and Durable Transition Outbox

- **Status:** implementation and PostgreSQL integration tests added; Java 21 CI verification pending.
- **Depends on:** M4 tenant installation lifecycle (PR #1653).
- **Owner:** optional `application-platform` capability.
- **Adapter:** `application-platform-postgres-adapter`; plain JDBC, no Quarkus dependency.
- **Storage guarantee:** installation request, tenant/application uniqueness, idempotency claim, immutable transition and delivery outbox are committed in one PostgreSQL transaction.

## 1. Schema and tenant isolation

Migration `V1__application_installation.sql` creates:

- `application_installations`: tenant-owned current aggregate state, package digest, lifecycle status and optimistic revision.
- `application_installation_idempotency`: tenant + SHA-256 idempotency-key hash, request fingerprint and installation reference.
- `application_installation_transitions`: append-only lifecycle history with actor, correlation, reason, timestamp and revision.
- `application_installation_outbox`: transition event delivery state, retry count, availability, lease owner/token/expiry and stable failure code.

All four tables have forced PostgreSQL row-level security. Every adapter transaction sets `numou.tenant_id` with transaction-local `set_config(..., true)`; an absent tenant setting returns no rows and cannot insert data. Composite tenant foreign keys prevent a row from linking an installation or transition belonging to another tenant.

The database role used by the application must not own or bypass these tables in production. RLS is defense in depth; adapter queries still include explicit tenant predicates.

## 2. Atomic create and idempotency

The adapter:

1. Validates the supplied idempotency value is a SHA-256 hex digest (the raw key is not accepted by persistence).
2. Acquires a transaction-scoped advisory lock for tenant + idempotency hash.
3. Replays an existing request only when its stored request fingerprint matches application ID, package version and package digest.
4. Inserts the tenant installation using a tenant/application unique constraint.
5. Inserts the idempotency record, revision-1 transition history and initial outbox event.
6. Commits all records together.

Same-key/same-request calls return the original installation as `REPLAYED`. Same key with a different request, or a second key attempting to install the same application for the tenant, returns `CONFLICT`. The advisory lock serializes concurrent reuse of the same tenant idempotency key; the unique constraint remains the final guard for duplicate application installation.

## 3. Revision-safe transition persistence

`ApplicationInstallationTransitionStore` loads an aggregate only by tenant + installation ID and appends a transition using expected-revision compare-and-set. The adapter verifies the transition identity, target status, actor, timestamp and revision agree with the aggregate before writing.

A successful transition updates current state, appends immutable history and creates the matching outbox event in one transaction. A stale revision returns `false` and creates no history/outbox record. The caller must reload after a failed compare-and-set; it must not retry with the already-mutated in-memory aggregate.

The transition-history trigger rejects UPDATE and DELETE. Corrections are represented by subsequent transitions, not history rewriting.

## 4. Durable outbox delivery contract

`ApplicationInstallationOutboxStore` provides:

- Tenant-scoped bounded claims using `FOR UPDATE SKIP LOCKED`.
- Expired lease recovery.
- A new random lease token on each claim and incremented attempt count.
- Lease-fenced delivery completion (tenant, event, worker, token and unexpired lease must all match).
- Bounded retry scheduling with stable failure codes only.
- Terminal `DEAD` state after the configured maximum attempt count.

Delivery is at-least-once. A worker may crash after performing a provisioning action but before marking the outbox row delivered. Every downstream provisioning operation must therefore use the stable installation/event identity as its idempotency key. No exactly-once guarantee is claimed.

This milestone provides durable persistence and lease operations, not the managed worker, provider-specific provisioning adapters, operator dead-letter redrive UI, or a public REST API. Those remain separate runtime/operations work.

## 5. Verification added

The PostgreSQL Testcontainers suite covers:

- Atomic initial state/history/outbox creation.
- Same-key replay and conflicting idempotency reuse.
- Tenant/application uniqueness.
- Concurrent duplicate request serialization.
- RLS tenant isolation, including an unset tenant context.
- Optimistic revision compare-and-set.
- Transition-history immutability.
- Tenant-scoped outbox claims, lease expiry/recovery, stale-token fencing, delivery acknowledgement and terminal retry exhaustion.

The focused Java 21 workflow now runs the PostgreSQL adapter reactor in addition to the existing manifest/resolver and installation domain/application tests. CI has not yet reported a result for this M5 branch. Testcontainers requires Docker in the CI runner; local runs without Docker will skip the integration suite through `disabledWithoutDocker`.

## 6. Explicit non-goals

- No second tenant, identity, membership, entitlement or subscription model.
- No direct access to Tenancy or Security private persistence.
- No synchronous provisioning inside the install request.
- No automatic permission grants from manifest declarations.
- No deletion of capability-owned business records during uninstall.
- No competing workflow engine; Gamelan remains the orchestration engine.
- No remote Gamelan adapter until its deployed protocol, service authentication and create-run idempotency/reconciliation contract are verified.

## 7. Next milestone

**M6 — Installation Provisioning Worker and Operations**

1. Add a bounded managed worker around the outbox lease contract, with per-tenant concurrency limits and graceful shutdown.
2. Define idempotent provisioning ports for host composition, entitlements, permission approvals and navigation registration without taking ownership of those domains.
3. Add retry/backoff policy, dead-letter inspection and audited redrive with actor/reason/idempotency.
4. Add crash-after-side-effect recovery tests and worker lease-loss tests.
5. Expose tenant-authorized installation status/history queries and operator-safe aggregate health.
6. Keep external orchestration on Gamelan where long-running workflows, compensation or human approval are needed.
