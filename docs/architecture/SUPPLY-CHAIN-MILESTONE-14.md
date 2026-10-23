# Supply Chain M14 — PostgreSQL recovery integration contract

## Goal

Add an opt-in real-PostgreSQL test for the durable warehouse recovery journal. Unit tests and SQL contract checks alone cannot prove payload serialization and PostgreSQL row-locking behavior.

## Integration test

WarehouseRecoveryPostgresIntegrationTest is enabled only when NUMOU_TEST_POSTGRES_URL is set. It uses optional NUMOU_TEST_POSTGRES_USER and NUMOU_TEST_POSTGRES_PASSWORD credentials, and expects migrations V1 through V4 to have been applied.

The test verifies:

- a typed receiving payload survives JDBC insert/read with exact decimal scale;
- the first worker can claim the new recoverable entry;
- a competing worker cannot claim the same entry while its lease is live;
- the test removes its own journal row in a finally block.

Use a dedicated disposable PostgreSQL database with the warehouse recovery migrations applied. The test deliberately checks that no other unclaimed recovery entries exist, because the repository claims the oldest bounded batch and unrelated rows would make the concurrency assertion ambiguous.

## Run

Export the connection settings in the test environment, then run the numou-warehouse-persistence-jdbc module's test suite from the repository's Maven reactor. The PostgreSQL JDBC driver must be present on the test runtime classpath; the module does not force a database driver on every consumer.

Without NUMOU_TEST_POSTGRES_URL, JUnit marks the integration test as skipped. A skipped test is not evidence of PostgreSQL compatibility.

## Remaining verification

This test covers payload round-trip and live-lease exclusion, not the full inventory/warehouse crash matrix. Production readiness still requires CI to provision PostgreSQL, apply migrations V1–V4, execute this test, and exercise crash windows with idempotent inventory and warehouse adapters. The distributed workflow remains at-least-once, not a distributed transaction.
