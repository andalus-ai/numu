# M1 — Content Core

## Delivered scope
- Independent `content/` Maven reactor with domain, SPI, application and PostgreSQL adapter artifacts.
- Tenant-scoped content identity and trusted actor/tenant command context.
- Immutable numbered revisions; revision updates append a new snapshot.
- Optimistic concurrency using an expected revision number.
- Draft/archive/restore lifecycle baseline.
- PostgreSQL schema with composite tenant/content keys and revision foreign keys.
- Domain and application unit tests.

## Explicit non-goals
Dynamic content type schemas, public publishing, editorial approval workflow, website builder, media binary storage and public REST endpoints are later milestones.

## Persistence guarantees
- All content reads and writes require tenant ID.
- Entry primary key is `(tenant_id, content_id)`.
- Revision primary key is `(tenant_id, content_id, revision_number)`.
- Revision append and current-revision pointer advance occur in one JDBC transaction.
- A stale expected revision updates zero rows and returns a conflict to the application layer.

## Integration
The adapter accepts a host-provided `DataSource`; it does not own connection configuration or tenant identity resolution. The domain and application modules do not depend on Quarkus, HTTP, ORM or broker libraries.

## Validation
Run from the repository checkout:
```bash
mvn -f content/pom.xml test
```
No local Maven compilation or PostgreSQL integration test has been executed as part of this remote GitHub write. Treat CI/build verification as pending until an actual run succeeds.
