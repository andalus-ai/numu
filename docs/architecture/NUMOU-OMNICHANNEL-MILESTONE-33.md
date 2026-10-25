# Numou Omnichannel — Milestone 33: Durable Webhook Replay Protection

## Objective

Provide a provider-neutral contract and PostgreSQL implementation for atomic replay-key reservation, separate from inbound event-ID deduplication.

## Delivered

- `WebhookReplayStore` defines an atomic reservation port and `WebhookReplayReservation` validates provider/account scope, SHA-256 key format, and expiry.
- `ReplayProtectedProviderWebhookVerifier` composes with a provider-specific verifier, extracts a provider-defined replay key only after successful signature verification, hashes it, and reserves it before accepting the request.
- Repeated keys return `REPLAY_DETECTED`; missing keys and store failures fail closed with stable non-sensitive reason codes.
- `PostgresWebhookReplayStore` uses a unique constraint and one `INSERT ... ON CONFLICT ... WHERE expires_at <= reserved_at` statement to ensure concurrent callers cannot both reserve the same active key.
- Migration V5 creates the replay reservation table and expiry index.
- Unit tests cover first reservation, duplicate delivery, rejected signatures, missing replay keys, and unavailable storage.

## Protocol responsibilities

The provider-specific verifier still owns signature validation and timestamp policy. The extractor must return only a protocol-defined replay identifier covered by the verified signature (or otherwise authenticated by the provider's documented protocol). Do not use an arbitrary unsigned request header as a replay key. Retention must be configured to cover the provider's replay acceptance window and operational retry behavior.

The wrapper's `enforcesReplayProtection()` declaration is an integration signal, not a substitute for protocol review. Register the wrapper—not the undecorated verifier—for protocols that require replay protection.

## Persistence and operations

PostgreSQL's unique key is the atomic concurrency boundary. The adapter requires auto-commit connections so reservation is committed before returning success. Expired rows can be replaced on subsequent use of the same key; deployments should also schedule bounded cleanup of expired rows to prevent unbounded table growth. The core remains independent of JDBC and PostgreSQL.

Replay reservations are scoped by provider ID and provider-account ID. If a provider's replay identifiers are tenant-scoped rather than account-scoped, the provider integration must ensure account identity is canonical and uniquely scoped; otherwise the persistence contract should be extended to include tenant identity before rollout.

## Validation

- Core unit tests were authored but not run in this session.
- PostgreSQL integration tests for concurrent reservation and expiry reclaim remain required against the supported PostgreSQL version.
- Run the Omnichannel Maven reactor and CI before production use. No provider-specific protocol compliance or production deployment is claimed.
