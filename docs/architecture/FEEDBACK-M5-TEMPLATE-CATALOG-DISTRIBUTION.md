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

Migration V5__feedback_survey_catalog_distribution.sql adds tenant-scoped template and distribution tables. The template key prevents version replacement. Token hashes are globally unique to prevent ambiguous invitation resolution.

## Security and integration notes

- Resolve tenant from authenticated context for authoring and administration; do not accept a tenant ID from an unauthenticated public request.
- Public invitation resolution must hash the supplied token before lookup, apply rate limiting, and enforce expiry and response quota atomically at the Survey submission boundary.
- Never log or emit the raw token. Store only the hash.
- QR codes should encode the same public URL/token and must not create a second authorization mechanism.
- Email and notification delivery consume the transactional outbox; they must not be called inline from the database transaction.

## Verification status

Domain unit tests have been added for checksum stability, publish transitions, invalid versions, invitation expiry, revocation and response-limit predicates. They have not been executed. PostgreSQL integration tests are still pending for immutable-version conflicts, tenant isolation, token non-persistence, quota races and outbox atomicity. CI has not been run for this slice.


## Migration ordering correction

The feedback PostgreSQL migration directory previously contained two V2 migrations. This branch restores a unique, dependency-ordered sequence: V1 foundation, V2 attachments, V3 ratings/reviews, V4 outbox leases/dead letters, V5 survey catalog/distribution. This avoids Flyway's duplicate-version failure on a clean install.


## Public invitation submission and quota enforcement

The follow-up implementation adds an application-level submission coordinator:

- Resolve the bearer token by hashing it and looking up an active, unexpired distribution.
- Reserve one response slot in PostgreSQL before calling the Survey submission use case.
- Bind the response to the distribution's tenant and survey ID; the Survey adapter verifies the response belongs to that survey.
- Commit the reservation only after Survey accepts the submission. Release the reservation on synchronous failure.
- A response claim is unique by tenant + distribution + response ID. Repeated completed submissions return an idempotent outcome.
- Reservation and completion operations lock the distribution row before the claim row. This consistent lock order avoids a claim/distribution deadlock and serializes competing submissions.
- The quota constraint is enforced in the database with accepted + reserved <= max responses, so concurrent requests cannot oversubscribe an invitation.
- A reservation older than five minutes can be reclaimed. The Survey bridge treats an already-submitted response as success, covering a crash after Survey commits but before Feedback finalizes the claim.
- Revocation is tenant-scoped and emits feedback.survey-distribution-revoked.v1 in the same transaction.

### Important runtime integration boundary

The application handler and Survey submission bridge are implemented, but the HTTP resource/route and CDI/runtime registration are not yet mounted. The runtime should expose invitation resolution and response submission through the public API, apply rate limiting and bot protection, and map invalid/expired tokens to a non-enumerating 404 response and quota exhaustion to 410/409. Survey's own response start/answer endpoints must use the same verified invitation context; they must not accept an arbitrary tenant or survey ID from the public caller.

### Recovery semantics

A reservation is a small cross-capability protocol, not a distributed transaction. Survey owns the response commit; Feedback owns invitation quota. The five-minute claim lease plus idempotent Survey bridge makes retry recovery possible. Production runtime should monitor stale RESERVED claims and alert if they persist beyond the lease. If Survey and Feedback share one transaction manager in a deployment profile, a future adapter may collapse both steps into one local transaction.
