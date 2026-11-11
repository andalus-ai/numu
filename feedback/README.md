# Numou Feedback Capability

**Milestone:** M2 — attachment and evidence integration  
**Status:** Document-reference contracts and PostgreSQL attachment metadata persistence are implemented. Host composition and scanning integration remain pending.

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

The FeedbackDocumentPort adapter is a composition concern. It must use the Document public application/domain contracts, never Document persistence entities or tables. PostgreSQL attachment metadata is stored in `feedback_attachments` by the feedback-postgres-adapter, with a composite tenant/feedback foreign key, exact document-version uniqueness, and a pending-scan index.

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

Run the Feedback reactor with `mvn -f feedback/pom.xml test`. The framework parent resolves from `core/framework/pom.xml`; foundation artifacts must be available in the local Maven repository or built/installed first.