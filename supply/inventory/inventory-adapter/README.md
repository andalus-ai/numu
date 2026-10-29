# PostgreSQL Inventory Adapter

This adapter is the durable persistence implementation for the existing Inventory capability. It does not introduce a second inventory domain or move reservation ownership into Marketplace.

## Tenant binding

Create a repository instance for a single tenant through the configured TenantInventoryRepositoryProvider. The adapter validates reservation tenant IDs and includes tenant_id in every SQL lookup and write. Do not share one instance across tenant scopes. Database credentials and tenant authorization remain application deployment responsibilities.

## Transaction invariants

- Stock-affecting commands serialize on a tenant/item/warehouse lock row.
- Multi-warehouse transfers acquire lock rows in sorted warehouse-ID order.
- Reservation creation checks ledger on-hand quantity minus active, unexpired reservations while holding the same stock lock used by stock movements.
- Idempotent command keys are serialized using a transaction-scoped PostgreSQL advisory lock. A key is bound to a command type and SHA-256 payload fingerprint; replay returns the original movement/reservation, while changed payloads are rejected.
- Reserved issue posting writes the movement and commits the reservation in one transaction.
- Reservation release/expiry uses optimistic version checks. A reservation cannot be marked committed through the generic update method; committing requires the reserved-issue transaction.

## Migration and configuration

The Flyway migration is in src/main/resources/db/migration/V1__inventory_ledger_and_reservations.sql. Include this adapter's Flyway location in the service's migration configuration and provide a PostgreSQL datasource.

## Important deployment note

This is the first durable adapter milestone. Production rollout still requires an application wiring change that binds the tenant repository provider to this adapter, PostgreSQL integration CI with a real service datasource, and operational validation of connection pool, migration ownership, backup/restore, and metrics. Keep the in-memory adapter for deterministic unit tests only.
