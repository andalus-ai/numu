# M35 — Resumable page revision synchronization

## Purpose

Provide a transport-neutral, bounded change feed so browser, desktop, and remote clients can recover page content changes after polling gaps, tab suspension, reconnects, or transient network failures. This reuses the existing immutable revision history rather than introducing a second event store.

## Contract

- GET /api/v1/pages/{pageId}/changes?afterRevision={cursor}&limit={n} returns immutable page revision snapshots after the supplied per-page revision cursor.
- The cursor is the page's existing monotonic revision number. Revision snapshots are committed with page mutations, so the feed cannot acknowledge a page revision that has no stored snapshot.
- Results are ordered by ascending revision and bounded to 1–100 items (default 50).
- nextCursor advances only to the last returned revision. Clients persist it only after applying the entire returned batch successfully.
- hasMore=true means another bounded request should be made immediately with afterRevision=nextCursor.
- currentRevision is the page revision observed when the request began. The database query is bounded to that revision, so a concurrent later write is picked up by the next poll.
- Tenant and actor are derived from trusted host identity. The application checks page READ permission before querying revision history.
- Future, negative, or invalid cursors/limits are rejected. Foreign-tenant page IDs do not disclose page contents.
- Revision snapshots are immutable and include block IDs, typed content, actor, timestamp, and change summary. They are snapshots, not partial patches.
- This endpoint is pull/replay, not push delivery or concurrent cursor editing. Presence remains advisory and independent.

## Client recovery

1. Keep the last fully applied revision as the cursor.
2. Request changes after that cursor.
3. Apply snapshots in ascending order; do not advance the cursor if any snapshot fails to apply.
4. Persist nextCursor only after the batch is committed locally.
5. If hasMore, fetch the next batch; otherwise continue polling or use a host push transport to trigger a poll.
6. If the server reports a cursor beyond currentRevision, reconcile from revision zero or the last known safe checkpoint.

## Deployment and compatibility

Workspace owns revision and authorization semantics. REST is one optional adapter; a future SSE/WebSocket adapter may notify clients to poll this same cursor contract. Standalone hosts and modular monoliths can call the application API directly. No transport-specific dependency enters domain/application code.

The feed intentionally excludes comment-thread changes, presence heartbeats, and structured-database events; those have distinct lifecycles and must not be conflated with page content revisions.
