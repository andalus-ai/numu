# Transport inventory — M3: PostgreSQL persistence

## What is implemented

- `transport-spi` defines the persistence port used by application services and alternate adapters.
- `transport-postgres-adapter` implements the port with JDBC and owns Flyway migration `V1__transport_departure_inventory.sql`.
- Inventory and passenger holds are stored under tenant/departure composite keys. Hold idempotency is unique within that scope.
- Reads use a repeatable-read transaction so the inventory version and hold list come from one database snapshot.
- Writes compare-and-set the inventory version and update the version plus all hold rows in one transaction.
- A stale writer receives `InventoryConcurrencyException`; the caller must reload and re-run the domain command rather than retry a stale candidate.
- Hold identity fields are immutable at the persistence boundary; only legal hold status transitions are accepted.
- Existing hold IDs must be retained in every saved snapshot. Database transactions roll back the version update if a hold write or audit-retention check fails.
- Tenant ID and departure ID are always part of lookup and write predicates; identical departure UUIDs can exist in separate tenants.

## Concurrency contract

The row-version compare-and-set is the serialization point. Two callers may both calculate a hold against version N, but only one can update the inventory row from N. The other transaction updates zero rows, rolls back, and must reload. The rejected stale candidate is not written, so it cannot oversell capacity.

The repository does not silently retry business commands: after a conflict, the application must re-run the domain operation against the new snapshot, which may now reject the request for insufficient capacity. This avoids accepting a command whose capacity assumptions were stale.

## Schema and runtime

- Database: PostgreSQL.
- Migration location: `transport-postgres-adapter/src/main/resources/db/migration/`.
- Runtime composition must provide a configured `DataSource` and apply the migration (for example, through the host application's Flyway configuration).
- PostgreSQL JDBC driver is declared as a runtime dependency of the optional adapter.
- Domain and SPI modules remain free of database/framework dependencies.

## Verification

PostgreSQL integration tests use Testcontainers and exercise persistence/reconstitution, tenant isolation, concurrent stale writes, rollback after a hold insert failure, and audit-history retention. They are named as Maven Surefire tests so `mvn -B -ntp -f transport/pom.xml verify` runs them. Testcontainers may skip the integration tests when Docker is unavailable; a skipped container test is not equivalent to a successful database verification.

## Remaining work

This milestone supplies the persistence port and reference PostgreSQL adapter. It does not yet provide an HTTP booking API, an automatic conflict-retry application service, a hold-expiration scheduler, ticket issuance, payment capture/refunds, or a production runtime configuration. Those should be introduced through their owning capability contracts in subsequent milestones.
