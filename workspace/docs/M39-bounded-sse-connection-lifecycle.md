# M39 — Bounded SSE connection lifecycle

## Runtime behavior

The REST adapter now composes `WorkspacePageChangeSseResource` with an application-scoped `WorkspaceSseConnectionRegistry`.

Default process-local settings:
- maximum 2,000 concurrent SSE connections;
- heartbeat comment every 20 seconds;
- 60-second write-stall timeout.

The heartbeat is an SSE comment (`: keepalive`) and carries no page data. Page-change events remain content-free and carry the per-page revision ID. Each connection allows only one in-flight write. A page event arriving while a prior write is still pending closes that slow consumer rather than queueing additional frames.

A connection whose writes do not complete within the configured write-timeout window is closed. This is deliberately a **write-stall timeout**, not a claim that the server can detect client-side user inactivity: SSE is a one-way transport and has no application-level client activity signal.

## Admission, cleanup, and shutdown

- New connections are admitted atomically up to the process-wide limit; excess connections receive HTTP 503.
- Closing the SSE sink, a failed write, a slow consumer, a write timeout, or application shutdown closes the registration.
- Subscription cleanup is attached with close-before-attach race handling.
- Heartbeat tasks are cancelled on connection close.
- The registry owns and shuts down its daemon heartbeat scheduler when the CDI application-scoped bean is destroyed.

## Health and operations

`WorkspaceSseConnectionRegistry.health()` exposes a snapshot with:
- active, accepted, and rejected connections;
- successful heartbeat count;
- write timeouts;
- slow-consumer disconnects;
- send failures;
- registry shutdown state.

The host can export these values through its existing metrics/health stack. The adapter does not add a new metrics vendor dependency or expose an unauthenticated public health endpoint.

## Deployment boundary

The connection limit and counters are process-local. They do not coordinate quotas across replicas. The M38 local collaboration hub remains single-process/non-durable. Distributed fanout, cluster-wide quotas, broker retry/DLQ, and shared operational metrics remain responsibilities of a host-provided broker-backed implementation and deployment runtime.

The M35 revision history remains the durable recovery source. Clients must reconcile through `/pages/{pageId}/changes` after every connection or reconnect; heartbeats and SSE event IDs do not replace revision replay.