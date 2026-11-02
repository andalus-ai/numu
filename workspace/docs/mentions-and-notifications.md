# Workspace comment mentions and durable delivery

## Contract

Workspace comments support explicit `@handle` mentions. The application parses at most 20 unique handles per comment, case-insensitively, and resolves them only through the host-provided `WorkspaceMentionDirectory`.

The directory adapter must:

- Scope every lookup to the supplied tenant.
- Return active tenant members only.
- Use a stable member/user ID as the notification recipient.
- Provide a stable handle and a display name safe for workspace display.
- Resolve the authenticated actor through the same directory when possible.

Email addresses are not treated as mentions. Unknown, inactive, and cross-tenant handles are ignored. The comment author is not notified about their own mention.

## Authorization

Mention suggestions are page-scoped and require page READ access. Mention recipient resolution occurs only inside comment create/reply workflows that have passed page UPDATE authorization. Tenant and actor IDs are always derived from the trusted Workspace identity provider; they are never accepted in request bodies.

## Durable notification delivery (M31)

The Workspace persistence adapter implements `WorkspaceMentionOutboxStore`. For a comment containing mentions, the comment row, its audit row, and recipient-specific outbox rows are committed in the same JDBC transaction. A rollback therefore removes all of them; a process crash after commit cannot lose the delivery intent.

Each recipient intent uses the stable idempotency key:

`workspace:comment-mention:{commentId}:{recipientId}`

The dispatcher claims due rows using PostgreSQL `FOR UPDATE SKIP LOCKED`, assigns a fresh lease token, and fences delivery acknowledgement, retry, and dead-letter transitions with that token. Expired leases can be reclaimed. Retry attempts use bounded exponential backoff and eventually move to a terminal dead-letter state.

Delivery is **at least once**, not exactly once. If a worker crashes after the downstream Notification provider accepts a command but before Workspace records it as delivered, the command may be sent again. The host Notification adapter must honor the stable idempotency key.

## Host composition

The host provides:

- `WorkspaceMentionDirectory`, backed by its trusted Identity/membership integration.
- `WorkspaceMentionDeliveryPort`, backed by the shared Notification capability.
- A scheduled worker lifecycle that invokes `WorkspaceMentionOutboxDispatcher` with bounded batches and a stable worker identifier.
- `WorkspaceMentionOutboxAdminPolicy`, which authorizes trusted operators to inspect and requeue dead-letter deliveries.

The JDBC persistence module supplies the outbox store when `JdbcWorkspaceStore` is registered. The REST adapter composes `WorkspaceMentionService` when a directory is available; a direct Notification port is optional and retained only for compatibility. New comment workflows prepare recipient intents before persistence and do not synchronously call Notification after commit.

Dead-letter inspection and requeue are exposed through `WorkspaceMentionOutboxOperationsService`. Hosts should expose that application boundary through their chosen authenticated operations transport. The service checks the host-owned admin policy before every read or mutation, scopes store calls by tenant, and uses the trusted actor ID for the transactional requeue audit record. A false requeue result intentionally does not distinguish a missing row from a foreign-tenant or non-dead row.

Workspace does not depend on Notification classes, provider implementations, or a particular scheduler/runtime.

## Web client

The optional browser client calls `GET /api/v1/pages/{pageId}/mention-suggestions?q={prefix}&limit={n}` while a user types an `@handle` token. Selecting a suggestion inserts the canonical handle into the comment body. The REST endpoint returns only the directory's active, tenant-scoped member projection.


## Worker lifecycle and operational health (M33)

The application module exposes `WorkspaceMentionOutboxWorker` as a scheduler-neutral entry point. The host owns the scheduler, cadence, startup/shutdown hooks, stable worker ID, and delivery adapter. Each invocation processes one bounded batch (1..500); overlapping invocations on the same worker instance are skipped rather than competing for the same local worker slot. PostgreSQL lease claims remain the cross-process coordination mechanism, so multiple application instances may run workers safely.

The host should stop scheduling new invocations before application shutdown and allow an in-flight batch to finish within the configured lease/shutdown budget. The worker does not create threads, own a timer, or introduce a runtime dependency.

Operational health is exposed through `WorkspaceMentionOutboxHealthService` and the aggregate-only `WorkspaceMentionOutboxHealthStore` port. The JDBC adapter reports tenant-scoped pending, due, leased, expired-lease, and dead-letter counts plus the oldest pending creation time. It does not select handles, recipient IDs, comment bodies, or notification payloads. The application service authorizes the operator before the store is queried and returns the age of the oldest pending item at a single captured timestamp.

Hosts may publish these values through their existing metrics/health system. Avoid tenant IDs, handles, recipient IDs, comment IDs, or idempotency keys as metric labels; prefer aggregate counters and bounded-cardinality status labels. A rising expired-lease count indicates stalled workers or lease sizing problems; a rising dead-letter count requires the authorized recovery workflow above.
