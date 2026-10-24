# Numou Omnichannel — Milestone 8: Verified Payload and Dispatch Boundaries

## Goal

Define provider-neutral contracts for authenticity verification, short-lived payload retention, and dispatch to existing Numou application use cases. Provider SDKs and transport frameworks remain optional adapters.

## Contracts introduced

- `ProviderWebhookVerificationRequest`: transient provider/account, headers, and raw body; body bytes are defensively copied.
- `ProviderWebhookVerifier` and `WebhookVerificationResult`: provider-specific signature verification with safe machine-readable outcomes.
- `InboundPayloadStore` and `InboundPayloadReference`: opaque, tenant-scoped payload storage and lifecycle contract.
- `VerifiedInboundEvent`: metadata plus opaque payload reference, never embedded payload bytes.
- `InboundEventDispatcher`: adapter boundary to existing application ports/use cases.

## Required ingress sequence

1. Resolve provider account and tenant/branch from trusted configuration and authenticated credentials; never trust caller-supplied tenant/branch identifiers.
2. Enforce request-size and content-type limits before buffering a body.
3. Verify authenticity using the provider's documented algorithm and timestamp/replay rules. Reject before dispatch when verification fails.
4. Compute SHA-256 over the exact raw bytes used for signature verification.
5. Store payload using tenant-scoped authorization, encryption at rest, a bounded retention deadline, and restricted access.
6. Claim the event in the durable inbox. Same key/same digest is a duplicate; same key/different digest is a security/consistency conflict.
7. Transition the inbox row from RECEIVED to VERIFIED only after verification and payload persistence are complete.
8. Dispatch through existing application ports. Use domain-level idempotency and/or an outbox for business effects.
9. Record sanitized outcomes and delete payload bytes according to retention policy.

## Important integration gap

These contracts do not by themselves implement the ingress orchestration or the durable RECEIVED-to-VERIFIED transition. The existing inbox adapter inserts RECEIVED rows and the worker claims only VERIFIED rows. A follow-up persistence change must add an atomic, digest-checked transition and persist the payload reference before an end-to-end handler is enabled. Do not mark events verified merely because a signature check succeeded in memory.

## Security and privacy requirements

- Never log raw bodies, authorization headers, signatures, access tokens, or provider secrets.
- Error codes and audit records must be sanitized and must not include raw provider response bodies.
- Enforce tenant isolation on every payload read/delete and operator replay action.
- Store only opaque payload references in event metadata; do not store public object URLs or credentials.
- Set finite retention deadlines and provide deletion/expiry monitoring.
- Provider verification must include replay-window checks where supported; deduplication alone does not replace signature freshness checks.
- Dispatch only after tenant/branch authorization and successful verification.

## Scope limits

This milestone defines provider-neutral ports only. It does not claim a concrete provider signature implementation, encryption adapter, webhook endpoint, durable state transition, scheduler, or business-use-case integration. Those must be implemented and tested against the actual provider contracts and existing application ports before production use.

## Validation status

Compilation and tests must be run in CI. Contract tests should cover defensive copying, malformed verification outcomes, tenant-scoped payload access, digest mismatch rejection, expired payload deletion, duplicate event handling, and dispatch idempotency. Provider-specific tests must use official signature fixtures and replay-window edge cases.
