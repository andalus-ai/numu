# Supply Chain M22 — recovery worker operations

## Goal

Expose safe worker operations around durable claims, retry deferral, and terminal recovery handling.

## Included

- Adds atomic repository operations to defer a claimed retry and clear its lease in the same database write.
- Adds atomic dead-letter transition for a claimed reconciliation attempt, fenced by tenant, command key, version, worker, exact lease deadline, and unexpired lease.
- Adds an application service for bounded claim acquisition, retry deferral, dead-lettering, explicit claim release, and bounded dead-letter inspection.
- Prevents the crash window where a worker releases a claim but crashes before persisting its retry eligibility.

## Worker contract

- Claim only a bounded batch with a bounded lease.
- Execute/reconcile using the same tenant-scoped command key and immutable payload.
- For retryable failures, call `defer(claim, nextEligibleAt)`; on success, discard the old claim.
- For exhausted or non-retryable reconciliation, call `deadLetter(claim, reason)`; on success, discard the old claim.
- If an atomic operation rejects a stale or expired claim, do not retry the write with a newer version blindly. Re-read and let the claim protocol determine ownership.

## Validation and limits

- Maven and PostgreSQL integration tests were not run in this editing session.
- The adapter requires the full recovery schema including claim, payload, retry eligibility, and dead-letter status columns/constraints.
- Add PostgreSQL tests for claim-fenced retry scheduling, stale/expired claim rejection, concurrent terminalization, and crash/reclaim behavior before production rollout.
- This milestone provides operational APIs, not a continuously running scheduler, executor pool, metrics exporter, or dashboard.
