# PostgreSQL outbox relay concurrency and recovery test

Run `./scripts/postgres-outbox-recovery-test.sh`. It starts a disposable PostgreSQL 16 container, applies the bundled migrations, and checks:

- a worker holding a claimed row lock does not prevent another worker from claiming a different row (`FOR UPDATE SKIP LOCKED`);
- an expired `PUBLISHING` lease can be reclaimed by a new worker;
- the old lease token cannot acknowledge an event after reclamation, while the current token can;
- terminal failed events are excluded from claim selection.

The test only validates PostgreSQL-level claim/fencing behavior using SQL shaped like `PgOutboxRelayStore`. It does not invoke the Java adapter, run a broker, or establish end-to-end delivery guarantees. Delivery remains at-least-once; consumers should be idempotent. The script does not connect to an existing database.

Requires Docker. Optional port override: `SYIRKAH_TEST_PG_PORT` (default `55441`).
