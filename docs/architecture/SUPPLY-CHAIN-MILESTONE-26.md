# Supply chain M26 — warehouse migration-chain verification

## Scope

M26 follows the M25 Flyway version-collision repair with a regression guard and clearer integration-test prerequisites. The migration directory now has V1 through V5, with V2 reserved for transactional execution and V5 for retry scheduling.

## Delivered

- Added `WarehouseMigrationChainContractTest`, which scans the JDBC module's migration resources and fails when two SQL migrations use the same numeric Flyway version.
- The contract test requires warehouse migration versions V1 through V5 and asserts the canonical V2 and V5 migration names.
- Updated opt-in PostgreSQL recovery integration-test documentation to require the complete V1–V5 schema.

## What this protects

The contract test prevents the exact duplicate-version regression from silently returning. It is not a substitute for executing Flyway against PostgreSQL: it does not validate SQL syntax, constraint compatibility, or real upgrade behavior.

## Required validation before release

1. Run `mvn -pl supply/warehouse/warehouse-persistence-jdbc -am test` from the repository root.
2. Run the Flyway V1–V5 chain on a disposable PostgreSQL database and confirm the expected tables, columns, indexes, and status constraint.
3. With `NUMOU_TEST_POSTGRES_URL` and optional `NUMOU_TEST_POSTGRES_USER` / `NUMOU_TEST_POSTGRES_PASSWORD` configured, run the warehouse PostgreSQL integration tests.
4. Run a separately reviewed upgrade test against a representative pre-M25 database snapshot. The previous duplicate V2 history cannot be reconciled generically.

## Validation status

Changes were committed to the milestone branch. Maven and PostgreSQL tests were not executed in this environment; CI results should be reviewed before merge.
