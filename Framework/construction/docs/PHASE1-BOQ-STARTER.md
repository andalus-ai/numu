# Syirkah Phase 1 — BOQ foundation implementation starter

## P0 audit findings from `syirkah-framework.txt`

Verified in the source dump:
- `Boq` has `currentRevision`, but its lifecycle only moves `DRAFT -> UNDER_REVIEW -> APPROVED`; there is no visible revision creation/snapshot workflow.
- `BoqItem.amount()` directly multiplies quantity by rate, with no explicit rounding policy or quantity/rate unit compatibility check.
- `BoqQuantity` and `BoqRate` use string units. `BoqRate` uses `java.util.Currency`.
- `foundation.domain.valueobject.Money` and `MonetaryRoundingPolicy` exist. The foundation currency is `tech.kayys.syirkah.foundation.domain.valueobject.Currency`, distinct from `java.util.Currency`.
- `InMemoryBoqRepository` stores live aggregate object references in a `ConcurrentHashMap`, and `save` replaces the stored object by id. It is suitable as a dev/test adapter, not evidence of durable persistence, immutable snapshots, or optimistic concurrency.
- `BoqRepository` exposes `findByProjectId`, but no revision-specific query is visible in the inspected interface.

## Trusted tenant context (latest slice)

The BOQ revision handlers now resolve tenant identity through the required server-side `BoqRevisionTenantContext`, parallel to `BoqRevisionActorContext`. Tenant IDs are absent from client command records and are passed explicitly through all `BoqRevisionWorkflow` operations. See `docs/trusted-tenant-context.md` and run `scripts/check-boq-trusted-context-contract.sh`. This is a source-breaking integration seam; bind it to the host application's authenticated tenant resolver and run runtime integration tests before production use.

## Included code

1. `BoqCalculationPolicy.java`: explicit line amount precision/rounding and quantity/rate unit compatibility. It intentionally returns `BigDecimal` because the current `BoqRate` still uses `java.util.Currency`; currency unification should be a separate coordinated migration.
2. `BoqRevisionSnapshot.java`: immutable value snapshot of BOQ header and line values, with a lifecycle for draft -> review -> approved. `captureDraft(...)` copies values from the existing `Boq` and `BoqItem` types. Approved snapshots cannot be edited because all fields and item snapshots are immutable; transitions return new instances.

## Integration notes

This is an integration starter, not a patch already applied to a checked-out repository. The only provided source was a text dump, not the actual Maven working tree, so this package cannot honestly claim to compile against all modules. Add these files under `construction-domain`, then wire snapshot persistence through a revision-aware SPI repository and application commands. Approval must persist the approved snapshot and BOQ state in one transaction (or use an outbox/transactional event strategy). Do not rely on the in-memory adapter for production persistence.

The code uses Java records and `BigDecimal`, matching patterns visible in the source dump.

## Wire calculation into existing `BoqItem` (backward-compatible change)

Add this overload to `BoqItem.java`; leave the existing `amount()` temporarily for compatibility, then migrate callers to the explicit policy overload:

```java
public BigDecimal amount(BoqCalculationPolicy policy) {
    Objects.requireNonNull(policy, "Calculation policy cannot be null");
    return policy.calculateLineAmount(quantity, rate);
}
```

After callers have migrated, deprecate the no-policy `amount()` method so application code cannot accidentally use an implicit calculation rule.

## Application workflow wiring (second slice)

Added a command/handler layer for:
- capturing a draft revision snapshot;
- submitting a specific revision for review;
- approving a specific revision.

The handlers follow the existing `CommandHandler` + Mutiny `Uni` pattern in the source dump. They delegate persistence and aggregate coordination to `BoqRevisionWorkflow`, a new SPI port. This is deliberate: doing `BoqRepository.save(...)` and snapshot persistence as two unrelated writes inside a handler would allow the BOQ status and its revision snapshot to diverge if one write failed.

### Required adapter guarantees before production use

