# Numou Feedback Capability

**Milestone:** M0 — domain, application and persistence foundation  
**Status:** Canonical aggregate, application submission contract, PostgreSQL persistence and transactional outbox schema implemented; HTTP transport, runtime registration and outbox relay remain pending.

## M2 evidence ownership

- Feedback owns the attachment association, tenant/feedback relationship, display metadata and moderation/scanning state.
- The Document capability owns binary upload, storage provider selection, checksums verified against stored bytes, document versions, retention and download authorization.
- Uploads use the existing Document upload-session lifecycle: create an expiring upload session, upload to the returned target, then complete the session. Feedback does not proxy file bytes or implement a second storage path.
- Feedback stores the Document ID and exact Document version ID. It does not store bucket names, object keys, presigned URLs or binary content.
- The host composition implements FeedbackDocumentPort using the Document capability's public application contract. The adapter must resolve both document and version within the supplied tenant.
- New associations begin as PENDING_SCAN. They must not be exposed as downloadable until a trusted malware/content inspection workflow marks them AVAILABLE.
- REJECTED and DELETED associations remain in audit history. Removing an association does not delete the underlying Document, which may be referenced by other capabilities.
- Document download authorization must be re-evaluated by Document on every access; possession of a FeedbackAttachment ID is not authorization.

## Security requirements

1. Tenant identity is mandatory for both Feedback and Document lookup.
2. The Document adapter must query by tenant and document ID, then verify the exact version belongs to that document.
3. Do not accept a document reference from a client as proof of access. The host must authorize the actor against the feedback item before invoking the use case.
4. Never trust a client-supplied filename, MIME type, size or checksum as evidence metadata; use values returned by the Document capability after upload completion.
5. Do not proxy arbitrary presigned URLs through Feedback. Use the Document capability's authorized download flow.
6. Scanning is asynchronous and fail-closed: pending, rejected, archived and deleted documents cannot be served as feedback evidence.

## Current application contracts

- FeedbackAttachmentRepository: tenant-scoped metadata persistence.
- FeedbackDocumentPort: lookup of an exact, tenant-scoped Document version and authoritative metadata.
- AddFeedbackAttachmentHandler: validates the feedback and Document version, then stores only the association metadata.

The FeedbackDocumentPort adapter is a composition concern. It must use the Document public application/domain contracts, never Document persistence entities or tables. PostgreSQL attachment metadata is stored in `feedback_attachments` by the feedback-postgres-adapter, with a composite tenant/feedback foreign key, exact document-version uniqueness, and a pending-scan index. Attachment creation and lifecycle transitions append versioned integration records to the existing `feedback_outbox` in the same database transaction; unchanged replays do not emit duplicate events.

## Existing Feedback domain and integration boundaries

- Feedback owns original submissions and triage/resolution lifecycle.
- Feedback types remain extensible; initial use cases include comments, bugs, ideas, error reports, complaints and reviews.
- Actor identity is an opaque principal reference; Feedback does not own User, Customer, Party or Employee identity.
- Support owns support cases and SLAs; Survey owns survey definitions and respondent sessions.
- Notification receives application-level delivery commands/events; Feedback domain does not send messages.
- Workflow / Gamelan may orchestrate triage and escalation through application contracts.
- Timeline may consume published feedback events as a projection.
- Developer Portal / Iket may submit API, SDK and integration feedback using stable kind/context references.

## Build

The separate Maven reactor is included in the framework reactor and follows the repository's top-level capability pattern:

```bash
mvn -f core/framework/pom.xml -pl ../../feedback -am test
```

The framework parent resolves from `core/framework/pom.xml`. The foundation artifacts must be available in the local Maven repository or built/installed first according to the repository's multi-reactor workflow.


## Persistence foundation (M0)

The PostgreSQL adapter owns the Feedback write schema and migration:

- `feedback_items` stores the canonical `Feedback` aggregate snapshot, including opaque actor reference, extensible kind code and bounded origin context.
- `feedback_idempotency` scopes submission keys by tenant and resolves concurrent retries to the original feedback ID.
- `feedback_outbox` stores versioned domain events in the same transaction as the aggregate and idempotency mapping.
- Every item read includes both `tenant_id` and `id` predicates.
- Rehydration uses the canonical `Feedback` aggregate and does not emit a second `FeedbackSubmitted` event.

The adapter currently persists new submissions and reads them back. Lifecycle update persistence, outbox dispatch/retry workers, transport endpoints, runtime composition, and CI execution evidence remain follow-up work. The PostgreSQL Testcontainers integration tests are present but have not yet been run in this implementation session.
