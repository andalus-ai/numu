# History Graph Application

Authorization-aware use cases for historical relationship research and trusted projection maintenance.

- Tenant identity for normal relationship commands comes only from HistoricalRelationshipTenantContext, supplied by the host's authenticated server-side security context.
- HistoricalRelationshipAuthorizationPort authorizes relationship access and validates existence/access for both referenced canonical entities.
- HistoricalProjectionMaintenanceAuthorizationPort is required by rebuild and reconciliation services. Tenant-private and shared public-corpus operations have separate authorization checks.
- Maintenance authorization is evaluated before any rebuild checkpoint, canonical audit event, or graph projection is read or changed.
- Projection reconciliation compares the canonical PostgreSQL outbox with the disposable graph projection. Repairs replay immutable canonical events only; a graph revision newer than PostgreSQL is reported and never overwritten automatically.
- No HTTP, CDI, database, broker, or graph-provider dependency is required. Hosts supply trusted authorization and runtime wiring.
- Shared public-corpus ingestion remains outside the tenant-facing relationship service.
