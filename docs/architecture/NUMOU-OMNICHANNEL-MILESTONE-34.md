# Numou Omnichannel — Milestone 34: Replay Store Operations and Concurrency Tests

## Objective

Complete the operational side of durable replay protection by bounding expiry cleanup and testing PostgreSQL concurrency and reservation expiry semantics.

## Delivered

- Added `PostgresWebhookReplayStore.deleteExpired(cutoff, batchSize)`, a bounded cleanup operation with a maximum batch size of 10,000 rows.
- Cleanup orders expired rows by expiry and deletes only a bounded batch, suitable for scheduled maintenance.
- Added opt-in PostgreSQL integration tests using an isolated schema and the existing `NUMOU_TEST_POSTGRES_URL` convention.
- Integration tests cover concurrent reservation (exactly one winner), expired-key reclaim, and cleanup that leaves unexpired reservations intact.

## Operations

Schedule `deleteExpired` at a controlled cadence and tune the batch size to database load. Repeat bounded batches until the deleted count is below the requested batch size, while avoiding long-running transactions. Monitor table size, cleanup duration, database errors, and replay-protection rejection counts.

The PostgreSQL integration tests are skipped unless `NUMOU_TEST_POSTGRES_URL` is configured, with optional `NUMOU_TEST_POSTGRES_USER` and `NUMOU_TEST_POSTGRES_PASSWORD`. Use a disposable database; tests create and drop an isolated schema.

## Validation

Tests are authored but have not been run in this session. Verify against the supported PostgreSQL version and run the core and PostgreSQL adapter Maven tests before production rollout.
