# M29 — Opt-in HTTP transport for courier outbox

M29 adds `HttpCourierEventPublisher`, a concrete Java HTTP transport implementing the existing `CourierEventPublisher` port. It posts the immutable event envelope to a configured event gateway and returns only for a 2xx response. Non-2xx responses and transport errors propagate to the outbox relay so its durable retry policy can take over.

## Configuration

The bean is opt-in at build time. Configure these properties in the deployment's Quarkus configuration:

```properties
# Build-time switch; set to true in the deployment build profile.
numou.courier.outbox.http.enabled=true

# Runtime destination endpoint. Use HTTPS in production.
numou.courier.outbox.http.url=${COURIER_EVENT_GATEWAY_URL}
numou.courier.outbox.http.destination=default
numou.courier.outbox.http.bearer-token=${COURIER_EVENT_GATEWAY_TOKEN}
numou.courier.outbox.http.connect-timeout-ms=2000
numou.courier.outbox.http.request-timeout-ms=5000

# Existing relay properties should target the same destination.
numou.courier.outbox.destination=default
numou.courier.outbox.interval=5s
numou.courier.outbox.batch-size=100
```

Supply the token through a secret manager or environment-backed secret; do not commit credentials. The publisher validates the endpoint scheme, host, credentials, and fragment; disables redirects; bounds connection/request timeouts; and refuses to publish to a destination different from its configured destination. The HTTP adapter is a single-destination adapter, matching the current worker's single configured destination.

## Wire contract

Each POST has a JSON envelope with `tenantId`, `eventId`, `eventType`, `aggregateType`, `aggregateReference`, `occurredAt`, and the original JSON `payload`. Headers include:

- `Idempotency-Key: <tenantId>:<eventId>`
- `X-Numou-Tenant-Id`
- `X-Numou-Event-Id`
- `X-Numou-Event-Type`
- `Authorization: Bearer ...` when a token is configured

The receiver must persistently deduplicate on the idempotency key. HTTP timeouts can occur after the receiver has accepted a request; the relay will retry, so exactly-once delivery is not claimed. The endpoint must authenticate the caller and validate the tenant/event envelope independently.

## Validation

`HttpCourierEventPublisherTest` uses a local JDK HTTP server to verify envelope serialization, stable idempotency headers, bearer authentication, destination guard, endpoint validation, and retry-triggering behavior for a 503 response.

Run:

```bash
mvn -pl logistics/numou-courier-postgres-adapter -am test
```

Tests were authored but not run during this change. Before production rollout, run CI and perform a deployment-level test against the actual gateway, including authentication/authorization, TLS, timeout ambiguity, consumer deduplication, and gateway outage recovery.
