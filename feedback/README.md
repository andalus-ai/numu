# Numou Feedback Capability

**Milestone:** M2 — attachment evidence and reliable integration events  
**Status:** Domain, application contracts, PostgreSQL attachment persistence, exact Document-version references, and a runtime-neutral outbox relay are implemented. Host runtime scheduling, broker publisher binding, malware-scanning integration, and full CI verification remain pending.

## Purpose

Feedback is a reusable, tenant-owned capability for comments, bug reports, ideas, error reports, complaints, reviews and future feedback kinds. It is independent of Support and Survey and may be composed into standalone applications, modular monoliths or distributed deployments.

## Domain and ownership

- `Feedback` owns the original submission and its triage/resolution lifecycle.
- `FeedbackKind` is an extensible validated code, not a closed enum. Initial well-known codes: comment, bug, idea, error-report, complaint, review and other.
- `FeedbackActor` is an opaque principal reference. Feedback does not own User, Customer, Party or Employee identity.
- `FeedbackContext` stores bounded origin references only. It must not contain request bodies, credentials, stack traces or copied business records.
- Every aggregate is tenant-owned through the shared `TenantId` primitive.
- Domain events use the shared `DomainEvent` contract. Tenant, actor, correlation and idempotency metadata belong in the application/integration envelope, not duplicated into the neutral domain event abstraction.
- `FeedbackRepository` is a capability-owned persistence port. Reads require both `TenantId` and `FeedbackId`; adapters must enforce the tenant predicate in the authoritative query.

## M2 attachment and evidence ownership

- Feedback owns the attachment association, tenant/feedback relationship, display metadata and evidence association lifecycle.
- The Document capability owns binary upload, storage provider selection, stored-byte checksum verification, document versions, retention and authorized downloads.
- Uploads use the existing Document upload-session lifecycle: create an expiring upload session, upload to the returned target, then complete the session. Feedback does not proxy file bytes or implement a second storage path.
- Feedback stores the Document ID and exact Document version ID. It does not store bucket names, object keys, presigned URLs or binary content.
- The host composition implements `FeedbackDocumentPort` using the Document capability's public application contract. The adapter must resolve both document and exact version within the supplied tenant.
- Attachment metadata is persisted in `feedback_attachments`, with a composite tenant/feedback foreign key and tenant-scoped lookup methods.
- A newly linked attachment begins as `PENDING_SCAN`. It must not be exposed as downloadable until the authoritative Document scanning workflow has confirmed it is safe.
- **Scanning integration is not yet implemented.** The current Document lifecycle has ACTIVE/PUBLISHED/ARCHIVED/DELETED states but no malware-scan result contract. Do not mark evidence AVAILABLE based only on a completed upload. Add scanning to Document or integrate a trusted scanner there, then propagate the resulting status/event to Feedback.
- Removing an attachment association does not delete the underlying Document, which may be referenced by other capabilities. Document authorization must be re-evaluated on every download.

## Transactional outbox and delivery

- Feedback aggregate persistence and its domain events are committed atomically.
- Attachment metadata and its integration event are committed atomically.
- The PostgreSQL outbox uses `FOR UPDATE SKIP LOCKED`, lease expiry recovery, unique lease tokens (fencing), bounded retries and terminal dead-letter timestamps.
- `FeedbackOutboxRelay` is runtime-neutral. A host scheduler calls `dispatchOnce`; a host-specific `FeedbackEventPublisher` adapter binds it to the selected broker or event bus.
- Delivery is at-least-once. Consumers must deduplicate by `eventId` and enforce tenant authorization; broker acknowledgement is not a substitute for consumer idempotency.
- Stale workers cannot acknowledge a message after another worker has reclaimed it because updates require the current lease token.
- Dead-letter inspection, replay authorization, operator audit and metrics still need a host operations adapter.

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

A consuming capability maps its workflow to these lifecycle concepts through an explicit policy/adapter. Do not silently widen transitions in the domain.

## Integration boundaries

| Capability | Relationship |
| --- | --- |
| Support | Feedback may link to a support case; Support remains the owner of case handling, SLAs and support conversations. |
| Survey | Feedback may link to a survey response or consume satisfaction metrics; Survey remains the owner of survey definitions, respondent sessions and scoring. |
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
7. Public/anonymous submission policy, rate limiting, moderation, retention and attachment scanning are application/adapter concerns and must not be bypassed by direct persistence access.

## Build

Run the Feedback reactor through the framework reactor:

```bash
mvn -f core/framework/pom.xml -pl ../../feedback -am test
```

The framework parent resolves from `core/framework/pom.xml`. Foundation artifacts must be available in the local Maven repository or built/installed first according to the repository's multi-reactor workflow.

## Persistence layout

- `feedback_items`: canonical Feedback aggregate snapshot.
- `feedback_idempotency`: tenant-scoped submission idempotency keys.
- `feedback_attachments`: tenant-scoped association to exact Document versions.
- `feedback_outbox`: versioned domain/integration events.
- `V1__feedback_foundation.sql`: aggregate, idempotency and initial outbox schema.
- `V2__feedback_attachments.sql`: attachment association schema.
- `V3__feedback_outbox_leases_and_dead_letters.sql`: leases, retry scheduling, fencing tokens and dead-letter state.

PostgreSQL Testcontainers tests cover attachment persistence, tenant isolation, outbox lease recovery and dead-letter handling. They still require execution in CI or a Docker-enabled build environment before this milestone can be called verified.
