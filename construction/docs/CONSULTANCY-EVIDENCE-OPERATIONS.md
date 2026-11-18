# Consultancy evidence operations

Construction owns the durable attachment saga state. Document continues to own
versioned evidence links and document access policy.

Operational inspection and recovery are application use cases behind
`ConsultancyEvidenceOperationsAdminAuthorization`. A host must supply an
authorization adapter; operation IDs and tenant identifiers are not credentials.
The application service authorizes every list, summary, audit-history, and
recovery request before it reaches persistence.

The tenant-scoped operational snapshot reports counts by operation state and
the oldest update timestamp for pending, retryable, and terminal failures. It
does not enumerate tenants, expose global totals, or calculate alert thresholds;
those are host-level operational policy.

Recovery history is separately queryable by tenant and operation, ordered newest
first with bounded pagination. It contains the actor, reason, idempotency key,
and immutable state transition. The database trigger rejects UPDATE and DELETE;
deployments should additionally grant the runtime role INSERT/SELECT but not
UPDATE/DELETE on the audit table.

Recovery is intentionally limited to `TERMINAL_FAILURE`. The PostgreSQL
adapter locks the tenant-scoped operation, appends a separate audit row, and
requeues it as `PENDING` in one transaction. The original `last_failure`,
attempt count, and creation identity are retained. The worker will claim the
requeued operation with a new lease token; stale workers remain fenced.

No HTTP endpoint, scheduler, tenant enumeration, or host authorization policy
is introduced by this capability milestone.
