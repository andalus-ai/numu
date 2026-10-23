# Supply Chain M20 — bounded recovery retry policy and terminal state

## Goal

Provide an explicit bounded retry decision for warehouse recovery and a terminal state that stops exhausted work from returning to the normal recovery queue.

## Included

- `WarehouseRecoveryRetryPolicy` calculates capped exponential backoff from the failure timestamp.
- Retry decisions are explicit: either a next-eligible timestamp, or a terminal reason when the configured attempt budget is exhausted.
- Policy configuration validates positive limits, valid durations, and a positive multiplier.
- `WarehouseExecutionAttemptStatus.DEAD_LETTER` represents terminal recovery work.
- `WarehouseExecutionAttempt.deadLetter(reason)` requires a reconciliation state and a non-empty reason; a dead-lettered attempt cannot be retried through the aggregate.
- Existing JDBC recovery selection only includes `PREPARED`, `INVENTORY_POSTED`, and `NEEDS_RECONCILIATION`, so `DEAD_LETTER` records are not claimed by the current adapter.
- Unit tests cover capped backoff, exhaustion, invalid policy values, and terminal transition rules.

## Retry contract

The initial prepared attempt has `attemptCount = 1`. After a failed attempt, call `decide(attemptCount, failureTime)`. If another attempt is allowed, the caller must defer it until `nextEligibleAt`. If exhausted, transition the reconciliation attempt to `DEAD_LETTER` with a useful reason.

## Important limitation

This milestone introduces the decision policy and terminal domain state, but it does **not yet persist `nextEligibleAt` in the recovery journal**. The current JDBC claim query does not enforce backoff eligibility, so callers must not claim this milestone alone as durable retry scheduling. M21 should add a database migration for the retry timestamp, atomically persist retry decisions, filter claims by eligibility, and expose a query for exhausted/dead-letter records. Until then, this is a domain/application foundation rather than a complete production retry scheduler.

## Validation

- Added unit tests, but Maven was not run in this session.
- CI must validate Java compilation and tests.
- Before enabling automated retry in production, M21 must implement durable eligibility and integration tests against PostgreSQL.
