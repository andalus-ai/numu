# M6 — Tenant Application Installation Provisioning Worker

- **Status:** implementation branch; focused verification pending.
- **Depends on:** M5 PostgreSQL installation store and durable outbox (PR #1656).
- **Owner:** optional `application-platform` capability.
- **Worker contract:** shared `ManagedWorker` lifecycle; one configured worker instance per tenant.
- **Delivery:** at-least-once; host provisioning adapters must be idempotent.

## 1. Ownership and boundary

Application Platform owns the installation lifecycle and dispatch coordination. The host owns the actual capability activation operations. `ApplicationInstallationProvisioningPort` is the explicit host boundary; this milestone does not reach into capability-owned repositories or create a second permission, entitlement, subscription, tenancy or identity model.

The port receives the validated package, canonical tenant, installation ID, original requester, correlation ID and a stable `operationId` equal to the durable request event ID. A host adapter must make `ensureProvisioned` converge for repeated calls with the same operation ID. It must not translate manifest permission requests into grants without the host's authorization/approval policy.

## 2. Durable state progression and crash recovery

For `APPLICATION_INSTALLATION_REQUESTED`, the dispatcher:

1. Loads the installation by tenant + installation ID and verifies application ID, package version and digest against the committed outbox event.
2. Moves `REQUESTED -> VALIDATING` using the aggregate's revision compare-and-set.
3. Loads the publisher-verified, integrity-checked package from the catalog and rechecks the committed digest.
4. Rechecks the tenant's selected host capability profile.
5. Moves `VALIDATING -> PROVISIONING` with revision compare-and-set.
6. Calls the host provisioner with the stable event ID as `operationId`.
7. Moves `PROVISIONING -> INSTALLED` with revision compare-and-set.
8. Acknowledges the outbox lease only after the durable state is installed.

If the process crashes after step 5 or after the host side effect, the next lease owner resumes from `PROVISIONING` and invokes the same idempotent operation again. A crash after the final state commit but before acknowledgement is harmless: the next delivery sees `INSTALLED` and acknowledges without repeating provisioning.

Status-change events created by the worker are acknowledged by this dispatcher; they are not recursively treated as installation requests. Approval, suspension, uninstall and compensation are separate lifecycle operations and are not inferred from arbitrary event payloads.

## 3. Failure policy

Host adapters may throw `ApplicationInstallationProvisioningException` with a stable uppercase failure code and an explicit retryable flag. Only the stable code is persisted; exception messages and secrets are not copied into the outbox.

- Retryable failures use capped exponential backoff and the configured maximum attempts.
- Non-retryable failures are sent directly to the outbox dead-letter state.
- When retry exhaustion is recorded, the dispatcher attempts a revision-safe transition to `FAILED` with the same stable code.
- Unknown dependency failures are classified as retryable `PROVISIONING_TEMPORARY_FAILURE`.

If the lease has expired or another worker has taken ownership, lease fencing prevents the stale worker from acknowledging or rescheduling the event. The installation aggregate uses revision CAS to prevent stale workers from overwriting a newer lifecycle state.

## 4. Managed worker behavior

`ApplicationInstallationOutboxManagedWorker` implements the shared `ManagedWorker` contract:

- one worker is configured for one trusted tenant;
- fixed-delay polling, bounded batches and sequential event processing;
- no overlapping poll on the same worker instance;
- database lease fencing coordinates multiple replicas;
- graceful stop waits for the current dispatch to drain up to a configured timeout;
- a safe local health snapshot exposes last completion time and a stable failure code, never exception messages.

The host runtime is responsible for discovering authorized tenants, creating worker instances, injecting trusted data sources and provisioning adapters, and managing lifecycle. This module does not scan tenants globally or elevate a user-supplied tenant ID.

## 5. Tests and verification

Application Platform unit tests cover:

- successful state progression to `INSTALLED`;
- crash-window retry from `PROVISIONING` with the same stable operation ID;
- permanent package digest mismatch and transition to `FAILED`;
- acknowledgement of non-request status events without invoking provisioning;
- managed worker polling, non-overlap lifecycle and graceful shutdown.

The existing M5 PostgreSQL/Testcontainers suite continues to verify real lease claims, expiry recovery, lease fencing, tenant RLS and retry exhaustion. The application-platform verification workflow runs the plugin, application and PostgreSQL adapter reactors on Java 21.

## 6. Explicit non-goals

- No new workflow engine; Gamelan remains the orchestration engine for long-running processes, human approval and compensation.
- No arbitrary package code execution.
- No direct permission grants from manifest declarations.
- No synchronous provisioning in the installation request transaction.
- No exactly-once delivery claim.
- No universal transaction across multiple capability databases; host provisioning adapters must use their own transactional outbox/idempotency patterns.
- No HTTP admin API or operator dead-letter redrive endpoint in this worker milestone; those require their own authorization and audit contracts.
