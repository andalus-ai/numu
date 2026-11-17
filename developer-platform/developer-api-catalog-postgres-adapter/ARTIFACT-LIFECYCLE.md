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

## M5j operational audit and visibility

- Each successful lease claim, object deletion, retry scheduling and terminal failure appends an immutable row to `developer_api_artifact_cleanup_audit` in the same PostgreSQL transaction as the lifecycle state change.
- Audit rows carry tenant, product/version, immutable artifact reference and digest, action, worker actor reference, attempt number, bounded failure code and timestamp. RLS is forced by tenant; UPDATE, DELETE and TRUNCATE are rejected.
- `ApiArtifactCleanupRepository.snapshot(...)` provides tenant-scoped gauges for currently eligible items, active leases, terminal failures, tombstones and age of the oldest eligible item.
- The managed worker exposes process-local monotonic counters for claimed, deleted, failed and lease-lost items, plus its last completion/failure and the latest database snapshot. A hosting runtime can bind these getters to its metrics/health exporter without coupling the reusable capability to a particular metrics vendor.
- These counters reset on process restart. The audit table and database snapshot are the durable operational source of truth.
- A stale worker that no longer owns a lease cannot write a success/failure audit record over the newer lease. Failed object deletion and its audit entry are committed together with the retry/terminal state update.

## M5k retention and legal-hold governance

- Provider administrators can apply or release a legal hold and extend an artifact's retention deadline through the Developer API Catalog application contract and authenticated REST adapter.
- Governance commands derive tenant and actor from trusted execution context; request DTOs cannot select a tenant or impersonate an actor. A non-anonymous actor and a reason (1–500 characters) are required.
- Retention is monotonic: an existing deadline can be extended but never shortened or cleared through this contract. Deadlines must be in the future.
- Governance updates lock the tenant-scoped artifact row and append an immutable audit entry in the same PostgreSQL transaction. Audit history records the action, actor, reason, resulting legal-hold state and retention deadline.
- A legal hold cannot be applied while an active cleanup lease is executing. If a cleanup lease has expired and its 30-second object-delete settle window has elapsed, applying a hold atomically cancels the stale lease and returns the artifact to `ABANDONED`; a subsequent worker claim excludes it while the hold remains active.
- Retention extension follows the same active-lease fence. An expired pending lease is cancelled and returned to `ABANDONED` before the longer deadline is persisted.
- Governance does not override the protection of published artifacts or deleted tombstones. Releasing a hold does not itself delete an artifact; normal retention and cleanup eligibility checks still apply.
- The read-only governance view is available at `GET /api/v1/developer/api-products/{productId}/versions/{version}/governance`; it returns legal-hold state, retention deadline, lifecycle state, cleanup attempts, last bounded failure code and tombstone time.
- REST operations:
  - `PUT /api/v1/developer/api-products/{productId}/versions/{version}/legal-hold` with `{ "held": true|false, "reason": "..." }`
  - `PUT /api/v1/developer/api-products/{productId}/versions/{version}/retention` with `{ "retentionUntil": "...", "reason": "..." }`
