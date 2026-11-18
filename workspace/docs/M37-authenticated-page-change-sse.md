# M37 — Authenticated page-change SSE transport

## Endpoint

`GET /api/v1/pages/{pageId}/events` is an optional Server-Sent Events endpoint. It emits `page-change` events with:
- SSE `id`: the page's monotonic revision;
- `pageId`, `revision`, `changeType`, and `occurredAt` in JSON data.

No page title, block text, comment, attachment metadata, tenant identifier, or other page content is sent. The page ID is a routing hint, not an authorization grant.

## Security and composition

- The application API derives tenant and actor only from the host's trusted `IdentityProvider`.
- It resolves the page within that tenant and requires `PAGE/READ` before registering the subscription.
- Every delivered signal is defensively checked against the authorized tenant and page.
- The REST resource accepts no tenant or actor query parameter.
- The host must compose `WorkspacePageChangeSubscriptionPort` with an implementation that enforces tenant/page isolation, subscriber quotas, lifecycle cleanup, and cross-instance fanout when multiple instances are deployed.
- No in-memory-only broker is supplied as a production default. No broker SDK or SSE dependency enters the Workspace domain/application contracts.
- The resource permits only one in-flight SSE write per connection. If a consumer falls behind, the connection is closed; clients reconnect and replay rather than accumulating an unbounded queue.

## Reconnect and recovery

The SSE event ID is the page revision, not a globally ordered event sequence. It is scoped to the page URL. `Last-Event-ID` is validated as a non-negative revision hint but is not used to claim durable replay. On initial connect and every reconnect, clients must call the M35 endpoint:

`GET /api/v1/pages/{pageId}/changes?afterRevision={lastFullyAppliedRevision}&limit={n}`

Apply the returned snapshots in ascending order and persist `nextCursor` only after the entire batch is applied. SSE may be duplicated, delayed, reordered, or missed; it only prompts a bounded replay. The durable revision feed remains authoritative.

## Operational responsibilities

The host transport implementation owns connection authentication lifecycle, authorization revocation handling, connection limits, heartbeat/idle timeout policy, broker fanout, metrics, and shutdown draining. If access is revoked after connection establishment, the host must terminate affected subscriptions promptly; the REST adapter's initial READ check is not a substitute for revocation propagation.

This milestone adds an optional SSE adapter and a host-owned subscription contract. It does not claim a ready-made distributed broker or automatic heartbeat scheduler.
