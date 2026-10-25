# Numou Omnichannel — Milestone 31: Recover Incomplete Ingestion on Redelivery

## Problem

The inbox claim is durable before the raw payload is stored and the event is marked verified. If payload storage or the verification transition fails, the row can remain in `RECEIVED` without a payload reference. Recovery quarantines stale rows as `DEAD_LETTER` with `INGESTION_INCOMPLETE`. Previously, a provider redelivery with the same event key and payload digest was still treated as a duplicate, preventing the original ingestion path from retrying.

## Change

The PostgreSQL inbox adapter now permits a same-digest redelivery to reclaim a row only when all of these conditions hold:

- the row is `DEAD_LETTER`;
- the error code is exactly `INGESTION_INCOMPLETE`;
- no payload reference exists;
- the tenant, provider, account, event ID, and payload digest all match.

The conditional update atomically returns the row to `RECEIVED`, clears stale retry/lease/error fields, resets the attempt count, and records the new receipt time. Other dead-letter causes, digest conflicts, and ordinary duplicates retain their existing behavior.

## Validation

Added a PostgreSQL integration test for stale incomplete-ingress quarantine followed by same-payload redelivery. It runs when `NUMOU_TEST_POSTGRES_URL` is configured; without that environment variable, the integration test is skipped.

Suggested command:

```bash
mvn -pl commerce/omnichannel/numou-omnichannel-postgres -am test
```

The test was authored but not run in this environment.
