# Cargo & Courier PostgreSQL Adapter

This module provides JDBC implementations of `HubRepository` and `ShipmentRepository`, plus Flyway migrations for tenant-owned hubs, shipments, and parcels.

## Runtime integration

Include `numou-courier-postgres-adapter` in the Quarkus application that owns the cargo/courier runtime. Configure the datasource in that application (do not commit credentials):

```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=${DB_USERNAME}
quarkus.datasource.password=${DB_PASSWORD}
quarkus.datasource.jdbc.url=${DB_JDBC_URL}

# Enable after reviewing the migration plan for the target environment.
quarkus.flyway.migrate-at-start=true
```

Flyway migrations are packaged under `db/migration`. The adapter does not create or own the application's datasource and should not contain environment-specific credentials.

## Persistence guarantees

- Save rejects an aggregate whose tenant differs from the explicit tenant argument before opening a connection.
- Reads, existence checks, and deletes include `tenant_id` in their SQL predicates.
- A database uniqueness constraint enforces normalized hub-code uniqueness per tenant; the application pre-check is only an early user-facing validation.
- PostgreSQL unique-constraint diagnostics for duplicate tenant-local hub codes are translated to `HubCodeConflictException`.
- Cross-tenant facility sharing is not implicit. It requires a separate authorization/agreement model.
- Shipment saves persist the booking aggregate and all parcels in one database transaction.
- Shipment lookups and draft deletion include tenant predicates; shipment references are unique per tenant.
- A shipment cannot be booked without at least one valid parcel. Sender/recipient address data is captured as a booking-time snapshot.

## Verification

The module contains lightweight query-contract tests and Docker-backed PostgreSQL tests. The latter execute Flyway migrations and cover tenant-isolated reads/deletes, same-code reuse across tenants, tenant-local duplicate rejection, a concurrent duplicate-code race, shipment/parcel round trips, shipment tenant isolation, and tenant-local shipment-reference uniqueness.

Run from the repository's supported Maven reactor:

```bash
mvn -f logistics/pom.xml -pl numou-courier-postgres-adapter -am test
```

Migrations V1–V3 are packaged only in the persistence adapter to avoid duplicate Flyway versions on the runtime classpath. The Testcontainers tests are skipped when Docker is unavailable. A successful source commit does not mean these tests have passed; verify the Maven/CI result before promoting the adapter.
