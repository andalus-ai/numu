# M7 — Application Installation Terminal Failure Reconciliation

- **Status:** implementation branch; focused verification pending.
- **Depends on:** M5 PostgreSQL installation/outbox store (PR #1656) and M6 tenant provisioning worker (PR #1659).
- **Owner:** optional `application-platform` capability.
- **Persistence:** existing PostgreSQL adapter and outbox schema; no new table or migration.

## 1. Problem

M6 records retry exhaustion by moving the outbox event to `DEAD`, then asks the application dispatcher to transition the installation aggregate to `FAILED`. Those writes occur in separate transactions. A process crash, database outage, or revision conflict between the two operations can leave a terminally dead provisioning request with an installation still showing `PROVISIONING`.

That state is operationally ambiguous: the worker will no longer claim the dead event, while the aggregate appears to be in progress.

## 2. Atomic terminal transition

The PostgreSQL adapter now owns the consistency boundary when retry exhaustion is reached:

1. Start one transaction and set the trusted tenant RLS context.
2. Lock the outbox row using tenant, event, worker, lease token and unexpired lease predicates.
3. Determine terminality from the persisted claim attempt count and configured maximum.
4. Change the original delivery row to `DEAD`, clear its lease, and persist the stable failure code.
5. For an installation still in `REQUESTED`, `VALIDATING`, `AWAITING_APPROVAL` or `PROVISIONING`, transition its aggregate to `FAILED` using a revision increment.
6. Append the immutable `FAILED` transition and its status-change outbox event.
7. Commit all changes together.

If any statement fails, the transaction rolls back both the dead-letter update and aggregate transition. If the aggregate is already terminal or successfully installed, it is not overwritten. The existing worker-side failure transition remains safe as an idempotent fallback for adapters that do not provide this stronger transaction boundary.

The new status-change event is not a provisioning request and is acknowledged by the M6 dispatcher without recursively provisioning.

## 3. Tenant and security guarantees

- The adapter uses the same trusted tenant context and explicit tenant predicates as the existing installation store.
- The original event must still be owned by the current, unexpired lease token.
- No operator identity, tenant ID, or permission is inferred from an event payload.
- Only stable failure codes are persisted; exception messages remain out of the database.
- The original outbox row retains its event identity and terminal failure evidence.

## 4. Verification

The PostgreSQL Testcontainers test extends retry-exhaustion coverage to assert that the installation becomes `FAILED`, its stable failure code is recorded, and exactly one immutable failure transition plus its status-change outbox event are committed.

The focused Application Platform Java 21 workflow is the acceptance gate for this milestone. Broader repository CI remains a separate signal and must not be inferred from the focused workflow.

## 5. Explicit non-goals

- No public HTTP routes or operator UI; the host remains responsible for authenticated API composition.
- No dead-letter redrive yet. Redrive needs its own tenant-authorized command, actor/reason audit, idempotency record, and lease-safe requeue transaction; it must not be implemented as a generic status update.
- No cross-tenant operator privilege or global tenant scan.
- No replacement workflow engine; Gamelan remains the orchestration engine for long-running approval and compensation processes.

## 6. Next operations increment

Add tenant-authorized installation history and dead-letter inspection, then audited redrive with a stable idempotency key. The redrive transaction must append an immutable operator audit record and requeue the same durable operation identity without bypassing the host provisioning checks.
