# Numou Omnichannel — Milestone 21: Provider Signature Verification Contracts

## Goal

Provide a reusable, provider-neutral implementation for the specific webhook signing protocol that computes HMAC-SHA-256 over the exact raw request body, while preserving a provider-specific verifier port for protocols with different canonicalization or freshness rules.

## Changes

- Add `ProviderWebhookSecretResolver`, keyed by provider ID and provider-account ID. Production implementations must use managed secret storage and enforce account-scoped lookup.
- Add `HmacSha256RawBodyWebhookVerifier`, configurable by provider ID, signature header, and documented signature prefix.
- Compare signatures with `MessageDigest.isEqual`; reject missing, ambiguous, malformed, invalid, mismatched-provider, and unavailable-secret cases using safe reason codes.
- Add deterministic test vectors for valid signatures, body tampering, header case-insensitivity, missing/malformed/ambiguous headers, provider mismatch, unavailable secrets, and resolver failure.

## Protocol boundary

This verifier is suitable only when a provider's documented scheme signs the raw body with HMAC-SHA-256 and a hexadecimal digest. It does not implement timestamped signatures, request canonicalization, key rotation policy, or provider-specific replay windows. Providers requiring those features must implement `ProviderWebhookVerifier` with their exact protocol. Durable event identity/deduplication remains the inbox's responsibility and is not a substitute for provider timestamp validation where required.

## Security requirements for integrations

- Authenticate and authorize provider-account routing before invoking verification; do not trust caller-supplied account IDs.
- Resolve the secret by both provider ID and provider-account ID. Never use one global secret for multiple accounts unless the provider protocol explicitly defines that scope.
- Store secrets in a managed secret system; do not hardcode secrets or include them in logs, exceptions, metrics, or responses.
- Preserve raw request bytes before JSON parsing or reserialization.
- Keep provider-specific signature headers and canonicalization rules in the adapter configuration/implementation.
- Run the module tests and provider-specific contract tests before enabling a provider in production.

## Validation

Tests were authored but not run in this session. Run:

```bash
mvn -pl commerce/omnichannel/numou-omnichannel-core -am test
```

No production provider adapter is claimed by this milestone; it supplies a reusable verifier primitive and contract tests for compatible provider protocols.
