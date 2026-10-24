# Numou Omnichannel — Milestone 15: PostgreSQL reliability validation

## Goal

Exercise the durable inbox lifecycle against real PostgreSQL semantics, especially the atomic claim and lease-token fencing behavior that cannot be proven by mocks alone.

## Integration test suite

`numou-omnichannel-postgres` now includes `PostgresInboundEventReliabilityTest`, enabled when the following environment variables are supplied:

- `NUMOU_TEST_POSTGRES_URL` — JDBC URL for a disposable PostgreSQL database.
- `NUMOU_TEST_POSTGRES_USER` — optional database user.
- `NUMOU_TEST_POSTGRES_PASSWORD` — optional password.

Without the URL, JUnit skips the integration tests instead of failing ordinary builds. The test applies this module's V1–V3 migration resources and inserts rows with generated tenant IDs; it only asserts/reads those generated tenant rows. **Use a disposable database**, not a shared production or developer database.

Run from the repository root (or the Maven aggregator that includes `commerce/omnichannel`):

```bash
NUMOU_TEST_POSTGRES_URL='jdbc:postgresql://localhost:5432/numou_test' \
NUMOU_TEST_POSTGRES_USER='numou_test' \
NUMOU_TEST_POSTGRES_PASSWORD='replace-me' \
mvn -pl commerce/omnichannel/numou-omnichannel-postgres -am test
```

If the selected Maven aggregator is not the repository root, run the command from the actual aggregator POM that includes this module.

## Scenarios covered

1. Two concurrent claims race for one VERIFIED event; exactly one receives a lease.
2. An expired PROCESSING lease can be reclaimed with a new lease token, and the stale token cannot mark the event processed.
3. The recovery adapter quarantines stale RECEIVED rows with no payload reference while leaving fresh rows untouched; it records only the sanitized `INGESTION_INCOMPLETE` code.

## Operational contract

- Claiming uses PostgreSQL row locks and `SKIP LOCKED`; the unique event identity is tenant/provider/provider-account/external-event.
- Lease tokens fence stale workers from acknowledging or recording failure after a newer worker owns the lease.
- Incomplete ingestion is quarantined, not automatically dispatched, because there is no verified payload reference.
- This test suite does not prove end-to-end exactly-once business effects. Downstream domain use cases still need an atomic idempotency mechanism.

## Validation status

The tests are committed as opt-in integration tests. They have **not** been executed in this session because no configured PostgreSQL test database or successful CI run was available. Run the Maven command above and review the resulting test report before promoting the adapter to production.
