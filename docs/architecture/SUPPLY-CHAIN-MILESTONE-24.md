# Supply Chain Milestone 24 — Recovery Observability and Operator Controls

## Scope

M24 makes recovery operations tenant-aware at the operator-facing boundary. It adds a point-in-time health summary, tenant-scoped dead-letter inspection, and a version-fenced manual replay operation.

## Delivered

- Tenant-scoped health counters for recoverable, currently claimed, reconciliation, and dead-letter records.
- Oldest recoverable creation timestamp and oldest dead-letter update timestamp to support queue-age dashboards.
- Tenant-scoped dead-letter listing with bounded result size.
- Manual dead-letter replay that requires the tenant, command key, expected version, and a nonblank operator reason. It atomically changes the record back to PREPARED, increments the attempt count/version, records the reason, clears stale lease fields, and applies a future eligibility timestamp.
- Replay is guarded by optimistic version fencing; stale operator screens cannot silently replay a newer record.

## Operational guidance

Health summaries are snapshots, not transactionally locked dashboards. Use repeated samples to calculate trends and alert on growing queue age. Tenant IDs must come from the authenticated tenant context; do not accept an arbitrary tenant from an untrusted client.

Manual replay is a deliberate operator action. It does not bypass command idempotency: downstream inventory and warehouse effects must still use the original tenant-scoped command key/fingerprint. The operator reason is retained in `last_error` for audit context until the next domain transition updates it.

## Validation status

Unit/contract tests are added or extended where practical. They have not been executed in this environment. Run the warehouse application and JDBC persistence test suites against the full PostgreSQL migration chain before merge.
