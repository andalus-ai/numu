# BOQ revision workflow protocol test

Run from the bundle root:

```bash
./scripts/postgres-boq-workflow-test.sh
```

Requires Docker. The script starts a disposable PostgreSQL 16 container, applies all bundled migrations, and validates the database protocol for capturing a draft snapshot, submitting it for review, approving it, writing one tenant-tagged outbox event per transition, rejecting stale status transitions, and rolling back snapshot/outbox changes together.

The source dump does not establish the physical live BOQ table mapping. To avoid inventing that mapping, the script creates a **test-only** `test_live_boq` table and exercises SQL operations equivalent to the intended workflow. It does not invoke `DefaultPgBoqRevisionOperationsFactory`, `PgLiveBoqRevisionStore`, Mutiny, the transaction runner, or a message broker. It is a database protocol test, not end-to-end proof of the Java implementation.

Set `NUMOU_TEST_PG_PORT` to override the host port (default `55442`) if it is occupied. The script removes its disposable container on exit and does not connect to an existing application database.