An adapter implementing `BoqRevisionWorkflow` must:
1. load the BOQ and its items from the authoritative repositories;
2. capture scalar item values and the calculation-policy version;
3. enforce the requested `revisionNumber` equals the current revision;
4. atomically persist the snapshot transition and BOQ aggregate transition (or use a transactional outbox and a recoverable consistency protocol);
5. enforce compare-and-set/status checks so duplicate or concurrent submit/approve commands cannot both succeed;
6. emit domain/integration events only after commit, preferably through an outbox;
7. authorize the actor and record audit metadata. Actor IDs supplied by commands are not authorization by themselves.

`BoqRevisionWorkflow` is a port contract, not a completed persistence adapter. No production database adapter or migration is included because the source provided was a text dump rather than a checked-out working tree and no persistence technology was confirmed.

### Suggested integration order

1. Add domain classes under `construction-domain`.
2. Add `BoqRevisionWorkflow` under `construction-spi`.
3. Add commands/handlers under `construction-application`.
4. Implement the workflow in the persistence/application adapter with transaction + optimistic concurrency.
5. Add tests for duplicate submission, stale revision, approval races, transaction rollback, and snapshot immutability.
6. Migrate call sites from `BoqItem.amount()` to `amount(BoqCalculationPolicy)` and only then deprecate implicit calculation.


## Persistence slice (third slice)

Added:
- `BoqRevisionSnapshotRepository.java`: insert-only snapshot creation and status compare-and-set replacement contract.
- `db/migration/V20261009_01__boq_revision_snapshots_and_outbox.sql`: PostgreSQL tables for revision headers, copied line items, and a transactional outbox.
- `docs/boq-revision-transaction-protocol.md`: transaction ordering, concurrency requirements, and release tests.

This is a schema and port contract, **not yet a production database adapter**. The source dump does not identify the physical BOQ table name or the chosen transaction API, so the migration deliberately omits that foreign key and does not claim to be drop-in. Review the precision (`NUMERIC(24, 8)`), tenant scoping, and Flyway version before applying to a real database. The workflow adapter must insert/update snapshot rows and the existing BOQ aggregate within one transaction, and insert outbox events before commit.

## Outbox relay + command idempotency (fourth slice)

Added `OutboxRelayStore` and `OutboxDeliveryPort` contracts, a deterministic capped exponential `OutboxRetryPolicy` with unit tests, a `CommandIdempotencyStore` contract, and a follow-up PostgreSQL migration for outbox leases and idempotency records. These follow the source dump's use of Mutiny `Uni` and transactional-outbox patterns.

Still not implemented: a concrete Panache/PostgreSQL adapter, a scheduled relay worker, broker integration, and endpoint-level idempotency middleware. The schema is a proposed additive migration; validate table naming, migration ordering, JSON payload shape, tenant context, and actual DB constraints in the checked-out repository before deploying. Run the module's Maven tests after integration.

## Outbox relay orchestration (fifth slice)

Added `OutboxRelayService`, an application-level orchestrator that:

- claims a bounded batch using the `OutboxRelayStore` lease contract;
- calls the broker adapter after the claim call has completed, so the claim transaction should not remain open during network I/O;
- acknowledges successful delivery only with the claimed lease token;
- schedules retry using `OutboxRetryPolicy`, truncates persisted error messages, and marks attempts terminal at the configured limit;
- supports deterministic tests through an injected `Clock`.

The service intentionally does not declare a Quarkus `@Scheduled` method. The source dump shows Mutiny and an outbox publisher but does not establish which scheduler extension/configuration this module uses. Bind `runOnce()` from the project's existing scheduler or job runner. Configure batch size and lease duration so expected broker latency fits comfortably within the lease; if delivery can exceed the lease, add lease renewal or bounded broker timeouts.

Important behavior: a failure after broker acceptance but before `markPublished` can cause redelivery. This is expected at-least-once behavior; consumers must deduplicate by event ID. Store failures in `markFailed` are not swallowed and should be surfaced to runtime monitoring. Verify that the adapter increments `attempts` consistently with `OutboxRetryPolicy` before wiring this service.

