# M31 — Durable comment mention outbox

Comment rows, audit rows, and recipient-specific outbox rows must commit together. A process
crash after the comment transaction can delay delivery but cannot lose its durable intent.

- WorkspaceMentionOutboxStore is the persistence boundary; JDBC writes share one transaction.
- Claims use fresh per-row lease tokens; completion, retry, and dead-letter operations are fenced.
- Retries use bounded exponential backoff and become terminal at the configured maximum.
- Requeue is an explicit operator action for a dead-letter row.
- Delivery is at-least-once. The host Notification adapter must honor the stable
  workspace:comment-mention:{commentId}:{recipientId} idempotency key.
- Workspace has no direct dependency on Notification or Identity implementations.

Monitor pending age, expired leases, retry counts, and dead-letter volume.
