# Supply Chain Milestone 8 — JDBC recovery journal

## Purpose

Implement the M7 execution journal persistence port so warehouse/inventory recovery state can survive process restarts. This remains a neutral warehouse capability and does not couple warehouse to a particular transport provider or database framework.

## Included

- Optional `numou-warehouse-persistence-jdbc` adapter module.
- PostgreSQL migration for a tenant-scoped execution journal with command-key uniqueness, status constraints, version checks, timestamps, and a partial recovery index.
- JDBC implementation for insert-if-absent, tenant-scoped lookup, optimistic transitions, and bounded recoverable-attempt scans.
- No JDBC dependency is introduced into the warehouse domain or application layers.

## Operational contract

- Run the migration through the host application's configured migration runner.
- Supply a pooled `DataSource`; the adapter does not own pool lifecycle or credentials.
- Keep tenant ID and command key in every read/update predicate.
- A duplicate insert returns the existing journal record; the application service must compare the payload identity and reject mismatches.
- Transition updates compare the expected version and fail on stale writers.
- Recovery consumers must claim/lease records before parallel processing if multiple workers may run. This adapter only lists candidates and does not itself provide a distributed lease.
- The journal is evidence of orchestration progress, not the authoritative inventory ledger.

## Limitations / merge gate

This milestone persists only the execution journal. It does not yet provide JDBC warehouse aggregate/receipt/outbox persistence, durable inventory command receipts, a lease/claim protocol, or database integration tests. The adapter's SQL is PostgreSQL-specific due to `ON CONFLICT` and the migration's `TIMESTAMPTZ`/partial index. Maven tests and CI must be run before merge; no passing result is claimed.
