# Supply Chain M21 — durable retry scheduling and recovery operations

## Goal

Make retry eligibility durable and enforce it at the database claim boundary, rather than relying on every worker to remember an in-memory backoff decision.

## Included

- Adds nullable `next_eligible_at` to the warehouse execution recovery journal. Existing rows remain immediately eligible after migration.
- Adds an application-port operation to persist a retry time with an expected-version check.
- Retry scheduling is accepted only for `NEEDS_RECONCILIATION` records that are not currently held by a live claim. It increments the persisted version and updates the timestamp.
- Both bounded recovery scans and atomic PostgreSQL claim selection exclude attempts whose retry time is in the future.
- Adds a bounded oldest-first dead-letter query for operational review.
- Adds indexes for retry eligibility and terminal dead-letter inspection.

## Operational contract

1. A recovery worker reports a failed attempt and obtains a `WarehouseRecoveryRetryPolicy.RetryDecision`.
2. If retry is allowed, persist its `nextEligibleAt` through `scheduleRetry` using the version observed by the worker.
3. If the attempt budget is exhausted, transition the attempt to `DEAD_LETTER`; inspect it with `findDeadLetters`.
4. Claims remain lease-fenced; the database eligibility predicate is authoritative, not the worker's local clock.

The retry timestamp is deliberately nullable to support existing rows and a safe migration rollout. A worker should schedule only after it has released or lost its live claim; the adapter rejects scheduling while a live claim remains.

## Validation and remaining gates

- Maven and PostgreSQL integration tests have not been run in this editing session.
- CI should run warehouse application and JDBC module tests.
- PostgreSQL integration coverage should verify: future-dated rows cannot be claimed, rows become claimable after eligibility, concurrent workers cannot double-claim, stale versions cannot reschedule, live claims cannot be rescheduled, and dead letters are queryable but never claimable.
- This milestone persists eligibility and exposes terminal records; it does not yet add a complete operational dashboard, metrics exporter, or automatic worker orchestration.
