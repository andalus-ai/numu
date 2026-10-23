# Supply Chain M17 — Concurrent recovery claim isolation

## Goal

Add an opt-in PostgreSQL integration test that exercises recovery claiming from two independent worker threads and JDBC connections at the same time. This closes an important gap between the earlier single-worker lease test and production multi-worker deployments.

## Coverage

- Creates eight uniquely keyed recovery attempts under a unique test tenant.
- Starts two recovery workers behind a latch so their claim calls race rather than run sequentially.
- Uses the real JDBC repository and PostgreSQL `FOR UPDATE SKIP LOCKED` claim path.
- Asserts no target attempt is returned to both workers and that every inserted target attempt is claimed by one worker.
- Deletes test-owned rows in a `finally` block.

## Run

Configure `NUMOU_TEST_POSTGRES_URL`, optionally `NUMOU_TEST_POSTGRES_USER` and `NUMOU_TEST_POSTGRES_PASSWORD`, and apply warehouse migrations V1–V4 to a disposable PostgreSQL database. Run the `numou-warehouse-persistence-jdbc` module tests from the repository Maven reactor.

The test is opt-in and is skipped when the URL is unset. It requires PostgreSQL; it is not a substitute for a provisioned CI service, repeated stress testing, or process-level testing.

## Reliability boundary

This test checks claim exclusivity for concurrent calls to the JDBC adapter. It does not prove end-to-end exactly-once execution, because a worker may crash after an external side effect and before acknowledging/releasing its lease. Recovery handlers must continue to reuse durable idempotency keys. A lease expiring does not prove the previous worker has stopped.

## Validation status

The test has been added but was not executed in this remote GitHub editing session. No passing Maven or PostgreSQL result is claimed. CI should provision a disposable PostgreSQL database, apply migrations V1–V4, and run the module test suite before merge.
