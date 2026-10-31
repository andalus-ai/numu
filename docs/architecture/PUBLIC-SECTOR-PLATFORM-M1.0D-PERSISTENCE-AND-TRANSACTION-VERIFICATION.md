# Public-Sector Platform M1.0d — Persistence and Transaction Verification

**Status:** Source inspection completed for the paths listed below; implementation gates remain open.  
**Branch:** `feat/public-sector-platform-m1-0a-inventory`  
**Scope:** Verify the existing persistence and transaction patterns relevant to Organization–Party convergence. This milestone does not introduce schema, adapters, or application behavior.

## 1. Decision

Do not implement the Organization–Party association persistence yet.

The repository contains working-looking PostgreSQL JDBC adapters for canonical Party, Party-to-external-identity, and Party-to-Participant persistence. It does not yet provide verified production persistence for the top-level Organization aggregate, an Organization–Party association repository, or a concrete Ecosystem transaction composition that proves the Party/profile/event operations share one physical transaction.

The Ecosystem application contract explicitly requires transaction-scoped repositories and durable outbox append. The inspected tree exposes that contract, but not its concrete runtime implementation. A new association adapter built before this is resolved would risk creating a second transaction model and claiming atomicity that is not demonstrated.

## 2. Evidence reviewed

The following source files and migration definitions were read on this branch:

### Ecosystem application contracts and handler

- `core/framework/ecosystem/ecosystem-application/src/main/java/tech/kayys/numou/ecosystem/application/port/EcosystemTransaction.java`
- `core/framework/ecosystem/ecosystem-application/src/main/java/tech/kayys/numou/ecosystem/application/port/EcosystemTransactionRunner.java`
- `core/framework/ecosystem/ecosystem-application/src/main/java/tech/kayys/numou/ecosystem/application/port/OrganizationRepository.java`
- `core/framework/ecosystem/ecosystem-application/src/main/java/tech/kayys/numou/ecosystem/application/api/command/RegisterOrganizationHandler.java`

### Ecosystem JDBC adapter and migrations

- `core/framework/ecosystem/ecosystem-persistence-jdbc/pom.xml`
- `.../JdbcPartyRepository.java`
- `.../JdbcPartyIdentityAssociationRepository.java`
- `.../JdbcPartyParticipantAssociationRepository.java`
- `.../db/migration/V1__party_identity_associations.sql`
- `.../db/migration/V2__canonical_parties.sql`
- `.../db/migration/V3__party_participant_associations.sql`
- `.../JdbcPartyRepositoryPostgresTest.java`

### Top-level Organization capability

- `organization/organization-adapter/pom.xml`
- `organization/organization-spi/src/main/java/tech/kayys/numou/organization/spi/OrganizationRepository.java`
- `organization/organization-adapter/src/main/java/tech/kayys/numou/organization/adapter/memory/InMemoryOrganizationRepository.java`
- `organization/organization-adapter/src/main/java/tech/kayys/numou/organization/adapter/memory/InMemoryOrganizationMembershipRepository.java`

### Shared transaction abstraction

- `core/framework/foundation/numou-foundation-application/src/main/java/tech/kayys/numou/foundation/application/transaction/TransactionRunner.java`
- `.../UnitOfWork.java`
- `.../DefaultUnitOfWork.java`

The recursive Git tree for this branch contained 13,249 paths and was not truncated. The Ecosystem subtree contained the three JDBC repository implementations and three migrations listed above, plus the transaction interfaces and application ports. No concrete class implementing `EcosystemTransactionRunner`, no Ecosystem JDBC Organization-profile repository, and no Organization–Party association JDBC repository/migration appeared in that subtree. This is a branch-tree inventory, not proof that no implementation exists in an external deployment repository or another independently composed application.

## 3. Verified persistence behavior

### 3.1 Canonical Party

`JdbcPartyRepository` persists the global `ecosystem_party` identity table. The Party identifier is the primary key; tenant ownership is intentionally absent.

Observed behavior:

