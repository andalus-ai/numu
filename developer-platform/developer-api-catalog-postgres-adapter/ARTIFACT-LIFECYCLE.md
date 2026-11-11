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

M5h adds the durable registry and persistence coupling between API version registration/publication and lifecycle state.

## M5i cleanup worker

- The application dispatcher operates on one trusted tenant per invocation. Hosts create one `ApiArtifactCleanupManagedWorker` per authorized tenant; PostgreSQL leases coordinate replicas.
- Claiming uses `FOR UPDATE SKIP LOCKED`, a unique per-dispatch lease owner, bounded batches and lease expiry. Expired leases can be reclaimed after a process crash.
- The worker rechecks tenant, immutable reference, digest, retention, legal hold, attempt budget and current lease immediately before deletion.
- The Document Storage adapter accepts only the catalog's `s3://api-catalog` or `rustfs://api-catalog` reference shape. It constructs the storage key from the trusted tenant, verifies the object's key and SHA-256 metadata, and treats an already absent object as a successful retry.
- Failures are recorded as bounded error codes with exponential backoff (5 seconds, tripling to a one-hour cap). After the configured maximum attempts, the row remains visible in `ABANDONED` with its attempt count and last error code; it is not automatically retried until an operator-approved remediation resets the attempt budget.
- Successful deletion transitions the row to the durable `DELETED` tombstone. If a process crashes after deleting the object but before tombstoning, the next lease safely repeats the idempotent delete.

No automatic transition from `REGISTERED` to `ABANDONED` is introduced. A future reconciliation/administrative workflow must explicitly establish that an artifact is no longer referenced and assign its retention deadline before it becomes eligible. The worker never infers cleanup eligibility from age, and published artifacts remain protected.
