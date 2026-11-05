# M5c — PostgreSQL persistence for Developer API Catalog

## Scope
Adds a JDBC adapter for the M5 API catalog aggregate using the existing developer-platform PostgreSQL conventions. It reuses the existing developer-platform Maven aggregator and does not create a second developer platform or gateway persistence boundary.

## Persistence model
- `developer_api_products`: provider-tenant-owned API product metadata and optimistic revision.
- `developer_api_versions`: semantic API version, immutable OpenAPI artifact pointer and digest, lifecycle status.
- `developer_api_scopes`: immutable scope vocabulary attached to one product version.
- Composite primary/foreign keys carry provider tenant identity through every relationship.
- Unique `(provider_tenant_id, slug)` prevents duplicate product slugs within one provider tenant.
- RLS is enabled and forced on all catalog tables. Each adapter transaction installs `numou.tenant_id` transaction-locally and SQL also includes explicit tenant predicates.
- Contract metadata, product identity metadata and scope definitions are protected against mutation at the database boundary.

## Concurrency and transaction behavior
- Create requires expected revision zero and inserts the product and its versions/scopes atomically.
- Update requires the aggregate revision to equal expected revision + 1 and uses `WHERE provider_tenant_id=? AND id=? AND revision=?`.
- A stale or cross-tenant update fails closed with `OptimisticLockException`.
- Version contract metadata is immutable; only lifecycle status can change.
- JDBC work is shifted to Mutiny's default worker pool; the adapter does not perform blocking JDBC work on the caller/event-loop thread.

## Important boundaries
- OpenAPI bytes are not stored in PostgreSQL; only an opaque artifact reference and SHA-256 digest are stored. The M5b `ApiSpecificationValidationPort` remains mandatory before publish.
- This adapter does not implement the concrete authorization policy or event/outbox publisher. Those remain separate M5d wiring tasks; persistence alone must not be exposed as an HTTP endpoint.
- Artifact ownership and tenant authorization must be checked by the artifact adapter before publication.

## Verification
Testcontainers PostgreSQL tests cover tenant isolation, tenant-scoped slug uniqueness, aggregate round-trip, no synthetic creation event during reconstitution, and optimistic revision conflicts. Tests require Docker and are skipped when Docker is unavailable.
