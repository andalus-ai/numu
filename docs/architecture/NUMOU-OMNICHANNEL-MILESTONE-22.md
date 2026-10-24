# Numou Omnichannel — Milestone 22: Provider Adapter Readiness

## Objective

Make provider integrations explicitly declare their webhook security protocol and prevent the generic M21 raw-body HMAC verifier from being treated as universal.

## Delivered in this milestone

- `ProviderWebhookCapabilities` declares signature scheme, exact-body semantics, timestamp/replay requirements, secret-rotation support, account identity source, and whether the official protocol was reviewed.
- A deterministic readiness gate exposes stable rejection/readiness codes without including request or secret material.
- Tests cover a compatible protocol, timestamp/replay requirements, canonical-string signatures, unreviewed protocols, unknown account identity, and invalid metadata.
- This milestone remains provider-neutral: it does not assert that a named provider is ready or ship provider-specific credentials, HTTP framework code, or an adapter.

## Readiness policy

The generic `HmacSha256RawBodyWebhookVerifier` is suitable only when all of the following hold:

1. The provider's official documentation and test vectors have been reviewed.
2. The signature is HMAC-SHA-256 over the exact raw request bytes.
3. The signature encoding is hexadecimal and matches the generic verifier configuration.
4. No timestamp validation or replay-protection requirement is being delegated to this verifier.
5. Provider-account routing has a trusted identity source and is authorized before secret lookup.

If timestamp freshness or replay protection is required, a provider-specific or composed verifier must enforce it. A capability declaration is metadata and does not itself perform these checks.

## Secret management and ingress requirements

- Production secret resolvers must use a managed secret store and scope lookup to the exact provider/account pair.
- Secret rotation must be implemented by the resolver or provider-specific key-selection adapter; the boolean capability is declarative only.
- The HTTP ingress adapter must retain exact raw bytes until verification, avoid logging raw body/signature/secret, and only pass verified events to the durable verified-inbox transition.
- Provider-account identity must not be trusted solely because it appears in an unauthenticated body. Resolve or authenticate routing context before secret lookup.
- Unknown or incompatible protocols fail closed; do not fall back to generic HMAC.

## Validation

Run from the repository root:

```bash
mvn -pl commerce/omnichannel/numou-omnichannel-core -am test
```

Tests are authored but have not been run as part of this milestone. Provider release readiness still requires provider-specific test vectors, ingress integration tests, replay tests where applicable, secret-store integration tests, and operational review.
