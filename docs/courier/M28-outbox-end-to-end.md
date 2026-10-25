# M28 — End-to-end courier outbox delivery verification

M28 adds a Testcontainers-backed PostgreSQL test that drives the real `JdbcCourierOutboxPublisher` and `CourierOutboxOperations` against the complete Flyway migration chain.

## Covered behavior

- Creates a real outbox event and verifies its envelope reaches a fake transport and the publication row is acknowledged only after the transport returns.
- Exercises a transport failure, checks the durable retry state, then makes the row available and verifies a subsequent delivery succeeds.
- Models the ambiguous failure window where the destination may have accepted an event but the relay did not record acknowledgement. The retry receives the same event ID, demonstrating the at-least-once contract and the required downstream idempotency key (`tenantId + eventId`).
- Recovers a row with an expired worker lease.
- Exercises authorized dead-letter replay through the operations service, checking state reset, replay metadata, and audit insertion; rejects an unauthorized actor.

The tests use a fake transport, not a production Kafka/AMQP adapter. They validate the relay and database lifecycle, not broker-specific durability or consumer-side deduplication.

## Run

From the repository root:

```bash
mvn -pl logistics/numou-courier-postgres-adapter -am test
```

Docker must be available for Testcontainers to execute the PostgreSQL integration tests. The M28 test class ends in `Test`, so it is included by Maven Surefire's default test naming patterns. The earlier M27 `*IT` class may require explicit Failsafe configuration or renaming to be run by a default `mvn test`; do not count it as executed unless the build output confirms it.

## Release gates still outstanding

- Run the tests in CI with Docker enabled and retain the build result.
- Implement and test a deployment-specific `CourierEventPublisher` transport adapter.
- Verify consumer idempotency using `tenantId + eventId`.
- Exercise production identity/authentication wiring for `CourierOutboxOperationsAuthorizer`; the integration test supplies an explicit test authorizer.
- Observe retry/dead-letter behavior under load and test broker outages against the chosen transport.
