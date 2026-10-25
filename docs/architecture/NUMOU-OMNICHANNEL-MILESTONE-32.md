# Numou Omnichannel — Milestone 32: Fail-Closed Verifier Security Declarations

## Objective

Prevent a provider adapter from being registered when its reviewed protocol requires timestamp validation or replay protection but its verifier does not explicitly declare those controls.

## Delivered

- Added default-false security declarations to `ProviderWebhookVerifier` for provider timestamp validation and replay protection.
- Registration now rejects a verifier when the corresponding provider capability is required but the verifier does not declare support.
- Preserved the existing generic raw-body HMAC compatibility gate. That verifier remains unsuitable for protocols requiring timestamp or replay controls.
- Added registration tests for missing declarations and for a provider-specific verifier that declares both required controls.

## Security contract

The declarations are fail-closed registration gates, not runtime proof that a verifier is correct. A verifier may return `true` only if it implements the provider's documented behavior and is tested against official protocol vectors, including stale timestamps, replayed requests, malformed signatures, and relevant key-rotation cases.

Replay protection must use the provider-appropriate replay key and atomic reservation semantics where required. An event inbox's external event ID deduplication is useful defense in depth, but it is not automatically equivalent to protocol-level replay protection.

## Compatibility

Existing `ProviderWebhookVerifier` implementations remain source-compatible because the new methods default to `false`. Implementations registered for protocols that require timestamp or replay controls must override the corresponding methods before registration will succeed. This intentionally fails closed rather than silently assuming an older implementation is secure.

## Scope and limitations

This milestone does not add a universal timestamp parser, replay store, or HTTP runtime. Those details depend on the provider's signing protocol and storage/transaction boundary. No provider integration or production security compliance is claimed by the declarations alone.

## Validation

Suggested command:

```bash
mvn -pl commerce/omnichannel/numou-omnichannel-core -am test
```

Tests were added but have not been executed in this authoring session. Confirm the Maven result and CI before treating this milestone as verified.
