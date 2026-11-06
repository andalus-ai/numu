# History Graph Application

Authorization-aware use cases for historical relationship research.

- Tenant identity comes only from HistoricalRelationshipTenantContext, supplied by the host's authenticated server-side security context.
- HistoricalRelationshipAuthorizationPort must authorize relationship access and validate existence/access for both referenced canonical entities.
- The service checks authorization before reads to avoid leaking relationship existence.
- Create and revise operations use tenant-private repository writes and optimistic revision checks.
- No HTTP, CDI, database, broker, or graph-provider dependency is required.
- Shared public-corpus ingestion is intentionally outside this request-facing service.
