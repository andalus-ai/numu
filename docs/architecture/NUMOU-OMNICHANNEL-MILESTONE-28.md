# Numou Omnichannel — Milestone 28: Ordered Webhook Ingress

## Objective

Make the safe order of operations reusable across transport adapters rather than relying on every adapter author to remember separate helper calls.

## Delivered

- `WebhookIngressPipeline` validates the parsed header map before reading the body.
- It validates an advertised content length before reading, then always applies the bounded stream reader (including when length is unknown).
- It invokes request construction only after a successful bounded read, and invokes ingestion only after request headers and body are confirmed to match the validated ingress values.
- Regression tests assert malformed headers and declared oversized bodies cannot trigger body reads, request construction, or ingestion; a changed body is rejected before ingestion.

## Adapter usage

Adapters should call this pipeline at the earliest point where they have a parsed header map, body stream, and declared content length. The request factory should perform provider-specific envelope extraction and authorized tenant/account routing after bounded reading. The ingestion function is normally `InboundWebhookIngestionService::ingest`.

## Limits and remaining transport responsibilities

This is a shared orchestration primitive, not a concrete HTTP endpoint. The server/framework and reverse proxy still need request-line, header-count, and header-byte limits before parsing. Adapters must preserve duplicate signature header information long enough to reject or apply the provider's documented policy. Provider-specific body-size requirements must be checked before choosing a deployment limit. The default remains 1 MiB.

## Validation

Run:

```bash
mvn -pl commerce/omnichannel/numou-omnichannel-core -am test
```

Tests were added in this milestone but were not executed during authoring. No CI pass or provider-specific compliance is claimed.
