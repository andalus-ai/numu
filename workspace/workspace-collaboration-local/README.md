# Workspace collaboration — local runtime

`LocalWorkspacePageChangeHub` implements both application ports:
- `WorkspacePageChangeNotifier` for content-free page revision invalidations;
- `WorkspacePageChangeSubscriptionPort` for tenant/actor/page-scoped subscriptions.

It is intended for standalone and modular-monolith deployments running one process. It is not a distributed broker and does not synchronize multiple application instances.

## Resource limits

The hub enforces process-wide, per-tenant, per-page, and per-actor connection quotas. A rejected subscription throws `SubscriptionLimitExceededException`; the host REST exception mapper should translate that to an explicit capacity response (for example HTTP 429 or 503), not a generic success.

## Revocation and shutdown

`revokeActor(tenantId, actorId)`, `revokePage(tenantId, pageId)`, and `revokeTenant(tenantId)` remove registrations and invoke each subscription's revocation callback. The SSE adapter closes the associated connection immediately. Hub `close()` revokes all active registrations and rejects new subscriptions.

A subscription close initiated by the client is distinct from a host revocation and does not call the revocation callback.

## Health

`health()` returns aggregate active connection/page counts and published, failed, and rejected counters. A host may map this snapshot to its metrics/health framework without coupling this adapter to a specific observability vendor.
