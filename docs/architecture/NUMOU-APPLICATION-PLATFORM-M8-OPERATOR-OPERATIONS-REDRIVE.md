# M8 — Tenant-Scoped Operator Operations and Audited Redrive

- **Status:** implementation branch; verification workflow blocked before the Application Platform module tests by an unrelated Survey Maven parent-resolution failure.
- **Depends on:** M7 terminal failure reconciliation (merged PR #1710).
- **Owner:** optional `application-platform` capability.
- **Host responsibility:** authenticated principal resolution, permission policy and API transport.

## 1. Operator application boundary

The application layer exposes an operations facade for:
- bounded installation transition history;
- bounded tenant-scoped provisioning dead-letter inspection;
- bounded immutable redrive audit history.
- audited redrive of an eligible failed installation.

The facade calls a host-provided `ApplicationInstallationOperatorAuthorizationPort` before every read or mutation. The host must derive tenant and actor identity from its authenticated request context; it must not trust tenant or actor identifiers supplied in request bodies. The capability does not introduce HTTP routes or a second identity/authorization model.

## 2. Redrive semantics

A redrive is accepted only when:
1. the host authorizes the authenticated actor for the tenant's installation-redrive permission;
2. the installation exists in that tenant and is currently `FAILED`;
3. a dead provisioning request/retry event exists for that installation;
4. the idempotency key is new, or is an exact replay of the same actor, installation and reason.

The PostgreSQL adapter commits the following in one transaction:
- transition the aggregate from `FAILED` to `VALIDATING` with revision CAS;
- append immutable operator transition history;
- enqueue a new `APPLICATION_INSTALLATION_RETRY_REQUESTED` event;
- append immutable redrive audit evidence including actor, reason, original dead event, new event and operation identity;
- persist the idempotency fingerprint.

The original dead event remains `DEAD` as historical evidence. Redrive creates a new delivery event rather than mutating or erasing the failed attempt. Repeating the same idempotency key and payload returns the original redrive result; reusing the key with a different payload returns conflict. Concurrent redrives serialize on the tenant installation row, so only one can transition a failed installation.

## 3. Stable provisioning operation identity

M8 adds `operation_id` to outbox records. Existing rows are backfilled from their event IDs. A retry event has its own event ID and transition revision but carries forward the original provisioning operation ID. The dispatcher passes that stable operation ID to the host provisioning port.

This distinction is important:
- event ID identifies one durable delivery/transition record;
- operation ID identifies the idempotent host-side provisioning operation across retries and redrives.

The host provisioner must enforce idempotency for this operation ID. Redrive does not promise exactly-once delivery.

## 4. Tenant isolation and audit

- All operational reads and redrive writes set the transaction-local tenant RLS context and include explicit tenant predicates.
- The adapter does not expose cross-tenant/global dead-letter scans.
- Redrive audit rows are protected by forced tenant RLS and append-only update/delete/truncate triggers.
- Authorized operators can inspect the immutable redrive audit entries, including actor, reason, original dead event, retry event and stable operation identity.
- Raw idempotency keys are not stored; the caller supplies only a lowercase SHA-256 hash.
- Failure codes remain stable machine-readable codes; exception messages are not persisted.

## 5. Verification

The PostgreSQL Testcontainers test covers:
- dead-letter eligibility and redrive;
- transition history and revision update;
- exact idempotent replay and conflicting key reuse;
- stable operation ID preservation from original dead event to retry event;
- tenant isolation for history and dead-letter reads.

Application-layer tests prove authorization denial occurs before persistence reads or writes. The latest focused Java 21 workflow (run #117) fails in its shared plugin setup before reaching the Application Platform tests because Maven cannot resolve the unrelated `io.surveyhub:survey-platform:0.1.0-SNAPSHOT` parent for `survey/survey-adapter-rest`. Therefore the M8 tests have not yet been executed by CI; this run is not a pass/fail result for the M8 implementation itself. Broader repository CI remains a separate signal.

## 6. Explicit non-goals

- No built-in global administrator or cross-tenant operator role.
- No public HTTP API, admin UI, or operator identity store.
- No automatic bypass of package signature/digest validation, tenant capability compatibility checks, or host provisioning.
- No mutation/deletion of the original dead event or immutable transition history.
- No replacement workflow engine; Gamelan remains responsible for long-running approval and compensation workflows.

## 7. Host integration contract

A host that exposes these operations must:
1. resolve the authenticated actor and active tenant from trusted security context;
2. implement authorization for inspect and redrive actions;
3. hash client idempotency keys before constructing the command;
4. provide a provisioner that treats `operation_id` as its durable idempotency key;
5. expose only tenant-scoped API endpoints and apply rate limits/operator audit monitoring at the edge.
