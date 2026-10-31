# M33 — Page collaboration presence leases

M33 adds advisory page-presence sessions for active collaborators.

## Contract

- Tenant and actor identity come exclusively from the trusted host identity provider.
- A client supplies only an opaque per-tab session UUID; it cannot choose tenant or actor.
- Presence requires page READ permission. It never grants write permission, locks a page, or reserves a block.
- Heartbeats renew a fixed 60-second lease. Stale sessions disappear from reads as soon as the lease expires.
- A user with multiple tabs appears once with an active-session count.
- Session IDs are bound to the authenticated actor. A different actor cannot take over an existing session ID.
- Leaving removes only the caller's own session.
- Presence rows are ephemeral and may be purged in bounded batches; page deletion cascades them.
- No cursor text, selection, document contents, or personal metadata is persisted.

## HTTP

- GET /api/v1/pages/{pageId}/presence
- POST /api/v1/pages/{pageId}/presence/{sessionId}/heartbeat
- DELETE /api/v1/pages/{pageId}/presence/{sessionId}

The initial transport is request/response polling. WebSocket/SSE fan-out and cursor collaboration
are intentionally separate follow-up work; the API contract does not imply real-time push.

## Operations

Run expired-session cleanup periodically using WorkspacePresenceRepository.purgeExpired.
Monitor active session counts and cleanup lag. The TTL remains authoritative if cleanup is delayed.
