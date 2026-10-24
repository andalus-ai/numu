# Numou Omnichannel — Milestone 19: Ingestion Reliability Contract Tests

## Summary

This milestone strengthens the provider-neutral ingestion trust boundary with deterministic tests for ordering, persistence failure, duplicate/conflicting identities, and tenant/account scoping. Tests use fake ports and a fixed clock; they do not substitute for PostgreSQL integration tests or provider-specific cryptographic contract tests.

## Coverage added

- Unknown provider IDs are rejected without claiming an inbox row or writing a payload.
- Duplicate and payload-conflict claims do not write another payload or transition the record to verified.
- Payload storage exceptions are mapped to a safe reason code and do not expose exception text or attempt verification transition.
- Event identity differs across tenants and provider accounts even when provider event IDs match.
- The successful path preserves the raw body digest, attaches an opaque payload reference, and uses a deterministic retention deadline.
- Existing ingestion tests already cover invalid-signature rejection and cleanup when the durable verification transition fails.

## Run

```bash
mvn -pl commerce/omnichannel/numou-omnichannel-core -am test
```

PostgreSQL persistence tests and provider-specific signature test vectors must also be run before the reliability release gate can be marked complete. This milestone's core tests do not claim to validate database concurrency or a real provider's signature algorithm.

## Reliability model

The ingestion service verifies the provider request before inbox mutation, claims a tenant/provider/account/event identity, persists the raw body through the payload port, then performs the durable verification transition. The worker remains responsible for dispatch only after verified metadata is available. Delivery is at-least-once: downstream application use cases must atomically enforce idempotency with their business effects.
