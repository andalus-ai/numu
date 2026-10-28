# Consultancy deliverable creation and immutable history

## Scope

This milestone adds the authorized application command for creating a deliverable under an active consultancy engagement and an append-only timeline of deliverable snapshots. It does not add a public HTTP endpoint; runtime/API composition must expose the application handler through the repository's established boundary.

## Creation invariants

- Tenant and actor identity come only from the trusted ConsultancyTenantContext.
- The engagement is loaded through the tenant-scoped repository operation.
- Deliverables may be created only while the engagement is active.
- Client and consultant organizations are copied from the persisted engagement, never accepted from the caller.
- The actor must be authorized for CREATE_DELIVERABLE against the persisted consultant organization.
- The current-state insert, initial history snapshot, outbox event and idempotency outcome must share the same transaction-bound command context.

## History invariants

- Every create/lifecycle command appends a point-in-time snapshot containing the deliverable revision, status, content, review metadata, actor and occurrence time.
- Revision changes do not overwrite previous history snapshots; a rejection remains visible when a later revision is drafted.
- The history table has tenant-scoped foreign keys and an append-only database trigger that rejects updates and deletes.
- Timeline queries are tenant-scoped and ordered by the database-generated sequence, not caller timestamps.
- The history snapshot is an audit timeline, not a replacement for the current aggregate or for signed document/evidence storage.

## Remaining production gates

- Run the construction domain/application tests and PostgreSQL integration suite against this migration.
- Verify replay, same-key/different-payload conflicts, outbox rollback and concurrent review races.
- Add a tenant-authorized history query handler/API and retention/export policy.
- Integrate signed report/drawing/acceptance evidence only through the public document capability contract.
- Continue Indonesia statutory and Sharia istisna' extensions under qualified review; this milestone makes no compliance claim.
