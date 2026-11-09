# API specification artifact lifecycle

The API Catalog owns lifecycle metadata for its immutable OpenAPI objects. Binary bytes remain in the shared Document Storage capability; this registry is catalog metadata, not a second object store.

## States

- `REGISTERED`: an artifact is referenced by a persisted API version but has not been published.
- `PUBLISHED`: the version was published. This state is intentionally terminal for cleanup purposes.
- `ABANDONED`: a future reconciliation process has established that a registered artifact is no longer referenced by an active version.
- `DELETE_PENDING`: a cleanup worker has acquired a lease and the configured retention window has elapsed.
- `DELETED`: a tombstone recording completed object deletion. The registry row is retained.

## Safety invariants

1. The tenant, product/version identity, object reference and SHA-256 are immutable.
2. Existing versions are backfilled as `REGISTERED` or `PUBLISHED`; migration never infers abandonment from age.
3. Published artifacts cannot transition to a cleanup state.
4. Legal hold blocks cleanup, including an attempt to remove the hold and schedule deletion in one update.
5. An artifact can enter `DELETE_PENDING` only from `ABANDONED`, with an expired retention deadline, an eligible retry time and an active lease.
6. An artifact can become `DELETED` only while the prior cleanup lease is still active.
7. Lifecycle rows are tombstoned rather than physically deleted. Tenant isolation is enforced with PostgreSQL RLS.

## Scope of this milestone

This milestone adds the durable registry and persistence coupling between API version registration/publication and lifecycle state. It deliberately does not run object deletion. A later worker milestone must add lease acquisition, retry/backoff, storage deletion and crash recovery before any physical object cleanup is enabled. Until then, artifacts remain in storage and no cleanup is scheduled automatically.