## PostgreSQL outbox adapter slice

The `adapter/postgres/outbox/PgOutboxRelayStore.java` file now implements the relay store using the Vert.x Mutiny `PgPool` pattern found in the source dump. Migration `V20261011_01__outbox_terminal_failures.sql` adds terminal failure state and rebuilds the claim index. See `docs/postgres-outbox-adapter.md` for attempt semantics, service-role requirements, and required live PostgreSQL concurrency tests.

**Not yet verified:** this bundle has not been overlaid on the actual checkout, compiled with the repository's dependency graph, or run against PostgreSQL/a broker. Treat migration names/order and database permissions as integration items to verify.


## PostgreSQL idempotency adapter (sixth slice)

Added `PgCommandIdempotencyStore`, which uses the tenant/key primary key to arbitrate concurrent reservations, compares operation + request fingerprint, and stores completed JSON responses. Added `RequestFingerprint` and unit tests, plus `docs/postgres-idempotency-adapter.md`.

**Important:** the current adapter uses `PgPool` calls and is not transaction-bound to the business command. It is not sufficient to guarantee that reservation, business writes, outbox insert, and response completion commit atomically. The next required step is a transaction-aware implementation sharing one SQL connection/transaction, then PostgreSQL integration tests. No full Maven build was run against a real checkout.

## Latest slice: transaction-aware idempotent command executor

Added `TransactionalCommandRunner`, `TransactionalCommandContext`, `CommandOutcomeCodec`, and `IdempotentCommandExecutor`. This makes the required atomicity boundary explicit: reservation, BOQ revision writes, outbox insertion, and completed response must share one database transaction. See `docs/transaction-aware-command-execution.md`.

This is still a port/application slice, not a concrete database transaction adapter. It must be wired to the exact Quarkus/Vert.x API and dependency versions in a real repository checkout, then tested against PostgreSQL.

## Latest slice: transaction-bound PostgreSQL command context

Added `PgTransactionalCommandRunner` and `PgTransactionalCommandContext`. The runner
uses Vert.x Mutiny `PgPool.withTransaction`; idempotency SQL is executed using the
transaction callback's `SqlConnection`. `PgBoqRevisionOperationsFactory` defines the
missing connection-bound BOQ persistence adapter. See
`docs/postgres-transaction-runner.md`.

**Still required:** implement the BOQ operations factory against the actual table
mapping, ensure outbox insertion uses the same connection, and run live PostgreSQL
concurrency/rollback tests. The source provided is a text dump, not a checkout with
build files, so compilation against the repository's exact dependency versions remains
unverified.

## Transactional outbox writes from commands (next slice)

Added `TransactionalOutboxEvent` and `TransactionalCommandContext.appendOutboxEvent(...)`. `PgTransactionalCommandContext` inserts outbox records on the same transaction-bound connection as idempotency reservation/completion. Commands should write BOQ state, revision snapshot, outbox event, and completed response in one transaction, and must not call the broker directly. See `docs/transactional-outbox-command-write.md`.

**Not yet verified:** no Maven build or live PostgreSQL test has been run against a real checkout. Verify that the outbox migration is applied, tenant identity is trusted, JSON payloads are valid, and all BOQ adapter writes use the same connection.

## Latest slice: PostgreSQL BOQ snapshot adapter

Added `PgBoqRevisionSnapshotRepository`, a transaction-bound adapter for the snapshot schema. It inserts immutable line-item content, reconstructs snapshots, and uses compare-and-set lifecycle status updates. It does not acquire its own connection or transaction.

**Not yet verified against the actual repository:** this remains a starter bundle rather than a patch to a checked-out project. Run the project's Maven build and PostgreSQL integration tests, validate enum/schema/module compatibility, and reconcile migrations before deployment. Live BOQ loading/revision allocation remains dependent on the actual BOQ persistence mapping.

## Latest slice: transactional BOQ revision number allocation

