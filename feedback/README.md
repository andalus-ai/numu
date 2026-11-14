# Numou Feedback Capability

**Milestone:** M6 — public invitation HTTP and Quarkus runtime integration  
**Status:** Domain, submission/application workflows, PostgreSQL persistence, Survey bridge, public respondent HTTP endpoints and shared rate-limit storage are implemented. Host-specific Survey CDI composition and CI/build verification remain required before production enablement.

## Purpose

Feedback is a reusable, tenant-owned capability for comments, bug reports, ideas, error reports, complaints, reviews and future feedback kinds. It is independent of Support and Survey and may be composed into standalone applications, modular monoliths or distributed deployments.

## M6 public invitation API

The optional `feedback-runtime-quarkus` module exposes the following anonymous respondent endpoints:

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/feedback/public/invitations/{token}` | Resolve invitation and return safe survey metadata |
| POST | `/api/v1/feedback/public/invitations/{token}/responses` | Start a response |
| GET | `/api/v1/feedback/public/invitations/{token}/responses/{responseId}` | Read the respondent's own session |
| PUT | `/api/v1/feedback/public/invitations/{token}/responses/{responseId}/answers/{questionId}` | Save or clear one answer |
| POST | `/api/v1/feedback/public/invitations/{token}/responses/{responseId}/submit` | Submit through the invitation quota reservation workflow |

Start request:

```json
{ "respondentId": null }
```

Answer request examples:

```json
{ "text": "My answer" }
{ "number": 8 }
{ "bool": true }
{ "choice": ["option-a"], "other": "" }
{ "ranking": ["option-b", "option-a"] }
{ "matrix": { "row-a": ["column-1"] } }
```

The metadata endpoint returns only a question count; question definitions are returned with the response session after Survey applies conditional visibility. Scoring rules and answer keys are never serialized. The Survey capability remains authoritative for response lifecycle, question visibility, answer validation, scoring and thank-you behavior. Feedback resolves the bearer invitation for every operation, derives the tenant and survey from the persisted invitation, and checks that response IDs belong to that survey. Client-supplied tenant IDs are never accepted.

The raw 256-bit bearer token is never persisted; only its SHA-256 hash is stored. Responses use `Cache-Control: no-store`, `X-Content-Type-Options: nosniff` and `Referrer-Policy: no-referrer`. Configure reverse-proxy access logs to redact the invitation path token because it is a bearer credential.

## Public endpoint abuse controls

- A PostgreSQL-backed fixed-window limiter is shared across application instances.
- The default is 60 requests per minute per valid invitation token; configure with `numou.feedback.public-invitations.requests-per-minute`.
- Disable the endpoint with `numou.feedback.public-invitations.enabled=false`.
- Rate-limit rows older than 48 hours are pruned hourly by the optional Quarkus scheduler.
- The limiter deliberately validates the token before creating a bucket, preventing arbitrary invalid tokens from filling the limiter table.
- This is a per-invitation limit, not a CAPTCHA or per-client bot defense. Production deployments should additionally apply WAF/reverse-proxy abuse controls and optional CAPTCHA/bot verification at the edge.
- Configure the host request-body limit (for example, `quarkus.http.limits.max-body-size=64K`) and redact invitation tokens from reverse-proxy access logs.

## Runtime composition

`feedback-runtime-quarkus` is an optional HTTP/runtime adapter. The host application must provide the Survey `ResponseUseCase` and `SurveyAuthoringUseCase` CDI beans. The Feedback runtime supplies the PostgreSQL invitation repository, Survey bridge, application handlers, rate limiter and public REST resource. It does not silently select Survey's in-memory demo adapters for production.

Apply `V6__feedback_public_invitation_rate_limits.sql` through the host's migration runner before enabling the endpoint. The runtime expects the existing Feedback V1-V5 schema as well.

## Domain and ownership

- `Feedback` owns the original submission and its triage/resolution lifecycle.
- `FeedbackKind` is an extensible validated code, not a closed enum. Initial well-known codes: comment, bug, idea, error-report, complaint, review and other.
- `FeedbackActor` is an opaque principal reference. Feedback does not own User, Customer, Party or Employee identity.
- `FeedbackContext` stores bounded origin references only. It must not contain request bodies, credentials, stack traces or copied business records.
- Every aggregate is tenant-owned through the shared `TenantId` primitive.
- Domain events use the shared `DomainEvent` contract. Tenant, actor, correlation and idempotency metadata belong in the application/integration envelope, not duplicated into the neutral domain event abstraction.
- `FeedbackRepository` is a capability-owned persistence port. Reads require both `TenantId` and `FeedbackId`; adapters must enforce the tenant predicate in the authoritative query.

## Attachments and evidence

- Feedback owns the attachment association, tenant/feedback relationship, display metadata and evidence association lifecycle.
- The Document capability owns binary upload, storage provider selection, stored-byte checksum verification, document versions, retention and authorized downloads.
- Uploads use the existing Document upload-session lifecycle. Feedback does not proxy file bytes or implement a second storage path.
- Feedback stores the Document ID and exact Document version ID. It does not store bucket names, object keys, presigned URLs or binary content.
- A newly linked attachment begins as `PENDING_SCAN`. It must not be exposed as downloadable until the authoritative Document scanning workflow has confirmed it is safe.
- Malware-scanning integration remains a host/Document capability responsibility; do not mark evidence AVAILABLE based only on completed upload.

## Transactional outbox and delivery

- Feedback aggregate persistence and its domain events are committed atomically.
- Attachment metadata and its integration event are committed atomically.
- The PostgreSQL outbox uses `FOR UPDATE SKIP LOCKED`, lease expiry recovery, unique lease tokens (fencing), bounded retries and terminal dead-letter timestamps.
- Delivery is at-least-once. Consumers must deduplicate by `eventId` and enforce tenant authorization; broker acknowledgement is not a substitute for consumer idempotency.

## Lifecycle

```text
SUBMITTED -> TRIAGED
TRIAGED -> ACCEPTED | REJECTED | DUPLICATE
ACCEPTED -> IN_PROGRESS | CLOSED
IN_PROGRESS -> UNDER_REVIEW | RESOLVED | CLOSED
UNDER_REVIEW -> IN_PROGRESS | RESOLVED | CLOSED
RESOLVED -> REOPENED | CLOSED
REOPENED -> IN_PROGRESS | CLOSED
REJECTED | DUPLICATE -> CLOSED
CLOSED -> (terminal)
```

## Integration boundaries

| Capability | Relationship |
| --- | --- |
| Support | Feedback may link to a support case; Support remains the owner of case handling, SLAs and support conversations. |
| Survey | Survey owns respondent sessions, answer validation, scoring and survey definitions. Feedback owns public invitation grants and quota reservation. |
| Notification | Receives application-level delivery commands/events; Feedback domain does not send messages. |
| Workflow / Gamelan | May orchestrate triage and escalation through application contracts; workflow state is not duplicated in the aggregate. |
| Identity / Party / Customer | Actor references are resolved by host authorization and identity capabilities; no identity model is copied here. |
| Documents | Owns binary upload, storage, versions, scanning and authorized downloads. Feedback stores references only. |
| Timeline | May consume published feedback events as a projection; Timeline is not the write model. |
| Developer Portal / Iket | May submit API, SDK and integration feedback using stable kind/context references. |

## Security and privacy invariants

1. Tenant identity is mandatory and immutable on a Feedback aggregate and every attachment association.
2. Authorization is enforced by application use cases, not by possession of a FeedbackId, AttachmentId or DocumentId.
3. Actor references are not proof of authorization.
4. Context values are bounded references only; diagnostic secrets and PII redaction belong at ingestion boundaries.
5. Never trust client-supplied filename, MIME type, size or checksum as evidence metadata; use values returned by Document after upload completion.
6. Never expose a pending, rejected, archived or deleted document as feedback evidence.
7. Public invitation operations derive tenant/survey scope only from a successfully resolved opaque invitation token.
8. Do not log or cache raw invitation tokens or public response bodies containing personal data.
9. Public submission policy, rate limiting, moderation, retention and attachment scanning must not be bypassed by direct persistence access.

## Build

Run the Feedback reactor through the framework reactor:

```bash
mvn -f core/framework/pom.xml -pl ../../feedback -am test
```

For the public API runtime, include the Quarkus runtime module in the host application and provide Survey's application-port beans. Run the host's normal Maven/Quarkus build after applying V6.

PostgreSQL Testcontainers tests cover invitation quota reservations, concurrent rate-limit admission, tenant isolation and existing outbox/attachment behavior. Docker-enabled CI and the complete multi-reactor build must pass before calling this milestone production-verified.
