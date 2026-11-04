# M36 — Page change invalidation signals

## Objective

Provide an optional, transport-neutral notification hook after a page revision has committed. Browser and desktop clients can use the signal to request the new snapshot from the M35 revision feed rather than polling continuously.

## Event contract

`WorkspacePageChangeSignal` contains only tenant, workspace, space, page, revision, actor, change type, and occurrence time. It excludes page title, block text, attachments, comments, and other content. A signal is not an authorization grant and is not a replacement for a page read.

Change types are `CREATED`, `UPDATED`, and `RESTORED`. The revision is the authoritative per-page cursor. The signal is emitted only after the atomic page + revision + audit persistence operation reports success.

## Delivery semantics

- Notification is advisory and best-effort. A missing or delayed signal cannot lose page changes.
- Clients recover by calling the M35 changes endpoint with their last fully applied revision.
- A notifier must be non-blocking, isolate transport failures from the committed page command, enforce tenant-scoped subscriptions, and publish across instances if the host requires multi-instance fanout.
- Host adapters may use in-process fanout, SSE, WebSocket, or a broker. No transport SDK or broker dependency enters Workspace domain/application.
- The host notifier owns connection lifecycle, authentication binding, subscription authorization, backpressure, rate limits, and operational metrics.
- The signal contains no content. A receiving client must re-authorize its subsequent M35 page read; possession of a signal or page ID is not permission.
- Duplicate, reordered, or missed signals are harmless. Clients compare revisions and replay from their durable cursor.

## Scope boundary

This milestone does not introduce a built-in WebSocket/SSE server or claim cross-instance delivery without a host adapter. It establishes the event contract and post-commit application hook. A future transport adapter can implement the notifier without changing page persistence or the M35 replay contract.
