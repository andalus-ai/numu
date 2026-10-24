# Numou Omnichannel — M7: Retry and Processing Lifecycle

## Scope

M7 extends the PostgreSQL inbox with durable attempt counts, retry scheduling, worker leases, atomic claims, and dead-letter queries. It does not add provider-specific webhook handlers or dispatch business commands directly.

## Worker lifecycle

1. Authenticate the provider request and authorize the resolved tenant/account before claiming.
2. Persist the event using `InboundEventInbox`; only an `ACCEPTED` claim can enter processing.
3. Verify the event and move its state to `VERIFIED` using the application integration that owns verification.
4. A worker claims one eligible event using `FOR UPDATE SKIP LOCKED`. The claim changes state to `PROCESSING`, increments attempts, and sets a lease.
5. On success, compare-and-set `PROCESSING` to `PROCESSED`.
6. On a retryable failure, provide a sanitized error code and a retry instant. The adapter schedules `RETRYABLE_FAILURE` or moves the event to `DEAD_LETTER` after the attempt budget is exhausted.
7. Expired processing leases can be reclaimed after worker failure. Because a worker may crash after a downstream side effect, downstream commands must remain idempotent.

## Operational guarantees and limits

- PostgreSQL row locks and `SKIP LOCKED` allow multiple workers to claim different events without intentionally claiming the same live row concurrently.
- Attempt limits are supplied by the caller and must be configured consistently by all workers.
- Error details are stored as bounded, sanitized error codes, not exception messages or raw payloads.
- `findDeadLetters` is read-only. This milestone deliberately does not provide replay, because replay requires operator authorization, audit records, and a defined re-verification path.
- The work item contains metadata and the payload digest only. A secure payload store/retention strategy and worker-to-domain dispatch integration remain separate work.
- This is at-least-once processing with idempotency requirements, not end-to-end exactly-once delivery.

## Migration and validation

Apply `V2__add_inbound_event_retry_lifecycle.sql` after V1. Maven and PostgreSQL integration/concurrency tests have not been run in this environment. Before production, verify multi-worker claiming, lease expiry, max-attempt behavior, stale worker completion, schema migration on supported PostgreSQL versions, and transaction semantics.
