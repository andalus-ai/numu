# Workspace collaboration — Quarkus runtime

This optional module composes `LocalWorkspacePageChangeHub` as the CDI bean implementing both `WorkspacePageChangeNotifier` and `WorkspacePageChangeSubscriptionPort`. Include it only when the host wants the bounded, single-process implementation.

Configuration (all values are positive integers):

| Property | Default | Meaning |
|---|---:|---|
| `numou.workspace.collaboration.max-connections` | 2000 | Maximum live subscriptions in this process |
| `numou.workspace.collaboration.max-per-tenant` | 200 | Maximum live subscriptions for one tenant |
| `numou.workspace.collaboration.max-per-page` | 50 | Maximum live subscriptions for one tenant/page |
| `numou.workspace.collaboration.max-per-actor` | 20 | Maximum live subscriptions for one tenant/actor |
| `numou.workspace.collaboration.heartbeat-interval-seconds` | 25 | SSE comment heartbeat interval (5–300 seconds) |

The hub and shared heartbeat scheduler are closed by CDI disposal during application shutdown. The scheduler uses one daemon thread for all active SSE connections; each connection holds a cancellable recurring task rather than owning a thread. Heartbeats are SSE comment frames, skipped while a page-change frame is in flight, and do not create browser message events. Scheduler health exposes active stream count, callback count, callback failures, and shutdown state. Actor, page, and tenant revocation terminate matching subscriptions and notify the SSE resource so its connection is closed. The hub also exposes an aggregate health snapshot for host health/metrics integration.

This is a single-process adapter, not a distributed broker. For multi-instance deployments, omit this module and compose both application ports with a shared broker/event transport adapter. Do not run multiple local hubs and assume they share subscriptions.


The REST adapter requires a `WorkspaceSseHeartbeatScheduler` host bean. This Quarkus runtime supplies a shared scheduler with a configurable interval. A scheduler setup failure closes the SSE stream and its page subscription rather than leaving an unmaintained connection. The optional REST exception mapper converts local-hub quota rejections to HTTP `429 Too Many Requests`. Hosts using a different transport should map `SubscriptionLimitExceededException` to their equivalent capacity response.