Added `PgBoqRevisionNumberAllocator` and `V20261012_01__boq_revision_counter.sql`. Allocation is serialized per BOQ through a row lock and is intended to run on the same transaction-bound connection as snapshot insertion, BOQ updates, outbox insertion, and idempotency completion. The caller supplies `minimumNextRevision` because the source dump does not establish whether the first immutable snapshot should match the aggregate's `currentRevision` or increment it. See `docs/postgres-boq-revision-allocation.md`.

**Not yet verified:** no Maven compilation or live PostgreSQL concurrency test has been run. This does not yet implement the live BOQ table mapping or the complete BOQ operations factory.

## Latest slice: PostgreSQL BOQ revision operations orchestration

Added `DefaultPgBoqRevisionOperationsFactory`, which composes snapshot persistence, revision allocation, a transaction-bound live-BOQ adapter seam, and tenant-tagged outbox writes. Added `PgLiveBoqRevisionStore` / `PgLiveBoqRevisionStoreFactory` contracts because the source dump does not establish the existing live BOQ and item SQL mappings. Added `V20261013_01__outbox_tenant_id.sql` and `docs/pg-boq-revision-operations-factory.md`.

This remains an implementation starter. Implement the live BOQ adapter against the actual checked-out schema and run PostgreSQL integration tests before merging. The outbox tenant migration leaves legacy tenant IDs nullable until they can be backfilled from authoritative ownership data.

## Latest slice: disposable PostgreSQL schema and concurrency test harnesses

Added three Docker-based test scripts:

- `scripts/postgres-schema-smoke-test.sh` applies the migrations in a disposable PostgreSQL 16 container and checks snapshot/item/outbox/idempotency constraints plus transaction rollback.
- `scripts/postgres-concurrency-test.sh` races revision-counter allocation and idempotency reservations across independent sessions.
- `scripts/postgres-outbox-recovery-test.sh` checks `SKIP LOCKED` claim exclusivity, expired-lease reclamation, stale-token fencing, and terminal-event exclusion.

Run them from the bundle root. They require Docker and do not connect to an existing database. See `db/test/` for scope and environment variables.

**Validation boundary:** shell syntax and archive integrity are checked when this ZIP is built. The PostgreSQL scripts still need to be run in an environment with Docker. They exercise SQL/schema behavior, not the Java adapter or broker end to end; the project-specific `PgLiveBoqRevisionStoreFactory` remains an integration seam because the source dump does not establish live BOQ table mappings.

## Latest slice: BOQ workflow protocol regression test

Added `scripts/postgres-boq-workflow-test.sh` and `db/test/boq-workflow-test.md`. Against a disposable PostgreSQL instance, it checks the intended capture -> review -> approval sequence, snapshot/live-state compare-and-set behavior, one tenant-tagged outbox event per successful transition, stale-transition rejection, and rollback of snapshot/outbox writes together.

Because the source dump does not define the physical live BOQ schema, the test uses a test-only `test_live_boq` table and SQL protocol equivalent. It does **not** call the Java operations factory or prove the live adapter implementation. The remaining integration step is still to implement `PgLiveBoqRevisionStoreFactory` against the real project mapping and run the actual Java/PostgreSQL integration suite.

## Latest slice: configurable live BOQ PostgreSQL adapter

Added `MappedPgLiveBoqRevisionStoreFactory` and `PgLiveBoqSqlMapping` to implement
the previously abstract live-BOQ store seam. The adapter supports row locking, stable
item loading, and revision/status compare-and-set updates on the caller's transaction-
bound connection. SQL values are parameterized and mapping identifiers are validated
and quoted. See `docs/mapped-live-boq-store.md`.

**Important boundary:** the source dump still does not establish the actual physical
BOQ/item schema. This is a configurable adapter, not a claim that Syirkah's real schema
has been discovered or wired. Do not configure it with guessed identifiers. Also, the
current SPI lacks an expected-tenant argument, so tenant ownership must be enforced at
a trusted command boundary or the SPI must be extended before multi-tenant exposure.
No Maven build or live PostgreSQL execution was possible from this source-only bundle;
compilation and integration remain unverified.


