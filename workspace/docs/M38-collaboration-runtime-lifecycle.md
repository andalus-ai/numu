# M38 — Collaboration runtime lifecycle and revocation

## Objective

Make the existing local fanout adapter safely composable by a Quarkus host and ensure a host-side actor/page/tenant revocation closes long-lived SSE connections, rather than merely preventing future deliveries.

## Changes

- Add `WorkspacePageChangeSubscription`, an application-port lifecycle handle with a one-shot host revocation callback.
- Change the subscription port to return that handle. The application API continues to derive tenant/actor from trusted identity, checks PAGE/READ at subscription time and again before each signal, and propagates revocation to the transport.
- The local hub enforces process, tenant, page, and actor connection limits.
- Actor, page, tenant, and runtime shutdown revocation detach registrations and notify transports outside the hub's registry lock.
- Delivery checks the registration's active state under its own lock. Once revocation returns, no subsequent callback can begin.
- Add an optional Quarkus runtime module that composes the local hub as the default CDI implementation of both application ports and disposes it during shutdown. A host can override the default bean with its own implementations.
- The SSE resource closes its connection when the subscription is revoked.

## Configuration

See `numou.workspace.collaboration.*` in the Quarkus runtime README. Defaults bound total connections, per-tenant, per-page, and per-actor subscriptions. The local adapter health snapshot exposes aggregate counters for host metrics.

## Deployment boundaries

This runtime is intentionally single-process. It is suitable for standalone and modular-monolith deployments. A multi-instance deployment must omit the local default and provide shared broker/event-transport implementations of both application ports. The host owns broker authentication, tenant isolation, replay/failure semantics, connection limits across instances, and delivery observability.

Heartbeat scheduling, idle timeout policy, and network-level graceful drain remain responsibilities of the selected HTTP runtime/host. The durable M35 revision feed remains the recovery source; SSE is still an advisory invalidation channel only.
