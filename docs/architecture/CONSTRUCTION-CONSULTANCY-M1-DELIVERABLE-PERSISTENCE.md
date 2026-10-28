# Construction Consultancy M1 — Deliverable Persistence

## Scope

This change adds PostgreSQL persistence for the existing versioned consultancy deliverable aggregate. It deliberately builds on the consultancy capability instead of introducing a construction-only document model or a second document store.

## Contract and storage

- `ConsultancyDeliverable.reconstitute(...)` restores persisted state without replaying lifecycle commands and validates review metadata.
- `TransactionalCommandContext.ConsultancyOperations` exposes tenant-scoped save and lookup methods for deliverables.
- The PostgreSQL adapter uses the transaction-bound connection supplied by `PgTransactionalCommandRunner`; it does not acquire a separate connection.
- Migration `V20261028_02__consultancy_deliverables.sql` creates a tenant-scoped table with a composite key, tenant+engagement+code uniqueness, and a composite foreign key to the consultancy engagement in the same capability.
- Database checks enforce valid statuses, positive revisions, distinct client/consultant parties, and client-owned review metadata. A rejected deliverable must retain a nonblank rejection reason.
- Every lookup is scoped by both tenant ID and deliverable ID. Cross-tenant reads return no result.

## Verification added

- Domain test for aggregate reconstitution and inconsistent reviewer metadata.
- PostgreSQL integration test for saving and reading a rejected deliverable, review metadata restoration, and tenant isolation.

The tests are authored but have not been run in this environment. Run the construction reactor's unit suite and the PostgreSQL integration profile (`mvn -Ppg-integration verify` from `construction/`) in CI or an environment with the required database and migrations.

## Explicit limitations

This is persistence foundation, not a complete deliverable workflow. It does not yet add application commands for create/submit/accept/reject/revise, durable immutable per-revision history, file/document evidence linkage, or authorization wiring for reviewer actions. Those operations must be implemented through idempotent command handlers with transaction-bound state changes and outbox events before production use. A saved deliverable must reference an existing engagement in the same tenant.

This milestone makes no claim of Indonesian statutory compliance or Sharia/istisna' compliance.
