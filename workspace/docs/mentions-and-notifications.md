# Workspace comment mentions

## Contract

Workspace comments support explicit `@handle` mentions. The application parses at most 20 unique handles per comment, case-insensitively, and resolves them only through a host-provided `WorkspaceMentionDirectory`.

The directory adapter must:
- Scope every lookup to the supplied tenant.
- Return active tenant members only.
- Use a stable member/user ID as the notification recipient.
- Provide a stable handle and a display name safe for workspace display.
- Resolve the authenticated actor through the same directory when possible.

Email addresses are not treated as mentions. Unknown, inactive, and cross-tenant handles are ignored. The comment author is not notified about their own mention.

## Authorization

Mention suggestions are page-scoped and require page READ access. Mention notifications are issued only after a comment create/reply workflow has already passed page UPDATE authorization. Tenant and actor IDs are always derived from the trusted Workspace identity provider; they are never accepted in request bodies.

## Notification integration

Workspace defines `WorkspaceMentionNotificationPort` as an application-owned outbound port. The host composes it with the shared Notification capability. Each notification command carries a stable idempotency key:

`workspace:comment-mention:{commentId}:{recipientId}`

The Notification adapter should persist recipient-scoped idempotently and own inbox state, delivery retries, push providers, and transport policy. Workspace does not import Notification classes or depend on provider implementations.

The current bridge is synchronous after the comment has been committed. A host that requires crash-safe at-least-once dispatch should implement the port through its durable integration/outbox mechanism; the current Workspace milestone does not claim a cross-service transactional outbox.

## Web client

The optional browser client calls `GET /api/v1/pages/{pageId}/mention-suggestions?q={prefix}&limit={n}` while a user types an `@handle` token. Selecting a suggestion inserts the canonical handle into the comment body. The REST endpoint returns only the directory's active, tenant-scoped member projection.
