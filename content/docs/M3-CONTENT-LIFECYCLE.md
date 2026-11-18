# M3 — Content Lifecycle

## Scope
M3 introduces the editorial state machine and tenant-scoped compare-and-set persistence. It does not implement delivery side effects, scheduled execution, approval identities/policies, publication outbox events or public delivery endpoints; those belong to later workflow/publishing milestones.

## States
- DRAFT: editable working revision.
- IN_REVIEW: submitted for editorial review; content edits are blocked.
- APPROVED: approved for release.
- SCHEDULED: approved and awaiting the scheduling/delivery capability.
- PUBLISHED: editorial state records the selected revision as published; channel delivery is not performed by this milestone.
- UNPUBLISHED: previously published content has been withdrawn.
- ARCHIVED: retained but inactive; restore returns it to DRAFT.

## Allowed transitions
- DRAFT -> IN_REVIEW, ARCHIVED
- IN_REVIEW -> DRAFT, APPROVED, ARCHIVED
- APPROVED -> DRAFT, SCHEDULED, PUBLISHED, ARCHIVED
- SCHEDULED -> APPROVED, PUBLISHED, ARCHIVED
- PUBLISHED -> UNPUBLISHED, ARCHIVED
- UNPUBLISHED -> DRAFT, IN_REVIEW, ARCHIVED
- ARCHIVED -> DRAFT

All other transitions are rejected by the domain policy.

## Invariants
- State transitions do not create or mutate content revisions.
- Revisions remain append-only; editing remains restricted to DRAFT.
- Persistence transitions use tenant + content ID + expected prior status in one conditional SQL update.
- A stale concurrent transition returns a conflict to the application layer; it cannot overwrite a newer state.
- The domain policy is pure and does not replace authorization. Host applications must authorize actor actions before invoking commands.
- PUBLISHED is a lifecycle marker only in M3. Publication delivery, durable outbox, retries, scheduled execution and channel-specific publication records remain later work.

## Migration
V3 expands the status CHECK constraint while preserving existing DRAFT and ARCHIVED rows. No destructive data rewrite is required.

## Verification
Run `mvn -f content/pom.xml test`. CI and PostgreSQL migration execution must pass before merge. This GitHub write session has not run Maven or a live PostgreSQL migration.
