# Numou Omnichannel — Milestone 23: Validated Adapter Registry

## Objective

Turn M22 provider capability metadata into an enforced wiring boundary so an application cannot accidentally register the generic M21 HMAC verifier for an incompatible or unreviewed protocol.

## Delivered

- `ProviderWebhookAdapterRegistration` binds a reviewed capability declaration to its implementation.
- Registration rejects mismatched provider IDs, unreviewed protocols, unknown account-identity sources, and generic HMAC configurations whose declared protocol needs timestamp or replay validation or otherwise does not match the generic verifier.
- `ProviderWebhookAdapterRegistry` is immutable, rejects duplicate provider IDs, and exposes the verifier list for ingestion wiring.
- Tests cover valid registration, unsafe metadata, verifier/capability mismatch, duplicate providers, and unknown provider lookup.

## Integration

Construct a registry from reviewed registrations and pass `registry.verifiers()` into the existing `InboundWebhookIngestionService` constructor. The ingestion service continues to own verification, inbox deduplication, payload persistence, and the durable VERIFIED transition; the registry does not duplicate those responsibilities or introduce cross-module writes.

Provider-specific verifiers may implement protocols that need timestamp validation, canonical signing strings, key rotation, or replay controls. Their correctness still requires implementation-level tests against official protocol documentation and test vectors; a capability declaration alone does not prove those controls are implemented.

## Security boundaries

- Registration fails closed for unreviewed or unknown account-routing declarations.
- Production account routing must be authenticated/authorized before secret resolution.
- The generic raw-body HMAC verifier is not appropriate when the provider requires timestamp or replay checks that it does not implement.
- This milestone does not claim that a named provider is integrated or production-ready and does not add an HTTP framework or managed-secret backend.

## Validation

Run:

```bash
mvn -pl commerce/omnichannel/numou-omnichannel-core -am test
```

Tests are authored but have not been run in this session. Before enabling a provider, also run integration tests for its HTTP ingress, secret-store implementation, provider-account isolation, duplicate delivery, and protocol-specific replay behavior.