### Tenant-scoped live BOQ access

`MappedPgLiveBoqRevisionStoreFactory` now requires a trusted tenant ID for BOQ lookup,
item loading, and compare-and-set state changes. See `docs/mapped-live-boq-store.md` and
run `scripts/postgres-tenant-isolation-test.sh` (Docker required) for the SQL predicate
protocol test. The support snapshot/counter tables remain keyed by BOQ UUID, so global
BOQ-ID uniqueness is a required integration invariant unless those schemas are migrated.


## Tenant-scoped BOQ revision persistence (next slice)

Added `V20261014_01__tenant_scope_boq_revision_storage.sql`. Snapshot, item, and revision-counter keys now include `tenant_id`; repository reads and status CAS operations require tenant ID, and revision allocation is independent per `(tenant_id, boq_id)`. The PostgreSQL smoke/concurrency scripts now assert that the same BOQ/revision/item UUID can exist in two tenants without collision.

**Migration safety:** this migration intentionally aborts if revision snapshots, items, or counters already contain rows. Do not assign a default tenant. On a populated environment, first create an authoritative backfill using the real live BOQ ownership mapping, then review and apply an adapted migration. Docker/PostgreSQL and Java compilation have not been executed in this environment.


## Authorization and separation of duties (latest slice)

- `BoqRevisionAuthorization` is a required fail-closed port for capture, submission, and approval. The PostgreSQL operations factory has no permissive default; application wiring must provide the real authorization policy.
- The immutable snapshot domain rejects approval by the same actor who submitted the revision. The transactional operation also rejects this before writing the approved snapshot/live state/outbox event.
- The starter does not define Syirkah's actual role model or permission store. Implement the port using the project's trusted authenticated principal and tenant membership; do not trust actor IDs or roles supplied in a request body.
- See `docs/boq-revision-authorization.md` for the contract and integration checklist.

### Authorization contract checks

- `scripts/check-boq-authorization-contract.sh` statically checks authorization-before-resource-access for capture, submission, and approval; mandatory authorization injection; and self-approval guards.
- `db/test/boq-authorization-test-matrix.md` defines the integration scenarios that must be executed against the real identity adapter and transaction runner. The static check does not prove runtime authorization or rollback behavior.

## Trusted actor identity (latest slice)

BOQ revision command payloads no longer carry caller-selected `actorId` values. The three handlers require `BoqRevisionActorContext` and resolve the actor from trusted server-side identity. See `docs/trusted-actor-context.md` for host wiring and compatibility requirements. Tenant identity is still a separate integration concern because the workflow SPI does not yet carry tenant context explicitly.

Static check: `scripts/check-boq-authorization-contract.sh`. This starter bundle has no Maven/Gradle build descriptor and omits some host-project dependencies; Java compilation and runtime integration remain unverified.

## Trusted context runtime unit tests (latest slice)

Added `src/test/java/tech/kayys/syirkah/construction/application/boq/handler/TrustedCommandContextTest.java`. It verifies trusted actor/tenant propagation for all three commands, fail-closed behavior when actor or tenant context is missing, fixed-clock propagation, and that command records do not expose actor/tenant fields. See `db/test/trusted-context-runtime-tests.md`.

These Java tests were added but not compiled or executed in this source-only bundle because the host project's build descriptor and exact dependencies are not present. They test the handler boundary, not the full authorization/database transaction path.

## Latest slice: command transaction atomicity and retry protocol

Added `scripts/postgres-command-atomicity-test.sh` and `db/test/command-atomicity-test.md`. The disposable PostgreSQL protocol test injects a failure after the idempotency reservation, revision snapshot/item writes, test-only live BOQ update, and outbox insert, then asserts those writes do not survive. It also checks that a successful command persists a completed idempotency response with the snapshot and outbox event, and that a retry with the same tenant/key does not overwrite the result or duplicate the event.

