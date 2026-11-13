# History Graph SPI

The graph SPI owns the persistence boundary for historical relationship assertions.

## Tenant and corpus semantics

- A tenant identifier must come from the authenticated server-side tenant context. Do not accept it from a client request body or query parameter.
- Reads return the shared public corpus plus records owned by the requested tenant.
- Writes through this SPI append tenant-private research revisions only.
- Shared-corpus ingestion is a separate privileged workflow; the ordinary application database role cannot insert public rows with a null tenant_id.
- The SPI exposes canonical domain objects and never exposes SQL rows, ORM entities, or graph-provider types.

## Revision contract

saveTenantScoped(tenantId, relationship, expectedRevision) appends revision expectedRevision + 1 only when the current tenant-owned revision equals expectedRevision. Use 0 for creation. A false result indicates a compare-and-append conflict; callers should reload and reconcile rather than blindly retrying the same revision.

The repository never updates or deletes prior revisions. Relationship corrections are new immutable revisions.