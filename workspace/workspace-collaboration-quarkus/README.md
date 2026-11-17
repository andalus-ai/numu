# Workspace collaboration — Quarkus runtime

This optional module composes `LocalWorkspacePageChangeHub` as the CDI bean implementing both `WorkspacePageChangeNotifier` and `WorkspacePageChangeSubscriptionPort`. Include it only when the host wants the bounded, single-process implementation.

Configuration (all values are positive integers):

| Property | Default | Meaning |
|---|---:|---|
| `numou.workspace.collaboration.max-connections` | 2000 | Maximum live subscriptions in this process |
| `numou.workspace.collaboration.max-per-tenant` | 200 | Maximum live subscriptions for one tenant |
| `numou.workspace.collaboration.max-per-page` | 50 | Maximum live subscriptions for one tenant/page |
| `numou.workspace.collaboration.max-per-actor` | 20 | Maximum live subscriptions for one tenant/actor |

The hub is closed by CDI disposal during application shutdown. Actor, page, and tenant revocation terminate matching subscriptions and notify the SSE resource so its connection is closed. The hub also exposes an aggregate health snapshot for host health/metrics integration.

This is a single-process adapter, not a distributed broker. For multi-instance deployments, omit this module and compose both application ports with a shared broker/event transport adapter. Do not run multiple local hubs and assume they share subscriptions.
