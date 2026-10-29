# POS Platform M2: Durable sale storage and idempotency

**Status:** Proposed for review; verify against the full Maven reactor and PostgreSQL integration tests before production use.  
**Roadmap:** [Issue #1102](https://github.com/andalus-ai/numu/issues/1102)

## Ownership and boundary

POS owns sale lifecycle state and sale-time line snapshots. The application owns command authorization and idempotency semantics. The PostgreSQL adapter owns storage mechanics. Catalog, pricing, tax, inventory, payment, and accounting remain authoritative in their own capabilities and are not changed by this milestone.

## Included

- PostgreSQL-backed tenant-scoped sale storage with optimistic version compare-and-update.
- Durable idempotency records scoped by tenant, actor, and key.
- A unique database constraint serializes concurrent attempts for the same key. The command reservation, sale mutation, and completed response are intended to participate in one JTA transaction; an exception rolls the transaction back.
- Flyway migration for sale rows and idempotency rows.
- PostgreSQL JDBC and Flyway runtime dependencies.

## Transaction assumptions

The idempotency adapter opens a JTA transaction and obtains a JDBC connection. Sale repository calls made inside the command supplier must be enlisted in that same transaction by Quarkus Agroal/JTA. Verify this behavior with PostgreSQL integration tests, especially concurrent duplicate requests, a failure after the sale write but before idempotency completion, and a retry after rollback.

A committed PROCESSING record should not be produced by the normal single-transaction flow. If one is found, the adapter fails closed and raises an operational-recovery error rather than rerunning an uncertain command.

## Schema and migration

V1__pos_sale_storage.sql defines:
- pos_sale, keyed by (tenant_id, sale_id) with an optimistic version and JSONB aggregate payload.
- pos_command_idempotency, keyed by (tenant_id, actor_id, idempotency_key) and retaining the operation, SHA-256 fingerprint, status, result payload, and timestamps.

Flyway migration-at-start is enabled for this service configuration. Production rollout must ensure the database role has the required migration privileges, backups are current, and schema migration is controlled through the deployment process.

## Verification still required

- Build the commerce Maven reactor and run POS application tests.
- Run PostgreSQL-backed integration tests for JSON serialization, optimistic conflicts, concurrent duplicate commands, rollback, and tenant isolation.
- Verify the Quarkus version's JDBC/Flyway dependency alignment and the application's actual datasource configuration.
- Add a durable outbox/audit trail before publishing external effects; this milestone intentionally does not call payment, inventory, tax, or accounting systems.
