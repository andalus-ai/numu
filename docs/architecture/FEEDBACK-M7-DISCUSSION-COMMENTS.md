# Feedback M7 — Discussion and comment threads

**Status:** Implementation branch; tests are authored but not yet executed in this environment.

## Goal

Add a reusable discussion stream to Feedback without coupling the capability to Support conversations, a specific UI, or a particular identity provider.

## Scope

- Append-only root comments and replies.
- Explicit `PUBLIC` and `INTERNAL` visibility.
- Opaque actor references; identity remains host-owned and authorization is enforced through a required host-provided `FeedbackCommentAccessPolicy` port.
- Tenant-scoped reads and writes.
- Idempotent comment creation.
- Atomic comment + engagement-outbox persistence.
- Stable chronological retrieval with bounded pagination.

## Invariants

1. A comment always belongs to one tenant and one feedback item.
2. A reply must reference a parent comment from the same tenant and feedback item.
3. A reply must have the same visibility as its parent and cannot predate its parent. Public threads cannot branch into internal notes or vice versa.
4. Public retrieval always requires `PUBLIC` as an explicit visibility predicate; the repository has no nullable/all-visibilities overload.
5. Internal visibility is not authorization. Both write and read use cases require `FeedbackCommentAccessPolicy`; the host adapter must delegate to the Security capability and must not infer permission from actor identifiers.
6. The database enforces tenant/feedback ownership with composite foreign keys, not only application checks.
7. Repeating a tenant-scoped idempotency key returns the original comment ID and does not create a second comment or event.
8. Comment body is not copied into the outbox payload. The event contains identifiers and visibility only.
9. The database composite foreign key also enforces that parent and child visibility match.
10. M7 comments are append-only. Editing, deletion, moderation, attachments and rich-text rendering are separate policy decisions and are not implied by this milestone.

## Persistence

Migration: `V7__feedback_discussion_comments.sql`.

- `feedback_comments` stores comment entries and enforces parent/feedback consistency.
- `feedback_comment_idempotency` scopes idempotency by tenant.
- `feedback_engagement_outbox` receives `feedback.comment-added.v1` in the same transaction.
- Listing uses tenant, feedback, and visibility predicates with a deterministic `created_at, id` order.
- The application query carries the requester and invokes the host authorization port before reading.

M7's migration is numbered V7 because the separate M6 public-invitation branch introduces V6. Apply all migrations in sequence after the M6 integration has been reconciled.

## Build and verification

Run the Feedback reactor from the framework reactor:

```bash
mvn -f core/framework/pom.xml -pl ../../feedback -am test
```

Docker-enabled PostgreSQL tests should verify idempotency, tenant isolation, visibility filtering, same-feedback parent constraints and outbox atomicity. The repository's complete CI and the separate M6 runtime composition remain required before production enablement.
