# History Graph PostgreSQL Adapter

JDBC persistence for the History graph SPI.

## Storage contract

- historical_relationship_revision is the authoritative append-only relationship store.
- tenant_id IS NULL identifies shared public-corpus assertions; a UUID identifies tenant-private research.
- The ordinary application role can select public and own-tenant rows, and insert only rows for its current tenant.
- PostgreSQL row-level security is forced on the relationship table. Every adapter operation starts a transaction and sets numou.tenant_id transaction-locally.
- Revision writes use an advisory transaction lock plus compare-and-append. The primary key (relationship_id, revision) is the final concurrency guard.
- Update, delete, and truncate are rejected by database triggers. Revisions preserve chronology, precision, approximation and original date expressions.

## Transactional projection outbox

Migration V2 creates historical_relationship_projection_outbox and an AFTER INSERT trigger on historical_relationship_revision.

- The relationship revision and its projection event are committed or rolled back together.
- The event key is deterministic for (relationship_id, revision), with a unique database constraint.
- The versioned JSONB payload contains tenant scope, typed subject/object references, relationship kind and historical validity data. It deliberately excludes free-text descriptions and source evidence.
- Event identity and payload are immutable. Delivery metadata is reserved for the projection worker.
- The trigger function is SECURITY DEFINER so the restricted application role can insert a relationship revision without receiving direct write access to the outbox table. Its search path is fixed.
- The outbox has no public request-facing read API. A later worker milestone must define a narrowly scoped worker role and lease/acknowledgement contract before enabling delivery.

## Migrations and composition

The adapter ships Flyway-compatible SQL under src/main/resources/db/migration. The host deployment must apply these migrations using Flyway or an equivalent controlled migration runner before enabling the repository. The adapter does not migrate a production database implicitly.

Construct JdbcHistoricalRelationshipRepository with the host DataSource and the immutable HistoricalRelationshipVocabulary selected by the host application. This module does not install a connection pool, HTTP server, CDI runtime, broker, or Neo4j driver.

## Security and operational notes

- Supply a restricted application database role; do not run request traffic as a superuser or table owner.
- Tenant UUIDs must be obtained from trusted server-side context before calling the SPI.
- Shared corpus writes are intentionally not exposed by this repository. A future ingestion adapter must use a narrowly scoped privileged role and explicit editorial authorization.
- The adapter's indexed SQL endpoint queries return the latest visible revision and have a hard maximum page size of 200.
- Neo4j remains an optional disposable projection; it is not authoritative storage.
