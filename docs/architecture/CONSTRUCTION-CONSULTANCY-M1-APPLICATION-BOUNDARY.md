# Construction Consultancy M1 — Application Boundary

This slice defines the application-to-persistence and application-to-authorization boundaries for consultancy engagements.

## Boundaries

- The repository SPI requires tenant scope on every read and write. A storage adapter must not resolve a consultancy aggregate by ID without applying the tenant predicate.
- The pair (tenant_id, engagement_number) must be unique at the database level. Application preflight checks are useful for feedback but cannot prevent concurrent duplicates.
- ConsultancyAuthorization is a port. Its adapter must verify the authenticated actor's membership and permission for the requested organization and action. An organization ID in a request is not proof of authority.
- ConsultancyTenantContext is only to be built after authentication and tenant resolution. HTTP/runtime adapters must not trust tenant or actor IDs supplied in the request body.
- Consultancy remains a Construction extension. It does not take ownership of identity, project, document, finance, or workflow capabilities.

## Transaction and delivery requirements before exposing write endpoints

The repository contract intentionally does not pretend that a normal save call provides command idempotency or durable event delivery. Before write endpoints are exposed, the command implementation must compose the existing Construction IdempotentCommandExecutor with a transaction-bound consultancy persistence operation so that aggregate writes and the completed idempotency response commit or roll back together. Any emitted domain/integration events must use the transaction-bound outbox.

A preflight uniqueness check must not be treated as a concurrency guarantee. The adapter must translate storage uniqueness violations into a stable application conflict result.

## Verification status

This change adds contracts only; no database adapter, endpoint, or transaction-bound command handler is introduced here. It makes no claim of Indonesian regulatory compliance or Sharia/istisna' compliance. Those require later compliance-profile and contract-evidence milestones, with qualified legal and Sharia review.
