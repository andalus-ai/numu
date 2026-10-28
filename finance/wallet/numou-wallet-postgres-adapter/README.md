# Wallet PostgreSQL adapter

This module packages PostgreSQL-backed implementations of the neutral wallet repository ports:

- `PgWalletRepository`: tenant-scoped, versioned wallet snapshots using the framework aggregate-document store.
- `PgLedgerRepository`: append-only ledger persistence with idempotency-reference lookup and request-fingerprint support.

## Deployment and migration boundary

The initial extraction intentionally leaves the existing wallet ledger migrations in
`mobility/mobility-persistence/src/main/resources/db/migration`. That is a compatibility bridge, not the desired final ownership boundary. Do not remove or duplicate those migrations in this module until the repository's Flyway discovery and application deployment topology have been reconciled; loading both copies could cause migration collisions.

The adapter must be included in an application explicitly. Do not include it alongside the legacy Mobility adapter beans in the same CDI application, because both implement the same wallet repository ports.

## Production verification still required

Before treating this adapter as production-ready, add/run PostgreSQL integration tests that prove:

1. wallet snapshot save, ledger append, and outbox publication share one transaction;
2. a ledger uniqueness conflict rolls back the wallet mutation;
3. concurrent writes cannot overspend and optimistic retries reload fresh state;
4. tenant boundaries are enforced for both aggregate and ledger reads;
5. replayed operation references are stable across process restarts.

This module is a packaging extraction, not a claim that these deployment and transaction properties have already been proven.
