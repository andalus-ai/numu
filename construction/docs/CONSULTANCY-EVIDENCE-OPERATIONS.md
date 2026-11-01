# Consultancy evidence operations

Construction owns the durable attachment saga state. Document continues to own
versioned evidence links and document access policy.

Operational inspection and recovery are application use cases behind
`ConsultancyEvidenceOperationsAdminAuthorization`. A host must supply an
authorization adapter; operation IDs and tenant identifiers are not credentials.
The application service authorizes every list and recovery request before it
reaches the persistence port.

Recovery is intentionally limited to `TERMINAL_FAILURE`. The PostgreSQL
adapter locks the tenant-scoped operation, appends a separate audit row, and
requeues it as `PENDING` in one transaction. The original `last_failure`,
attempt count, and creation identity are retained. The worker will claim the
requeued operation with a new lease token; stale workers remain fenced.

The recovery idempotency key is unique within a tenant. Reusing it for a
different operation, actor, or reason is a conflict. The audit table is
append-only by application contract; deployments should grant the runtime role
INSERT/SELECT but not UPDATE/DELETE on that table.

No HTTP endpoint, scheduler, tenant enumeration, or host authorization policy
is introduced by this capability milestone.
