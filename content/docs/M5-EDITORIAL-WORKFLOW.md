# M5 — Editorial Workflow

## Ownership
CMS owns editorial policy snapshots, reviewer assignments, review decisions, comments, and the decision that a particular immutable content revision is approved. Gamelan schedules tasks and coordinates long-running work but cannot approve content, mutate CMS state, or make a revision publishable by itself.

## Implemented domain and application behavior
- A review is bound to tenant + content + exact immutable revision number.
- Approval policies are snapshotted at submission: SINGLE, ALL, or SEQUENTIAL.
- Reviewer identities are unique; decisions are one-per-reviewer and append-only.
- Sequential reviews enforce configured reviewer order.
- Request-changes decisions require an explanatory comment and terminate the current review.
- Approval is reached only after the policy threshold is satisfied.
- Comments are immutable and duplicate comment IDs are idempotent.
- Aggregate versions support compare-and-set updates.
- Host-provided authorization is checked for submit, review, comment, reviewer eligibility, and cancellation.

## PostgreSQL transaction boundary
`PostgresEditorialReviewRepository` performs the following operations transactionally:
- Submission: verifies the exact current draft revision, transitions the content entry to IN_REVIEW, inserts the immutable policy snapshot and ordered reviewer assignments, and appends a workflow outbox event.
- Decision/comment: appends new history, performs aggregate-version CAS, mirrors terminal review outcome to the content lifecycle, and appends an outbox event.
- Approval moves content to APPROVED. A change request or cancellation returns it to DRAFT.
- Tenant identity is part of every aggregate key and query. The review references the exact immutable content revision through a composite foreign key.
- PostgreSQL triggers reject mutation of reviewer assignments, decisions, and comments and enforce valid review state transitions.

## Workflow dispatch
The outbox uses `FOR UPDATE SKIP LOCKED` leases, worker-owned acknowledgements, retry scheduling, attempt counts, and bounded error text. The dispatcher commits the claim before calling the optional `EditorialWorkflowOrchestrator` port. Calls carry the durable event ID so a Gamelan adapter can deduplicate at-least-once delivery. Failures release the lease with exponential retry delay.

The adapter port is implemented as a framework-neutral SPI. A concrete Gamelan adapter, scheduler/worker registration, operational metrics, and dead-letter policy are still required before production rollout. The outbox dispatcher is not automatically scheduled by this capability module.

## Verification
The Content Platform PostgreSQL integration suite covers:
- exact revision binding and atomic submit;
- approval and content lifecycle synchronization;
- change requests, comments, and content returning to DRAFT;
- tenant-scoped review reads;
- outbox lease ownership, retry, and acknowledgement.

The dedicated Content Platform workflow is the authoritative build/test signal. Do not treat this milestone as complete until the latest branch run passes and the composed runtime wires authorization and workflow adapters.

## Security and runtime boundaries
The host must resolve tenant and actor context from authenticated identity. Never accept tenant, author, or reviewer authority from content JSON. Re-check authorization on every command. Workflow assignment is not authorization. No external workflow call is made inside the review transaction.
