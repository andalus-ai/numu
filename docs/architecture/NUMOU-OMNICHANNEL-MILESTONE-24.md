# Numou Omnichannel — Milestone 24: Bounded Webhook Ingress

## Objective

Reject unexpectedly large webhook payloads before signature verification, inbox claims, payload persistence, or verification-state transitions. This limits avoidable CPU and storage work in the provider-neutral ingestion service.

## Delivered

- A configurable maximum raw-body size on `InboundWebhookIngestionService`.
- A default limit of 1 MiB, with constructor overloads for deployments that need a provider-specific limit.
- A payload-free `PAYLOAD_TOO_LARGE` disposition and stable reason code.
- A raw-body length accessor that avoids allocating another defensive copy merely to check the size.
- A regression test proving oversized requests do not invoke the verifier, claim the inbox, or persist a payload.

## Operational requirements

This is a defense-in-depth application-layer check, not a substitute for transport-level limits. The HTTP adapter / reverse proxy must enforce a request-body limit before fully buffering bytes into `InboundWebhookRequest`; otherwise a huge body may already have consumed memory before this service rejects it. Configure the service limit in line with the provider's documented maximum and leave headroom only when required by that protocol.

The limit is measured in raw bytes, not decoded characters. A rejection contains no body, headers, signature, or other request material.

## Validation

Run:

```bash
mvn -pl commerce/omnichannel/numou-omnichannel-core -am test
```

Tests were authored but have not been run in this session. A production rollout still requires transport adapter limits, provider-specific protocol tests, and operational monitoring of `PAYLOAD_TOO_LARGE` without logging payload contents.
