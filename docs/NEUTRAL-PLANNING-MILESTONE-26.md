# Neutral Planning — Milestone 26: Atomic Schedule Change Application

## Goal

Connect approved schedule-change governance to an application use case without pretending that an approval or an in-memory state transition has persisted a plan change.

## Delivered

- `ApplyApprovedScheduleChange` validates approval state, plan identity, and the materialized target revision.
- `ScheduleChangeExecutionStore` defines the required transaction boundary for plan mutation plus execution receipt.
- The application checks for an existing execution receipt first so retries can return the original result even after the plan advances.
- Reusing an execution ID for a different request or scope is rejected.
- The store contract requires an atomic base-revision compare-and-set, durable idempotency, tenant/structure ownership validation, and joint persistence of the target plan and receipt.
- A stale base revision must produce a durable conflict result without modifying the plan.

## Adapter requirements

The store implementation must use a single database transaction (or an equivalent consistency protocol) to:
1. Read and lock/compare the current plan revision against `request.baseRevision()`.
2. Confirm that the target plan belongs to the same tenant, structure, and plan identity.
3. Persist the fully materialized target plan at `request.targetRevision()`.
4. Persist the terminal execution receipt under a unique execution ID.
5. On retries, return the stored receipt only if the request/target payload matches; reject conflicting idempotency-key reuse.
6. Return conflict without writing the target plan if the base revision has advanced.

The existing `PlanRepository` is a read/ordinary-save port and does not itself guarantee compare-and-set semantics. This milestone deliberately introduces a separate atomic store contract rather than incorrectly treating two ordinary repository calls as a transaction.

## Verification

Application tests cover successful application, idempotent replay, target identity/revision validation, and conflicting execution-ID reuse. Tests were authored remotely and have not been executed in this session; no passing CI claim is made.