- JDBC calls are wrapped in Mutiny `Uni` and dispatched to the default worker pool.
- Save opens a connection, disables auto-commit, performs insert or lifecycle update, then commits; failures roll back.
- Existing Party lifecycle writes lock the current row with `FOR UPDATE`.
- Lifecycle updates enforce optimistic version progression and immutable Party kind.
- PostgreSQL unique-constraint failures are translated to an application-level exception.
- Reads are performed on separate connections and are not shown participating in a wider command transaction.

Migration `V2__canonical_parties.sql` defines a global UUID primary key, constrained kind/status values, timestamps, and a non-negative version. It does not contain tenant membership or Organization aggregate ownership.

A PostgreSQL Testcontainers test verifies persistence/rehydration, lifecycle versioning, stale-write conflict, and rejection of an unsupported kind. The test is evidence of the repository test contract; this milestone did not execute it.

### 3.2 External identity association

`JdbcPartyIdentityAssociationRepository` persists a tenant-scoped mapping between an external issuer/subject and Party.

Observed safeguards:

- Every lookup includes `tenant_id`.
- A partial unique index permits only one active mapping for a tenant + issuer + subject.
- Revoked rows remain as history; reactivation/rewriting of a revoked association is rejected.
- Save uses a local JDBC transaction and row locking for an existing association.
- The database unique index remains the concurrency authority for competing active-subject claims.

This association represents an external identity link. It is not an Organization affiliation, tenant membership, or authorization grant.

### 3.3 Party-to-Participant association

`JdbcPartyParticipantAssociationRepository` persists a tenant-scoped Party-to-Participant business relationship with a role and lifecycle.

Migration `V3__party_participant_associations.sql` references canonical Party and constrains active role uniqueness within a tenant. Its role vocabulary includes business roles such as customer, supplier, service provider, member, and employee.

This is not the proposed OrganizationId-to-PartyId identity association. In particular, `ParticipantId` is not `OrganizationId`; reusing this table or contract would conflate distinct relationship semantics.

### 3.4 Top-level Organization aggregate

The top-level `organization/` capability has an `OrganizationRepository` port keyed by `OrganizationId`. Its reviewed adapter module currently contains in-memory Organization, OrganizationMembership, and OrganizationUnit repositories. The adapter POM has domain/SPI and test dependencies, but no JDBC/PostgreSQL dependency.

The Organization aggregate is tenant-associated and owns its own lifecycle and structure. Organization membership uses `OrganizationId + UserId`; it is not a Party affiliation. No production database adapter or migration for this aggregate was found in the reviewed module tree.

### 3.5 Ecosystem Organization profile

The Ecosystem Organization profile aggregate is keyed by `PartyId`, and the application `OrganizationRepository` port is also keyed by PartyId. `RegisterOrganizationHandler` validates that the Party exists, is active, and has kind `ORGANIZATION`, then saves the profile and publishes its domain events through the transaction context.

This is a Party-keyed profile, not a mapping from the top-level Organization aggregate's `OrganizationId`. It cannot by itself satisfy the proposed cross-capability association contract.

The reviewed Ecosystem JDBC module has no implementation of this profile repository port and no profile-table migration among its three migrations.

## 4. Transaction and event-delivery findings

### 4.1 Ecosystem transaction contract

`EcosystemTransaction` documents a strong requirement:

- repositories and event publisher exposed by one transaction context must share the same physical database transaction;
- the event publisher must append events to a durable outbox before commit;
- broker delivery must occur asynchronously after commit;
- the application handler must not publish directly to a broker from the transaction context.

`EcosystemTransactionRunner` defines the application boundary and callback semantics. However, the reviewed Ecosystem module tree contains the interface only; no concrete implementation or runtime composition was found.

Therefore, the contract is a design requirement, not verified evidence of current atomic behavior.

### 4.2 JDBC repositories are currently operation-scoped

The three reviewed JDBC repositories obtain connections directly from `DataSource`. Their writes manage their own local transactions. No constructor or repository method accepts a transaction-scoped connection/session, and no inspected adapter binds these repositories to a common physical connection.

This means they cannot be assumed to participate atomically in a multi-repository command simply because the command is wrapped in the `EcosystemTransactionRunner` interface. A concrete transaction-scoped adapter/factory must demonstrate connection sharing, rollback behavior, and outbox enlistment.

### 4.3 Shared Foundation UnitOfWork is not an outbox substitute

The reviewed `DefaultUnitOfWork`:

