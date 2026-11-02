# M33 — Preserve mention delivery attempt history across replay

M31 introduced bounded retries and dead-letter delivery. M32 added authenticated, audited
operator requeue. The persistence implementation must preserve both contracts without making
a manually replayed delivery immediately terminal on its next failure.

- `attempts` is the lifetime number of claims/delivery attempts and is never reset by replay.
- `retry_attempts` counts claims in the current retry window and is reset only by an authorized
  dead-letter requeue.
- The dispatcher applies `maxAttempts` to `retry_attempts`, not lifetime attempts.
- Dead-letter inspection and the requeue audit retain the lifetime attempt count.
- Migration V27 initializes `retry_attempts` from existing `attempts`, preserving the current
  retry budget for rows already in flight or dead-lettered.
- Delivery remains at-least-once; the stable downstream idempotency key is unchanged.

Verification must include migration upgrade from V26, failed delivery to dead-letter, replay,
a fresh retry budget, monotonically increasing lifetime attempts, and tenant-scoped audited
requeue. PostgreSQL integration tests require `NUMOU_WORKSPACE_TEST_JDBC_URL`.
