# M5 — Editorial Workflow

## Ownership
CMS owns editorial policy snapshots, reviewer assignments, review decisions, comments, and the decision that a particular immutable content revision is approved. Gamelan is an optional orchestration adapter: it schedules tasks and coordinates long-running work but cannot approve content, mutate CMS state, or make a revision publishable by itself.

## Implemented domain foundation
- A review is bound to tenant + content + exact immutable revision number.
- Approval policies are snapshotted at submission: SINGLE, ALL, or SEQUENTIAL.
- Reviewer identities are unique; decisions are one-per-reviewer and append-only.
- Sequential reviews enforce the configured order.
- Request-changes decisions require an explanatory comment and terminate the current review.
- Approval is reached only after the policy threshold is satisfied.
- Comments are immutable and duplicate comment IDs are idempotent.
- Aggregate versions support repository compare-and-set updates.
- Authorization and Gamelan orchestration are explicit host-provided ports; workflow assignment is not authorization.

## Integration and remaining work
This commit establishes the M5 domain and public application ports. It does not yet provide the PostgreSQL repository, transactional content-status coordination, durable workflow outbox, reviewer notifications, API/runtime endpoints, or Gamelan adapter. Those are required before M5 can be considered production-complete. The next implementation slice must persist review aggregate state with tenant-scoped constraints and atomically coordinate review creation/terminal decisions with the content lifecycle. Do not emit an external Gamelan command before the durable transaction commits; use an outbox-backed dispatcher.

## Security
The host must resolve tenant and actor context from authenticated identity. Never accept tenant, author, or reviewer authority from content JSON. Re-check authorization on every command and enforce tenant scope in every repository query.
