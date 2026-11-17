# Historical graph projection operations

## Ownership and data safety

PostgreSQL is the canonical historical relationship store and transactional projection outbox. Neo4j is a disposable, rebuildable read projection. Operators must not edit Neo4j records directly as a repair strategy and must never mutate or delete canonical relationship revisions or outbox history.

Public corpus (tenant_id IS NULL) and each tenant-private corpus are separate operational scopes. Public operations require the dedicated public-corpus maintenance permission. Tenant operations require the authenticated tenant context and tenant maintenance permission. Never accept a tenant identifier from an untrusted request as proof of scope.

## Metrics contract

The application publishes framework-neutral snapshots through HistoricalProjectionMetricsPort. A runtime adapter can export them through Micrometer, OpenTelemetry, Prometheus, or another telemetry system. Keep tenant IDs, run IDs, event IDs, and relationship IDs out of metric labels; use bounded scope labels such as public / tenant and put identifiers in access-controlled logs or traces.

| Snapshot field | Meaning | Suggested exported metric |
|---|---|---|
| pendingEvents | Unpublished, non-dead events not currently held by a live lease | numou_history_projection_outbox_pending |
| leasedEvents | Events with an unexpired delivery lease | numou_history_projection_outbox_leased |
| deadLetterEvents | Events in terminal dead-letter state | numou_history_projection_outbox_dead |
| oldestPendingAgeSeconds | Age of the oldest currently pending event | numou_history_projection_outbox_oldest_pending_age_seconds |
| maxUnpublishedAgeSeconds | Age of the oldest unpublished event, including leased work | numou_history_projection_outbox_oldest_unpublished_age_seconds |
| runningRebuilds | Durable rebuilds still running in this scope | numou_history_projection_rebuild_running |
| rebuildEventsRemaining | Sum of captured high-water minus cursor for running rebuilds | numou_history_projection_rebuild_events_remaining |
| oldestRebuildAgeSeconds | Age of the oldest running rebuild | numou_history_projection_rebuild_oldest_age_seconds |

The metrics port also emits bounded rebuild progress (projected events, cursor, high-water, completion) and reconciliation results (canonical rows checked, missing, stale, divergent, orphaned, and repaired). Export these as counters/histograms or bounded gauges; do not label them with identifiers.

The PostgreSQL operational store is read-only. It applies transaction-local numou.tenant_id before tenant-scoped reads so forced row-level security remains active. Public snapshots read only the shared corpus. Collection is authorized through the maintenance authorization port.

## Initial alert guidance

Thresholds are deployment-specific and should be tuned against measured throughput and recovery objectives. Start with warning-level alerts and adjust after observing normal traffic:

- Pending count remains non-zero and oldest pending age exceeds the delivery SLO.
- Dead-letter count increases or remains non-zero beyond the agreed operator response window.
- Oldest unpublished age rises continuously while leased count is flat (likely stalled workers).
- Running rebuild age exceeds the planned maintenance window.
- Rebuild remaining count stops decreasing across successive collection intervals.
- Reconciliation reports any missing, stale, divergent, or orphaned records.
- Repaired count is non-zero; this is an operational event and should be reviewed, even when repair succeeds. Correlate it with the immutable STARTED/COMPLETED/FAILED audit entries for the operation.

Do not use a single global threshold for all tenants without considering workload size. Avoid paging on a transient backlog that is draining within the delivery SLO.

## Delivery incident procedure

1. Confirm the affected scope (public corpus or one tenant) and verify the caller's maintenance authorization.
2. Inspect pending, leased, dead-letter, and oldest-age metrics.
3. Check worker health, lease expiry, provider connectivity, Neo4j availability, and recent deployment changes.
4. Inspect a bounded dead-letter page using the authorized operational workflow. Review the stored error and event schema version.
5. Correct the underlying cause before replay. Replay is actor-attributed and append-audited; it resets the retry window but retains lifetime attempts.
6. Verify the event is delivered and the dead-letter count decreases. Do not edit the outbox row or its immutable audit history manually.

