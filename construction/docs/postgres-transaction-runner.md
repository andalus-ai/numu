# PostgreSQL transaction runner integration

This slice adds `PgTransactionalCommandRunner` and `PgTransactionalCommandContext`.
The runner delegates transaction lifecycle to Vert.x Mutiny `PgPool.withTransaction`.
The context's idempotency SQL and the BOQ revision operations are bound to the exact
`SqlConnection` supplied by that transaction callback.

## Required companion implementation

`PgBoqRevisionOperationsFactory` is deliberately a port: the source dump does not
identify the physical BOQ and BOQ-item table names or their persistence mappings.
Implement this factory in the adapter module. Its `create(connection)` result must
use only that connection for all reads/writes, and must not call `PgPool` or open a
nested transaction. BOQ snapshot rows, BOQ state changes, outbox insertions, and the
idempotency response must all commit or roll back together.

## Transaction behavior

- A successful callback result commits; a failed `Uni` rolls back.
- The reservation row is inserted before business work and completed response is
  written before the callback completes.
- The unique `(tenant_id, idempotency_key)` constraint arbitrates concurrent callers.
  PostgreSQL may wait for the competing transaction to commit or roll back before
  `ON CONFLICT DO NOTHING` resolves.
- If a response cannot be persisted, the callback must fail so all business writes
  roll back.
- Do not return success until `withTransaction` has completed successfully.
- Do not make external broker calls inside this transaction. Insert an outbox row;
  publish it asynchronously after commit.

## Required live PostgreSQL tests

1. Two simultaneous same-key requests: one business execution and one replay.
2. Same key with a different operation or fingerprint: conflict.
3. Failure after BOQ snapshot write but before idempotency completion: no writes remain.
4. Failure during outbox insert: BOQ and idempotency changes roll back.
5. Repeated completed request returns the same decoded response.
6. Transaction callback failure rolls back and leaves no committed `PROCESSING` row.
7. Tenant A cannot read or replay tenant B's command.

## Compatibility note

The source bundle has no Maven build files or a live database configuration, so this
code has not been compiled against the repository's exact Vert.x/Quarkus versions.
Verify the `PgPool.withTransaction` signature and generated Mutiny API version in the
actual checkout. This implementation is an adapter slice, not a claim that the whole
BOQ workflow is production-ready.
