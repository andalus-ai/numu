# M31 — Courier outbox operations and dead-letter recovery

This milestone hardens the operational contract of the PostgreSQL-backed courier outbox. It adds regression coverage for retry exhaustion and for the crash window where a worker's final permitted attempt expires before it can persist an outcome.

## Delivery guarantees and terminal states

- The relay is **at-least-once**, not exactly-once. A destination can accept an event while the relay fails before persisting `PUBLISHED`; the same event may be sent again.
- Receivers must durably deduplicate on the stable `tenantId:eventId` idempotency identity. A successful HTTP response should mean the receiver has durably accepted the event.
- Transient failures remain `PENDING` with exponential backoff, capped at one hour. The relay currently allows 12 attempts.
- A failed 12th attempt moves the publication to `DEAD`. If the process crashes during its 12th attempt, an expired lease is reaped to `DEAD` on a later claim cycle rather than remaining permanently unclaimable.
- `PUBLISHED` means the configured transport returned successfully and the relay persisted its acknowledgement; it does not prove downstream business processing completed.

## First-response checklist

1. Confirm the destination name matches the configured outbox destination and that the HTTP endpoint is reachable from the application network.
2. Inspect publication status, attempt count, last error, and lease timestamps for the affected destination. Use tenant/event identifiers only in controlled operational queries; never export secrets or payloads into logs or tickets unnecessarily.
3. For `PENDING`, check whether `available_at` is still in the future. Backoff is expected after transport failures; do not reset it automatically during an incident.
4. For `DEAD`, inspect the recorded error and gateway-side idempotency/delivery records before replaying. The last attempt can have an ambiguous remote outcome.
5. Fix the underlying cause before replay (endpoint, authentication, gateway availability, or incompatible event contract). Replay only through the authorized `CourierOutboxOperations` API with an authenticated actor and a meaningful reason; the operation records an audit entry.
6. Verify the replay returns to `PENDING`, that the audit row identifies the actor/reason, and that the destination deduplicates any previously accepted event.
7. Confirm the publication reaches `PUBLISHED`; separately verify downstream processing if the gateway exposes a durable receipt or status endpoint.

## HTTP transport configuration

The HTTP publisher is opt-in and must be enabled at build time:

```properties
numou.courier.outbox.http.enabled=true
numou.courier.outbox.http.url=https://gateway.example.internal/events
numou.courier.outbox.http.destination=default
numou.courier.outbox.http.connect-timeout-ms=2000
numou.courier.outbox.http.request-timeout-ms=5000
# Supply through a secret/configuration provider, not a committed file:
numou.courier.outbox.http.bearer-token=${COURIER_GATEWAY_BEARER_TOKEN}
```

Keep `numou.courier.outbox.destination` aligned with the HTTP publisher destination. Use HTTPS outside local development, rotate the bearer token through your secret manager, and do not include credentials in endpoint URLs. The sender disables redirects; configure the final gateway URL directly. A non-2xx response is treated as a delivery failure and retried.

## Database checks

Adapt the predicates to the incident's destination and tenant/event identifiers, and execute them with least-privilege operational access.

```sql
-- Aggregate backlog and terminal failures by destination.
SELECT destination, status, count(*) AS publications,
       min(available_at) AS earliest_available,
       max(attempt_count) AS max_attempts
FROM courier_event_publications
GROUP BY destination, status
ORDER BY destination, status;

-- Inspect a specific publication without selecting its payload.
SELECT tenant_id, event_id, destination, status, attempt_count,
       available_at, lease_owner, lease_until, published_at, last_error,
       replay_count, last_replayed_by, updated_at
FROM courier_event_publications
WHERE destination = :destination
  AND tenant_id = :tenant_id
  AND event_id = :event_id;

-- Confirm the replay audit trail for a specific publication.
SELECT actor_reference, reason, action, occurred_at
FROM courier_event_publication_audit
WHERE destination = :destination
  AND tenant_id = :tenant_id
  AND event_id = :event_id
ORDER BY occurred_at DESC;
```

Column names in operational queries should be checked against the deployed migration version before running them. Do not manually change status or reset attempt counters in production; use the authorized replay operation so that authorization and audit requirements remain intact.

## Verification

The PostgreSQL Testcontainers suite now explicitly checks:

- all 12 failed transport attempts transition the publication to `DEAD`;
- a crashed worker's expired lease at the retry limit is reaped without an additional transport send;
- existing success, retry, at-least-once redelivery, lease recovery, and authorized replay behavior.

Run from the repository root:

```bash
mvn -pl logistics/numou-courier-postgres-adapter -am test
```

Docker must be available. These tests were authored in this milestone but have not been executed in this session. Check the pull request's GitHub Actions results before treating them as verified.

## Operational gaps not claimed as solved

This milestone does not add metrics/alerts or prove the external gateway's durability, TLS configuration, or downstream deduplication. Production rollout should add alerting for sustained `PENDING` backlog and new `DEAD` rows, and should verify the receiver's durable idempotency behavior with its own integration tests.