- keeps its context in a `ThreadLocal`;
- joins nested calls through that context;
- runs before-commit synchronizations;
- marks the context completed and invokes after-commit synchronizations;
- invokes the optional `EventPublisher` after the before-commit phase;
- recovers publication failures rather than propagating them.

The interface is a useful lifecycle abstraction, but this implementation does not itself demonstrate that database writes and outbox rows are committed in the same physical transaction. Its after-commit publication behavior can lose an event if the process fails after the database commit and before successful delivery. The ThreadLocal context also requires explicit verification under the reactive execution/context-propagation model before it is used as a cross-thread transaction context.

Do not wire the Organization–Party association to this implementation and claim durable atomic event delivery without an adapter-specific proof.

## 5. Persistence readiness matrix

| Concern | Observed state | M1.0d assessment |
|---|---|---|
| Canonical Party persistence | PostgreSQL JDBC repository + migration + Testcontainers test | Exists; transaction is local to repository operation |
| Party lifecycle concurrency | Row lock and optimistic version check | Implemented in reviewed repository |
| External identity association | Tenant-scoped JDBC repository + partial unique index | Exists; separate semantics |
| Party-to-Participant association | Tenant-scoped JDBC repository + role constraints | Exists; separate semantics |
| Top-level Organization persistence | In-memory repository in reviewed adapter | Production persistence not verified |
| Ecosystem Organization profile persistence | Application port and handler | JDBC implementation/migration not found in reviewed Ecosystem tree |
| OrganizationId–PartyId association | Contract documented in M1.0c | No persistence implementation |
| Shared Ecosystem transaction | Application interfaces and contract | Concrete implementation/runtime binding not found |
| Durable Ecosystem outbox | Required by transaction interface documentation | Concrete storage/writer/enlistment not found |
| Distributed transaction semantics | Not established | Open; must use explicit remote validation/compensation or orchestration contract |

## 6. Required implementation gates

Do not begin association schema or adapter work until these gates are closed with source and test evidence:

1. **Find the actual composition root.** Identify where Ecosystem handlers, Party repositories, Organization profile repositories, transaction runner, and event publisher are bound in each supported runtime.
2. **Prove one physical transaction.** Demonstrate that a Party read/validation, Organization-profile write (where applicable), Organization–Party association write, and outbox append use the same JDBC connection/transaction in a modular monolith.
3. **Establish the outbox owner.** Identify the existing durable outbox table, writer, event envelope, relay, retry/dead-letter policy, and tenant/actor/correlation propagation. Reuse an existing owned capability if its contract fits; do not add a competing generic outbox.
4. **Choose Organization persistence ownership.** Decide whether the top-level Organization capability receives a production adapter or whether the association belongs in a different owner. Do not let Ecosystem write the Organization capability's private tables.
5. **Define the association repository port.** Keep `OrganizationId` and `PartyId` as distinct typed identifiers. Specify active uniqueness, lifecycle, provenance, idempotency, optimistic concurrency, and historical correction semantics.
6. **Prove database constraints and migration safety.** Include unique active mapping constraints, FK strategy where deployment topology permits it, staged/backfill compatibility, rollback/forward recovery, and deterministic migration ordering.
7. **Prove reactive behavior.** Test cancellation, failure, worker-thread/context propagation, transaction cleanup, nested calls, and rollback. Do not rely on ThreadLocal behavior without evidence.
8. **Prove distributed behavior separately.** In a microservice deployment, do not promise one ACID transaction across services. Define versioned API/event contracts, idempotent commands, outbox/inbox, retry, compensation, and reconciliation.
9. **Verify tests in the owning Maven reactors.** Run focused PostgreSQL/Testcontainers integration tests and relevant architecture checks after an implementation is selected. No build or test is claimed by this document.

## 7. Next milestone

**M1.0e — Compatibility API and persistence design** should start only after the above implementation and composition questions have been resolved. It should define the stable association port and versioned external contract, then identify whether the in-process implementation can join the verified transaction or whether the distributed adapter requires an explicit asynchronous lifecycle.

M1.0d does not authorize changing existing Organization or Party identifiers, rewriting tenant membership, migrating production data, or introducing an Organization–Party association table.