**Validation boundary:** this script's shell syntax and ZIP integrity can be checked here, but Docker is not available in this environment, so the SQL was not executed against live PostgreSQL. It is still a SQL-level protocol test and does not invoke `IdempotentCommandExecutor`, `PgTransactionalCommandRunner`, CDI wiring, or the actual Syirkah live BOQ adapter. Those require integration tests in the real project build.

## Latest slice: Java idempotency executor unit tests

Added `src/test/java/tech/kayys/syirkah/construction/application/idempotency/IdempotentCommandExecutorTest.java` with seven cases for reservation success, cached completed retries, key reuse conflicts, incomplete reservations, action failure, failed completion, and input validation. Run `scripts/check-idempotency-test-contract.sh` for a lightweight source-presence check; details and limits are in `db/test/java-idempotency-test-notes.md`.

**Validation boundary:** the source-contract script verifies that the seven test cases are present; it does not compile or execute Java. The fake runner used by these unit tests does not simulate database transactions. Because this starter contains no Maven/Gradle build descriptor and lacks the host dependency graph, Java execution and PostgreSQL integration remain unverified. The next acceptance gate is to run these tests in the actual Syirkah build and add integration tests against the real PostgreSQL adapters and transaction runner.

### Same-key idempotency race test

`scripts/postgres-idempotency-race-test.sh` races two independent PostgreSQL sessions against the same tenant/key and verifies one reservation, one simulated business effect, a preserved completed response, and no overwrite from a conflicting fingerprint. It requires Docker and `psql`; it is a SQL-level protocol test, not a Java adapter integration test. See `db/test/postgres-idempotency-race-test.md`.

## Java transaction-runner integration tests (next slice)

Added `PgTransactionalCommandRunnerIntegrationTest.java` to exercise the actual transaction runner/context and idempotent executor against PostgreSQL: callback failure should roll back both the reservation and outbox insert, and concurrent same-key calls should execute the callback once. See `db/test/pg-transaction-runner-integration.md` for prerequisites. This test source has not been compiled or run in the starter environment; wire it into the actual Quarkus module and configured test database before relying on it.

## Live BOQ revision operations integration tests

`src/test/java/tech/kayys/syirkah/construction/adapter/postgres/boq/DefaultPgBoqRevisionOperationsFactoryIntegrationTest.java` exercises the actual revision operations factory against PostgreSQL using explicitly test-only live BOQ tables. It verifies capture writes and rollback of snapshot, item, revision counter, live state, and outbox event. See `db/test/default-pg-boq-revision-operations-integration.md` for prerequisites and limitations. Run `scripts/check-default-pg-boq-operations-integration-contract.sh` for the static contract check; this is not a substitute for compiling/running the Java integration test.

### BOQ lifecycle concurrency tests

The BOQ operations integration test also covers concurrent approval and stale-revision submission. Run `scripts/check-boq-lifecycle-concurrency-contract.sh` for a static source-contract check; see `db/test/boq-lifecycle-concurrency-integration.md` for live PostgreSQL test setup and limitations.

### BOQ full lifecycle verification increment

Added a PostgreSQL-backed integration scenario for capture → submit for review → independent approval. It asserts one outbox event per successful transition and verifies that the submitter cannot approve their own revision. `scripts/run-phase1-static-checks.sh` provides one entry point for all static contract checks and shell syntax validation; see `db/test/boq-full-lifecycle-integration.md`. Java compilation and live PostgreSQL test execution still require the full Syirkah build and migrated test database.

### Unified Phase 1 validation runner

Use `./scripts/run-phase1-validation.sh --static-only` for fast source-contract and shell-syntax checks, or `./scripts/run-phase1-validation.sh --all` to add disposable PostgreSQL protocol tests (requires Docker and local `psql`). See `docs/phase1-validation-runbook.md` for scope, prerequisites, and evidence limits. SQL tests do not invoke Java adapters; full build and Java/PostgreSQL integration must be verified in the actual Syirkah checkout.

