# Supply-chain milestone 39: recovery export claim-path indexes

## Goal

Keep the PostgreSQL worker-claim query predictable as recovery-audit export history grows, without changing the queue's ordering or lease semantics.

## Changes

- Add a partial index for `REQUESTED` and `VERIFYING` jobs ordered by creation time and export ID.
- Add a partial index for `RUNNING` jobs keyed by lease expiry and the deterministic tie-break ordering.
- Make the claim predicate explicitly group pending/verification jobs separately from expired running jobs. This documents the intended OR semantics and avoids future edits accidentally changing the expiry guard.

## Correctness constraints

- The claim remains a single transaction using `FOR UPDATE SKIP LOCKED`, followed by a version-guarded update and commit.
- Only `RUNNING` rows require `lease_until <= now` to be reclaimable; `REQUESTED` and `VERIFYING` rows are immediately claimable.
- The indexes are performance aids, not a correctness mechanism. The row lock and optimistic version check remain required.
- Index selection and benefit must be verified against representative PostgreSQL data with `EXPLAIN (ANALYZE, BUFFERS)`; planner choice depends on queue distribution and table statistics.

## Validation status

Maven tests and PostgreSQL query-plan checks were not run in this environment. CI should run the warehouse reactor and apply Flyway migrations through V9 against the supported PostgreSQL version. Before production rollout, compare the claim query plan and write overhead with representative queue volume.
