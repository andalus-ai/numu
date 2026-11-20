# M38 — Bounded local collaboration fanout

## Local runtime module

`numou-workspace-collaboration-local` provides `LocalWorkspacePageChangeHub`, a single-process adapter implementing both `WorkspacePageChangeNotifier` and `WorkspacePageChangeSubscriptionPort`. Compose the same singleton instance into page-write notification and SSE subscription wiring.

Example construction:

```java
var hub = new LocalWorkspacePageChangeHub(
    2_000, // maximum connections in this process
    250,   // maximum connections for one tenant
    50     // maximum connections for one tenant/page
);
```

The constructor enforces `1 <= maxPerPage <= maxPerTenant <= maxConnections`. Quotas are enforced atomically at subscription registration. Each subscription is identified by tenant, actor, and page; fanout only reaches registrations matching the signal tenant and page.

## Revocation and lifecycle

- `revokeActor(tenantId, actorId)` removes all that actor's subscriptions for the tenant.
- `revokePage(tenantId, pageId)` removes every subscription for a page.
- The application subscription API rechecks PAGE/READ on each delivered signal. If access has been revoked, it closes the subscription and suppresses that signal.
- Closing an individual handle is idempotent. Closing the hub clears registrations and rejects new subscriptions.
- Listener failures are isolated; one consumer cannot prevent delivery to other consumers.

Revocation prevents subsequent fanout after the subscription has been removed. A callback already in progress when revocation begins may finish; hosts requiring strict in-flight cancellation must additionally coordinate connection closure in their transport adapter.

## Health

`health()` returns a snapshot of active connections/pages, published signals, listener delivery failures, rejected subscriptions, and closed state. These counters are process-local and should be exported by the host's metrics integration.

## Deployment boundary

This implementation is for standalone and modular-monolith deployments only. It is not durable and does not distribute events across processes. For multi-instance or microservice deployments, implement the same application ports using the host's existing broker/event infrastructure. The adapter contract carries tenant, actor, and page scope; broker consumers must preserve that scope and avoid publishing page content.

The SSE endpoint remains advisory. The M35 revision feed is the durable source of truth for reconnect and gap recovery. Heartbeats, connection idle timeouts, distributed quotas, broker retry/DLQ, and cluster-wide health remain host-transport responsibilities and are not claimed by this local adapter.