## Tenant-scope migration preflight

Before applying `V20261014_01__tenant_scope_boq_revision_storage.sql` to an existing target, run the read-only report in `db/preflight/V20261014_tenant_scope_preflight.sql` using the reviewed target connection. It reports whether the three revision tables satisfy the migration's empty-table guard; it does not establish tenant ownership or approve a migration. Any non-empty table requires a reviewed authoritative tenant backfill plan. See `docs/tenant-scope-migration-preflight.md`.

## Tenant-scope migration postflight

After the reviewed tenant-scope migration completes, run `db/postflight/V20261014_tenant_scope_postflight.sql` against the same target database. It checks `tenant_id NOT NULL`, ordered tenant-scoped composite primary keys, the validated composite item-to-snapshot foreign key, and the tenant/project lookup index. It is a structural check only and does not prove row ownership or application-level tenant isolation. See `docs/tenant-scope-migration-postflight.md`; the preflight remains mandatory before migration.

## Tenant-isolation SQL acceptance slice

Expanded `scripts/postgres-tenant-isolation-test.sh` to cover tenant-scoped snapshot/item/counter storage, composite item-to-snapshot integrity, no-op cross-tenant writes, and tenant-filtered outbox polling. Run `scripts/check-tenant-isolation-contract.sh` for the source-contract check or `scripts/run-phase1-validation.sh --postgres-only` for disposable PostgreSQL protocol tests. These fixtures are not production-adapter tests and do not prove that all application queries enforce tenant scope; execute equivalent tests in the full Syirkah build before relying on isolation guarantees.


## Outbox tenant-safety acceptance slice

Added `scripts/postgres-outbox-tenant-safety-test.sh` to check tenant-filtered claims, cross-tenant acknowledgement fencing, retry availability/attempt increments, and exclusion of legacy events with null tenant ownership. Run `scripts/check-outbox-tenant-safety-contract.sh` for static assertions or `scripts/run-phase1-validation.sh --postgres-only` for the disposable PostgreSQL SQL-protocol suite. This is not a production-adapter or broker test; align the claim predicates with the actual adapter and backfill legacy ownership from authoritative data before enabling delivery. See `db/test/postgres-outbox-tenant-safety-test.md`.


## Outbox consumer duplicate-delivery acceptance

Added `scripts/postgres-outbox-consumer-idempotency-test.sh` to simulate broker acceptance followed by a lost database acknowledgement, lease expiry, and redelivery. A test-only consumer inbox keyed by `(tenant_id, event_id)` ensures that two deliveries create one recorded business effect when inbox insertion and the effect share a transaction. Run `scripts/check-outbox-consumer-idempotency-contract.sh` for static assertions or include it in `scripts/run-phase1-validation.sh --postgres-only` for the disposable PostgreSQL protocol test. This is an at-least-once delivery/idempotent-consumer pattern, not an exactly-once transport guarantee or a production broker/adapter test. See `db/test/postgres-outbox-consumer-idempotency-test.md`.


## Outbox lease-fencing acceptance

Added `scripts/postgres-outbox-lease-fencing-test.sh` to simulate an expired lease reclaimed by a second worker and verify that the stale worker cannot acknowledge, reschedule, or renew the newer claim. The current lease holder must still be able to acknowledge the event. Run `scripts/check-outbox-lease-fencing-contract.sh` for static assertions or include it in `scripts/run-phase1-validation.sh --postgres-only` for disposable PostgreSQL protocol testing. This tests starter SQL predicates, not the production relay adapter; verify tenant/event/status/lease-token fencing in the actual Syirkah implementation. See `db/test/postgres-outbox-lease-fencing-test.md`.


### Outbox retry, terminal failure, and operator replay acceptance

The retry/terminal/replay scenario is documented in `db/test/postgres-outbox-retry-terminal-replay-test.md`. Run `scripts/check-outbox-retry-terminal-replay-contract.sh` for static checks, or `scripts/run-phase1-validation.sh --all` for the disposable PostgreSQL suite. The replay audit table is test-only; production relay/admin integration remains unverified.

