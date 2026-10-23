# Neutral Planning — Milestone 25: Schedule Change Execution and Reconciliation

## Goal

Provide a domain-level execution receipt for an approved schedule change. Approval is not execution: the plan may fail to persist, another writer may advance the plan, or a caller may retry after a timeout.

## Delivered

- An immutable `ScheduleChangeExecution` lifecycle: `APPLYING`, `APPLIED`, `FAILED`, and `CONFLICT`.
- Execution can begin only from an approved request with explicit approval evidence and while the live revision equals the approved base revision.
- Reconciliation accepts only snapshots for the same tenant, structure, and plan.
- The exact target revision is recorded as applied; an unexpected revision is recorded as a conflict.
- A still-visible base revision is treated as in-progress, not falsely reported as success.
- Failure and conflict outcomes preserve a completion timestamp and reason.
- Replaying reconciliation of an already-applied execution against the same target revision is idempotent.

## Required application-adapter contract

This milestone is a domain protocol, not a database transaction or a plan mutation adapter. Production integration must:

1. Persist the plan revision and execution receipt atomically where possible.
2. Use compare-and-set/optimistic locking on the base revision before applying the approved plan.
3. Use a durable idempotency key based on execution ID for retries.
4. Reconcile uncertain outcomes by reading the persisted plan snapshot before retrying a write.
5. Authorize the initiator and enforce tenant scope at the application boundary.
6. Publish integration events through an outbox or equivalent transactional mechanism.

The aggregate deliberately does not invent storage, authorization, or event infrastructure that is not supplied by the planning domain module.

## Verification

JUnit tests cover approval gating, base-revision checks, target reconciliation, replay, foreign-scope rejection, unexpected revisions, and failure recording. Tests were authored through repository editing; they have not been executed in this session. No passing CI claim is made.
