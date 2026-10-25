# Numou Omnichannel — Milestone 27: Bounded Stream Ingress

## Objective

Give HTTP and webhook transport adapters a small, transport-neutral mechanism to reject oversized bodies before they are fully buffered or passed into signature verification and inbox ingestion.

## Delivered

- `WebhookIngressLimits` provides a configurable body limit, defaulting to 1 MiB.
- `validateDeclaredContentLength` rejects a declared length over the limit before the adapter reads the body; `-1` represents unknown length.
- `readBounded` enforces the same maximum even when the declared length is absent or inaccurate. It consumes at most one byte beyond the limit to detect overflow and does not close the caller-owned stream.
- Tests cover exactly-at-limit bodies, early rejection by declared length, unknown-length overflow, bounded consumption, and invalid configuration.

## Required adapter sequence

1. Apply the HTTP server/framework's header count and byte limits before parsing/buffering where supported.
2. Preserve duplicate signature-header information and apply the provider's documented multiplicity rule.
3. Call `validateDeclaredContentLength` when a content length is available.
4. Read through `readBounded`; do not call an unbounded `readAllBytes` first.
5. Only after successful bounded reading, resolve tenant/provider account and construct `InboundWebhookRequest`.
6. Pass the request to `InboundWebhookIngestionService`.

## Boundary and rollout

This helper does not configure any particular HTTP framework, reverse proxy, or gateway; no concrete HTTP adapter was found in the core module. Deployments must wire it into the actual transport adapter and separately configure server/proxy header limits. The 1 MiB default should be reconciled with each provider's documented maximum payload before rollout.

## Validation

Run:

```bash
mvn -pl commerce/omnichannel/numou-omnichannel-core -am test
```

Tests are authored but have not been executed in this session. No CI pass or runtime-adapter integration is claimed.
