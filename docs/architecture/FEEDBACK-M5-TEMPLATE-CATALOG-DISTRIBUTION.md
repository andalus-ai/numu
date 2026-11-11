# Feedback M5 — Template catalog and distribution foundations

## Scope

This slice adds tenant-owned, immutable, versioned snapshots of existing Survey definitions and secure invitation/distribution grants. Survey remains the owner of question types, conditional logic, survey lifecycle and response validation. Feedback stores an opaque JSON snapshot and checksum; it does not fork Survey's model.

## Template catalog

- Key: tenant + template code + version.
- Versions are immutable. A changed definition requires a new version.
- SHA-256 is computed over the exact UTF-8 serialized Survey definition and checked on hydration.
- Draft and published states are explicit; a published version requires a publication timestamp.
- Latest-published lookup and bounded tenant-scoped listing are supported.

## Distribution grants

- A distribution references a Survey ID without introducing a cross-capability database foreign key.
- Channels: public link, QR code, email and notification.
- A 256-bit random bearer token is returned once. Only its SHA-256 hash is persisted.
- Grants carry expiry and maximum response limits; the acceptance predicate is domain-level and does not bypass Survey's response-submission rules.
- Distribution creation and feedback.survey-distribution-created.v1 outbox insertion share one database transaction.
- Email/notification delivery adapters, QR rendering and public HTTP endpoints remain separate composition/runtime work; this layer only issues durable grants.

## Persistence

Migration V4__feedback_survey_catalog_distribution.sql adds tenant-scoped template and distribution tables. The template key prevents version replacement. Token hashes are globally unique to prevent ambiguous invitation resolution.

## Security and integration notes

- Resolve tenant from authenticated context for authoring and administration; do not accept a tenant ID from an unauthenticated public request.
- Public invitation resolution must hash the supplied token before lookup, apply rate limiting, and enforce expiry and response quota atomically at the Survey submission boundary.
- Never log or emit the raw token. Store only the hash.
- QR codes should encode the same public URL/token and must not create a second authorization mechanism.
- Email and notification delivery consume the transactional outbox; they must not be called inline from the database transaction.

## Verification status

Unit and PostgreSQL integration tests still need to be added/run for checksum stability, immutable-version conflicts, tenant isolation, token non-persistence, expiry, quota races and outbox atomicity. CI has not been run for this slice.
