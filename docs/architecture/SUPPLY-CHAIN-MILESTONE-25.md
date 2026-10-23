# Supply chain M25 — warehouse migration-chain repair

## Problem

The warehouse JDBC adapter currently has two migration files with Flyway version V2:

- V2__warehouse_transactional_execution.sql creates the transactional work-order, command-receipt, and outbox tables.
- The former V2__warehouse_retry_schedule.sql adds retry eligibility and the dead-letter status constraint.

Flyway requires a unique version for each migration. A fresh database cannot safely run this migration set as-is; depending on configuration it fails validation or migration resolution before the warehouse recovery schema is usable.

## Changes

- Keep V2__warehouse_transactional_execution.sql as the canonical V2 migration.
- Move retry scheduling forward to V5__warehouse_retry_schedule.sql, after V3 leases and V4 replay payload.
- Make retry-column and index creation idempotent so a database where the retry objects were provisioned before this repair does not fail merely because those objects already exist.
- Preserve the original schema semantics and do not rewrite recovery-attempt rows.

## Deployment and migration-history warning

This fixes the migration files for a clean database. Existing databases may already have a Flyway schema-history row for the former V2 retry migration. Because two different scripts previously claimed version V2, there is no universally safe automatic way to infer which script a deployed database recorded.

Before deploying this repair to an existing environment:

1. Back up the database and inspect flyway_schema_history, including the installed V2 description and checksum.
2. Compare the actual schema with both former V2 scripts and confirm which objects were applied.
3. Prepare a database-specific, reviewed Flyway history reconciliation/repair plan. Do not blindly run flyway repair, delete history rows, or edit checksums in production.
4. Verify the resulting chain on a disposable PostgreSQL database from V1 through V5, then test an upgrade from a representative snapshot of the deployed schema.

The application repository does not run migrations automatically; the deployment owner remains responsible for running Flyway in a controlled release step.

## Validation status

The migration chain was inspected and the duplicate version was confirmed. This change has not been executed against PostgreSQL in this environment. A clean-database migration test and an upgrade test are required before merge.
