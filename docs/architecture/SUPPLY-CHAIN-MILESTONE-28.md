# Supply Chain Milestone 28 — Recovery Operator Audit Trail

## Scope

M28 adds a durable append-only record for human-initiated manual replay of dead-lettered warehouse execution attempts.

## Behavior

- Migration V6 creates `warehouse_recovery_operator_audit`, with tenant and command identity, action, actor, reason, prior/resulting versions, optional correlation ID, and timestamp.
- The repository now offers an actor-aware `replayDeadLetter` overload and a bounded tenant-scoped audit query.
- Replay state mutation and audit insertion execute in one JDBC transaction. A stale version or failed audit insert rolls back the operation; rejected replay attempts do not leave success audit rows.
- `WarehouseRecoveryOperationsService` exposes actor-aware replay and tenant-scoped audit inspection.
- The old replay method remains as a deprecated compatibility path and records actor ID `legacy-unknown`. New authenticated operator flows should always provide an actor ID and correlation ID.
- The migration adds tenant/time and tenant/command indexes for bounded audit reads.

## Security and retention

Audit events are immutable through the application API: no update/delete methods are exposed. Production database roles should have SELECT/INSERT permission for the audit table and should not have UPDATE/DELETE permission where feasible. Retention, legal hold, and export policy should be defined by the deployment's compliance requirements.

The actor ID is supplied by the caller; the application boundary must derive it from authenticated identity rather than trust an arbitrary client-provided value. Correlation IDs should be generated or validated by trusted middleware.

## Integration verification

The opt-in PostgreSQL integration test requires a disposable PostgreSQL database with warehouse migrations V1 through V6 applied. Configure `NUMOU_TEST_POSTGRES_URL` and optionally `NUMOU_TEST_POSTGRES_USER` / `NUMOU_TEST_POSTGRES_PASSWORD`.

The test verifies replay metadata, tenant isolation of audit queries, and that stale replay does not create a second audit event. Maven and live PostgreSQL tests were not executed in the GitHub editing environment; CI or a configured disposable database must verify compilation and behavior before merge.

## Deployment notes

V6 is forward-only. Existing deployments should apply it through the project's normal migration process after reviewing Flyway history. Do not rewrite historical migration checksums or delete migration history to force the migration through.
