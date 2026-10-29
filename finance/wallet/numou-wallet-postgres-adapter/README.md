# Wallet PostgreSQL adapter

This module packages PostgreSQL-backed implementations of the neutral wallet repository ports:

- `PgWalletRepository`: tenant-scoped, versioned wallet snapshots using the framework aggregate-document store.
- `PgLedgerRepository`: append-only ledger persistence with idempotency-reference lookup and request-fingerprint support.

## Deployment and migration boundary

The initial extraction intentionally leaves the existing wallet ledger migrations in
`mobility/mobility-persistence/src/main/resources/db/migration`. That is a compatibility bridge, not the desired final ownership boundary. Do not remove or duplicate those migrations in this module until the repository's Flyway discovery and application deployment topology have been reconciled; loading both copies could cause migration collisions.

The adapter must be included in an application explicitly and its CDI alternatives selected intentionally (for example, through `quarkus.arc.selected-alternatives`). The repository classes are CDI alternatives and are inactive by default, so adding the module to a runtime does not silently replace existing repository implementations. Do not select the neutral adapters in the same runtime composition as the legacy Mobility repositories unless the alternatives mechanism is configured to prefer the neutral implementations.

## PostgreSQL integration profile

The Mobility API's opt-in `pg-integration` profile selects these neutral repository alternatives for the existing end-to-end wallet tests. Run it only against a dedicated PostgreSQL database with the framework and Mobility migrations applied:

```sh
mvn -f mobility/mobility-api/pom.xml -Ppg-integration verify
```

The test profile selection is deliberately separate from production composition. The suite asserts the selected repository implementations before exercising replay, conflict, transaction rollback, and tenant-scoped reads. CI's ordinary unit-test run excludes these PostgreSQL tests.

## Production verification still required

Before treating this adapter as production-ready, add/run PostgreSQL integration tests that prove:

1. wallet snapshot save, ledger append, and outbox publication share one transaction;
2. a ledger uniqueness conflict rolls back the wallet mutation;
3. concurrent writes cannot overspend and optimistic retries reload fresh state;
4. tenant boundaries are enforced for both aggregate and ledger reads;
5. replayed operation references are stable across process restarts.

This module is a packaging extraction, not a claim that these deployment and transaction properties have already been proven.
