# Supply Chain M15 — Crash-window recovery tests

## Goal

Exercise failure windows between inventory posting, warehouse aggregate/receipt commit, and recovery-journal acknowledgement. These are deterministic application-level failure-injection tests using in-memory adapters; they complement, but do not replace, PostgreSQL integration and process-restart tests.

## Scenarios

1. **Inventory posts, acknowledgement is lost.** The fake inventory adapter records the movement under the tenant-scoped command key and throws before the coordinator can advance the journal. Recovery retries the exact payload and command key. The test asserts the physical movement count remains one and warehouse quantity is executed once.
2. **Warehouse aggregate/receipt commits, journal acknowledgement fails.** The fake journal repository rejects the first WAREHOUSE_COMMITTED transition after warehouse execution has committed. The coordinator records reconciliation state; recovery replays the inventory idempotency key and warehouse receipt. The test asserts one inventory movement, one new warehouse commit, and final journal status WAREHOUSE_COMMITTED.

## What this proves

The tests verify that the application coordinator passes the same tenant-scoped command key and persisted payload through retry, and that the fake inventory/warehouse adapters replay idempotently in these specific failure windows.

## What this does not prove

- It does not prove PostgreSQL transaction, row-lock, lease-expiry, or migration behavior.
- It does not spawn a separate process or restart a real application.
- It does not prove a third-party inventory adapter implements durable idempotency correctly.
- It does not create a distributed transaction across inventory, journal, and warehouse state.

Run the warehouse application module's Maven tests, and run the opt-in M14 PostgreSQL integration test against a disposable database with migrations V1–V4 applied. No test result is claimed until those commands run in CI or a configured environment.