## Outbox relay service unit-test increment

Added `OutboxRelayServiceTest.java` for deterministic application-layer tests of empty batches, lease-token acknowledgement, retry scheduling and error bounds, terminal attempts, per-event failure isolation, and constructor validation. Run `scripts/check-outbox-relay-service-test-contract.sh` for a static source-presence check. The Java tests are not claimed as compiled or executed because this starter bundle does not include the actual Syirkah build descriptor/dependency graph; they must be run in the real module. See `db/test/outbox-relay-service-unit-tests.md`.

Added `PgOutboxRelayStoreIntegrationTest.java` to exercise the real PostgreSQL relay adapter for eligible claiming, persisted lease identity, stale-token fencing after reclaim, bounded failure recording, and invalid-argument handling. Run `scripts/check-pg-outbox-relay-store-integration-contract.sh` for static presence checks. The Java integration test is not claimed as compiled or executed; run it inside the actual Syirkah module against an isolated PostgreSQL test database after reconciling fixture DDL with the canonical migrations. See `db/test/pg-outbox-relay-store-integration.md`.


## Outbox database-role boundary (latest slice)

Added `scripts/postgres-outbox-role-boundary-test.sh` and `db/test/postgres-outbox-role-boundary-test.md`. The disposable PostgreSQL fixture checks tenant RLS, least-privilege relay column grants, command-writer separation, and the absence of `BYPASSRLS`/superuser privileges. Run it with the full validation runner or directly when Docker is available. This is a test fixture only: map its policy and roles to the actual Syirkah migrations and trusted tenant-context setup before treating database isolation as verified.


## Tenant-context propagation and pooled connection safety

Added `scripts/postgres-tenant-context-pool-safety-test.sh` to verify transaction-local `app.tenant_id` context does not leak across commits or rollbacks when a PostgreSQL session is reused, and that independent sessions remain tenant-scoped under forced RLS. The test is included in `scripts/run-phase1-validation.sh --postgres-only`. Run `scripts/check-tenant-context-pool-safety-contract.sh` for its static contract check. This fixture does not exercise Syirkah's real authentication/request context or reactive connection pool; add concurrent application-level tests in the full repository before treating runtime context propagation as verified. See `db/test/postgres-tenant-context-pool-safety-test.md`.

### Application tenant-context concurrency

The `TrustedCommandContextTest` suite now includes a concurrent two-tenant handler-boundary scenario. Run `scripts/check-application-tenant-context-concurrency-contract.sh` for its static source contract. This test harness does not substitute for exercising the production authentication/context bean and connection pool in the full Syirkah repository; see `db/test/application-tenant-context-concurrency.md`.

### Tenant-context transaction integration

`PgTenantContextTransactionIntegrationTest` verifies that a transaction-local PostgreSQL tenant setting does not survive commit or rollback when the same pooled connection is reused. See `db/test/pg-tenant-context-transaction-integration.md`. This is a database primitive check, not proof of end-to-end authentication or production RLS enforcement.


### Tenant RLS write-boundary acceptance

The disposable PostgreSQL test `scripts/postgres-tenant-rls-write-boundary-test.sh` checks fail-closed behavior without tenant context, RLS `WITH CHECK` rejection of cross-tenant inserts and ownership changes, and same-tenant update behavior. It is included in `scripts/run-phase1-validation.sh --all`. The fixture is not evidence that production Syirkah roles or migrations enforce the same policies; reconcile those with the canonical repository before relying on this boundary.

## Real-repository integration gate

Before treating the starter as integrated, run `scripts/verify-real-repository-readiness.sh` from the complete Syirkah repository root. See `docs/phase1-production-integration-gate.md` for the required build, canonical migration, trusted tenant context, database-role, and runtime acceptance sequence. The preflight is a presence check only; it does not replace compilation or integration testing. It is expected to report `BLOCKED` when run from this starter bundle alone.
