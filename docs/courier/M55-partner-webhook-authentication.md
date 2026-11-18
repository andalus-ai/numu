# M55 — Partner webhook authentication boundary

## Objective

Authenticate third-party webhook bytes before constructing a `PartnerWebhookEvent`
or calling `JdbcCourierNetworkRepository.recordInboundEvent`. The existing inbox
unique key provides delivery deduplication; it does not authenticate the sender
or prevent replay of an old signed request.

## Contract added

- `CourierWebhookSecretResolver` resolves key material by trusted tenant, partner
  identity and opaque secret reference. The host implements it using the configured
  secret manager; raw credentials are not stored in Courier tables.
- `PartnerWebhookSignatureVerifier` verifies HMAC-SHA256 against the exact raw
  request bytes using `timestamp + "." + body`.
- The signature header is `t=<unix-seconds>,v1=<64 lowercase hex chars>`.
- Timestamps outside the caller-supplied tolerance are rejected. Tolerance must
  be positive and no greater than ten minutes.
- Request bodies are bounded to 1 MiB; signing keys must contain at least 32 bytes.
- Comparison uses `MessageDigest.isEqual`; the verifier never parses and reserializes
  JSON before verification.

## Required inbound adapter order

1. Resolve tenant and partner from authenticated route/credential configuration,
   never from the JSON body.
2. Read and bound the raw body once.
3. Resolve the partner's signing key through `CourierWebhookSecretResolver`.
4. Verify signature and timestamp against the raw bytes.
5. Parse and validate the event envelope, including tenant/partner binding and
   allowed event types.
6. Persist to the existing tenant-scoped idempotent inbox.
7. Return success for a verified duplicate; enqueue only newly inserted events.

No adapter currently wires this full HTTP ingress flow. This milestone provides
the cryptographic contract and secret-resolution port; production activation still
requires an inbound transport adapter, route-level authentication/rate limiting,
partner status and event-type checks, and PostgreSQL inbox integration tests.

## Security notes

- HMAC proves possession of a shared key, not business authorization. Partner
  status, tenant binding, route policy, payload schema and allowed event type
  remain mandatory checks.
- Timestamp freshness limits replay age but does not replace inbox idempotency.
- Secret resolver implementations must not log key material and should return
  a fresh byte array that the caller can clear after use.
- Never trust forwarded tenant IDs or partner IDs supplied only in payload fields.

## Verification

Unit tests cover valid signatures, body tampering, expired/future timestamps,
malformed headers, weak keys, body limits and excessive tolerance. Run the Courier
reactor from `logistics/pom.xml`; repository-wide CI currently has unrelated
reactor compilation failures, so report narrow results separately.
