# M4 — Tenant Application Installation Lifecycle

- **Status:** domain and application request contracts implemented; persistence adapter and transport remain later milestones.
- **Depends on:** M1–M3, merged as PRs #1646, #1647 and #1649.
- **Owner:** new optional `application-platform` capability; manifest validation remains in the framework plugin reactor.
- **Java baseline:** 21.

## 1. Why this capability is separate

M3 validates a package and resolves its capability requirements. It does not install an application for a tenant. Installation state is a platform business lifecycle and must not be stored in the executable plugin registry or in the Tenant aggregate.

M4 therefore introduces a separate `application-platform` reactor with domain and application artifacts. It depends on the canonical foundation `TenantId`; it does not create another tenant model or reach into Tenancy persistence.

## 2. Delivered contracts

### Domain

- Stable reverse-DNS `ApplicationId`, SemVer package version and SHA-256 package digest.
- Tenant-scoped `ApplicationInstallation` aggregate.
- Explicit lifecycle: `REQUESTED → VALIDATING → AWAITING_APPROVAL / PROVISIONING → INSTALLED → ENABLED`, with `SUSPENDED`, `FAILED`, retry and `UNINSTALLING → UNINSTALLED` branches.
- Every transition increments a monotonic aggregate revision and returns an audit/outbox-ready transition record with tenant, application, actor, correlation ID, timestamp and stable reason code.
- Approval is an explicit transition. The direct provisioning path is allowed only from validation; an installation awaiting approval cannot bypass the approval method.
- Failure state stores a stable failure code, not arbitrary exception text or secrets.
- Uninstall changes application activation state only. Capability-owned business records are outside this aggregate and are not cascade-deleted.

### Application request use case

- The command accepts application ID, package version and idempotency key; it does **not** accept tenant ID or actor ID.
- Tenant and actor are derived from a host-supplied verified `InstallationActor`.
- A host authorization port is called before package lookup. Its adapter is responsible for the install permission and applicable subscription entitlement.
- The catalog port must return only publisher-verified, integrity-checked and manifest-validated packages.
- The compatibility port checks the package requirements against the tenant's selected host runtime profile, using M3's capability resolver.
- The store port accepts a SHA-256 hash of the idempotency key and requires atomic persistence of the installation, uniqueness claim, idempotency record and initial transition outbox event.
- A repeated identical request is reported as `REPLAYED`; a conflicting reuse/duplicate install is rejected.

## 3. Reuse and ownership decisions

- **Tenancy:** supplies the canonical `TenantId`; application-platform does not own tenant lifecycle or membership.
- **Security:** a host adapter maps verified security context to `InstallationActor` and implements authorization. The caller cannot select another tenant in the command.
- **Commerce Subscription:** the existing subscription capability owns plan entitlements and usage. Application-platform does not create a second subscription, plan or billing model; its authorization adapter composes the existing entitlement check where the host's licensing policy requires it.
- **Capability runtime:** M3 owns manifest parsing and capability resolution. This milestone does not load arbitrary package code or alter `RuntimePluginManager`.
- **Gamelan:** remains the workflow engine. Application-platform does not introduce a scheduler or duplicate workflow persistence.

## 4. Reliability and security boundaries

The `ApplicationInstallationStore` contract is intentionally atomic: the initial installation row, tenant/application uniqueness, idempotency hash and transition outbox record must commit together. The raw idempotency key is never a persistence field. M5 must implement this guarantee in PostgreSQL and prove it under concurrent duplicate requests.

No provisioning side effect is performed synchronously inside the request handler. The durable outbox consumer will be added with the PostgreSQL adapter and worker milestone. It must use a stable operation ID and idempotent provisioning ports. A crash after committing the request must be recoverable without creating duplicate subscriptions, permissions, navigation registrations or other resources.

Permissions declared by an application manifest remain requests. Installation does not grant them automatically. Tenant authorization and per-resource authorization remain enforced by the authoritative host/security and capability boundaries.

## 5. Not claimed as delivered

- PostgreSQL persistence, migrations, row-level security and concurrent uniqueness enforcement.
- Outbox relay, provisioning worker, retry schedule or dead-letter operations.
- HTTP/gRPC API or admin UI.
- Publisher registry, signing key management or package download service.
- Application upgrade and rollback orchestration.
- Permission grant workflow, role mapping or navigation runtime registration.
- Gamelan remote adapter.

These are explicit subsequent milestones, not hidden in-memory substitutes.

## 6. Verification

- Domain tests cover valid/invalid lifecycle transitions, approval enforcement, revision monotonicity, failure/retry and uninstall provenance.
- Application tests cover trusted tenant context, authorization ordering, package lookup, compatibility failure, hashed idempotency key and replay.
- M4 adds a focused Java 21 workflow for the M3 framework plugin tests and the new application-platform reactor. This isolates both from the unrelated security-vault prerequisite failure observed in the broad Numou CI workflow.
- Passing status must be confirmed from the resulting workflow run; adding a workflow is not itself test evidence.

## 7. Next milestone

**M5 — PostgreSQL Installation Store and Durable Provisioning Outbox**

Implement:
1. Tenant-scoped installation schema with composite uniqueness/FKs, optimistic revision and RLS.
2. Atomic create-if-absent with idempotency-key hash and initial outbox record in one transaction.
3. Transition persistence with expected-revision compare-and-set.
4. Outbox lease, retry/backoff, terminal failure and safe replay contracts.
5. Concurrent duplicate-request, cross-tenant isolation, crash/replay and audit integrity PostgreSQL tests.
6. Keep package provisioning operations idempotent and outside the database transaction.