## Rebuild procedure

1. Verify the Neo4j target database and credentials. Schema bootstrap requires schema privileges and is a separate deployment operation.
2. Start a rebuild for exactly one authorized scope. A run captures an immutable outbox high-water sequence.
3. Process one bounded page at a time. The cursor advances only after the entire page projects successfully.
4. Pause a run before planned maintenance; pause takes effect at the next page boundary. A page already being projected may finish, but its checkpoint is rejected if the lifecycle version changed.
5. If a page fails, mark the run FAILED with a bounded reason and actor identity. Fix the projection/provider problem, then explicitly retry the same run. Retry resumes from the last committed cursor; projection application must remain idempotent.
6. Cancel only when the run should not be resumed. COMPLETED and CANCELLED are terminal; a FAILED run must be explicitly retried or cancelled.
7. Every pause, resume, failure, retry, and cancellation is actor-attributed and appended to the PostgreSQL lifecycle audit in the same transaction as the state transition. Stale operator commands fail their lifecycle-version compare-and-set.
8. Confirm the run completes and the outbox continues to deliver events created after the captured high-water mark.
9. Run a read-only reconciliation and review missing, stale, divergent, and orphaned counts.

Never run tenant and public rebuilds by passing client-supplied scope values. Never increase page limits beyond the service's enforced bounds to accelerate a recovery.

## Reconciliation and repair

Run reconciliation in dry-run mode first (repair=false). It compares canonical latest PostgreSQL events with the graph projection and checks for graph-only orphan records. Review the report before enabling repair.

Repair (repair=true) requires audit and rebuild authorization, an authenticated operator identity, and the durable PostgreSQL repair-audit adapter. The audit stream appends STARTED before projection changes and COMPLETED or FAILED afterward; records are immutable. If durable audit is not configured, repair is refused before projection changes. It replays canonical events only into the disposable projection. A graph revision newer than the canonical revision is reported as divergent and is not overwritten automatically. Escalate such a result for investigation rather than forcing a downgrade.

## Recovery and rollback

- If Neo4j is lost or corrupted, provision a clean projection database, bootstrap its constraints/indexes, and rebuild from PostgreSQL's retained outbox.
- If a rebuild fails, preserve the FAILED run and its last durable cursor. After resolving the cause, use the authorized, audited retry operation. Do not delete the run or reset its cursor manually.
- If a rebuild is paused, resume it through the authorized lifecycle operation. Do not restart a second run merely to bypass a pause.
- If a rebuild is cancelled, start a new run only after confirming that the cancelled snapshot is no longer required; cancelled runs cannot be resumed.
- If a deployment introduces an incompatible event schema, roll back the projection consumer or deploy a compatible adapter; do not rewrite historical outbox payloads.
- If PostgreSQL is unavailable, stop projection maintenance operations. Never fall back to treating Neo4j as canonical.
- Capture the run ID, deployment version, scope, time window, metric snapshot, and reconciliation report in the incident record, subject to tenant data access policy.

## Deployment checklist

- Apply Flyway migrations using the host's approved migration process before enabling adapters.
- Provision PostgreSQL and Neo4j credentials with least privilege; runtime projection role must not have canonical revision mutation rights.
- Bootstrap Neo4j schema using a schema-capable deployment identity, not the application read/write identity.
- Wire a metrics adapter and alert rules before production rollout.
- Configure tenant context, maintenance authorization, and authenticated operator context from trusted server-side security infrastructure. Worker-triggered failure transitions must use a configured service-principal actor.
- Exercise a tenant-scoped rebuild, public rebuild (where applicable), pause/resume, cancel, failure/retry, stale-worker fencing, dry-run reconciliation, and an audited dead-letter replay in a non-production environment.
- Verify the History PostgreSQL/Neo4j CI workflow and retain the test report with the release evidence.
