# PostgreSQL outbox relay adapter

## Included

`PgOutboxRelayStore` is a Quarkus/Vert.x Mutiny `PgPool` adapter following the source dump's existing PostgreSQL adapter pattern. It implements the `OutboxRelayStore` SPI with:

- one atomic `UPDATE ... FROM (SELECT ... FOR UPDATE SKIP LOCKED)` claim statement;
- random per-claim lease tokens;
- lease-token-fenced success and failure updates;
- reclaim of expired `PUBLISHING` leases;
- terminal failure state excluded from future claims;
- delivery-attempt count incremented at claim time, so a worker crash still consumes an attempt.

The relay must publish outside the claim statement/transaction. The adapter deliberately does not keep a database transaction open during broker I/O.

## Attempt semantics

`attempts` counts delivery attempts started, not only broker failures. A claimed event carries the incremented count. On failure, the service decides whether the attempt is terminal and supplies a retry timestamp; the store records that decision. If a worker dies after claim, the expired lease can be reclaimed and the attempt count has already advanced.

## Operational requirements

- Run only as a narrowly scoped background-service database role. The relay is cross-tenant by design; request handlers must not call it with end-user credentials.
- Ensure the broker adapter publishes the event ID as an idempotency/deduplication key. The system provides at-least-once delivery.
- Alert on terminal rows (`terminal = true`), old pending rows, repeated expired leases, and claim/publish latency.
- Keep lease duration longer than ordinary broker latency, or add lease renewal for long-running delivery.
- Avoid logging raw payloads if they can contain project, contract, or personal data.

## Required PostgreSQL integration tests

Run against the same PostgreSQL major version and Vert.x client used by the real application:

1. Insert 100 pending rows; launch two concurrent workers and assert each event is returned to at most one worker per lease.
2. Claim an event, replace its lease token in a controlled test, and assert old-token success/failure acknowledgements return `false`.
3. Expire a `PUBLISHING` lease and assert another worker can reclaim it with a different token.
4. Mark an event terminal and assert no future claim returns it.
5. Fail an event, assert attempts is incremented once at claim, and verify `available_at` prevents claim before retry time.
6. Simulate worker death after claim; after lease expiry, assert the row is reclaimed and the attempt count has advanced.
7. Verify JSONB payload round-trips without losing its JSON representation.

## Integration caveats

This bundle is a source-dump-based implementation slice, not a repository checkout. Verify module dependencies, package/module ownership, migration discovery, database grants, RLS policies, and PostgreSQL extension/function availability in the real checkout. No full Maven or live PostgreSQL integration test is claimed here.
