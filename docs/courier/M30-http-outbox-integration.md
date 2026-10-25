# M30 — HTTP gateway + PostgreSQL outbox integration

M30 adds a single integration test that exercises the full relay path against PostgreSQL and a real local HTTP server, using the production `JdbcCourierOutboxPublisher` and `HttpCourierEventPublisher` implementations.

## Assertions

- A 503 response does not acknowledge the event; its publication remains `PENDING` and the attempt counter increments.
- The retry sends the same tenant/event idempotency key.
- A subsequent 204 response is acknowledged as `PUBLISHED`.
- The request envelope contains the persisted tenant/event identity and original JSON payload, and the configured bearer token is sent in the Authorization header.

This tests HTTP-level integration without requiring a third-party gateway account. It does not prove the production gateway's authentication rules, TLS setup, durability, or downstream deduplication. The receiver must durably deduplicate by `tenantId:eventId`.

## Run

```bash
mvn -pl logistics/numou-courier-postgres-adapter -am test
```

Docker must be available for the PostgreSQL Testcontainers fixture. The test class ends in `Test` so Surefire's default naming patterns include it. The tests were authored but not executed in this session; CI results must be checked before calling this milestone verified.
