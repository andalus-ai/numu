# Construction Consultancy — PostgreSQL Lifecycle Updates

## What changed

The PostgreSQL consultancy operations adapter now implements the transaction-scoped `updateEngagement` port. All writes use the `SqlConnection` supplied by the existing command transaction runner; no pool-level second connection is opened.

## Concurrency strategy

Updates are conditional SQL compare-and-set operations scoped by both tenant ID and engagement ID:

- DRAFT proposal revision requires the stored revision to equal the new revision minus one, and only allows a pre-activation stored state.
- Proposal submission requires DRAFT.
- Activation requires PROPOSED.
- Completion requires ACTIVE.
- Cancellation requires a non-terminal stored state and the expected scope revision.

A zero-row update becomes a typed concurrency/state conflict instead of silently overwriting a newer aggregate. This protects lifecycle state from stale writes without introducing a persistence framework or cross-capability foreign keys. The current model has no general aggregate version field; the conditional state/revision checks are therefore intentionally explicit. Any future mutable fields must be added to the concurrency predicate or accompanied by a dedicated version column.

## Integration tests added

The PostgreSQL integration suite now covers:
- proposal submission, proposal revision, re-submission, activation, and completion round trips;
- revision increments and persisted scope text;
- cancellation reason persistence;
- rejection of a stale proposal revision after another command activates the engagement;
- tenant-scoped update isolation.

These tests require the repository's PostgreSQL-enabled integration profile and the consultancy migration. They were authored but not executed in this environment.

## Remaining production gates

- Run the construction Maven reactor and PostgreSQL integration profile in CI.
- Test concurrent commands and atomic rollback of lifecycle mutation plus outbox/idempotency.
- Ensure the outbox dispatcher and consumers handle at-least-once delivery idempotently.
- Add API-level mapping for the typed concurrency exception to a stable conflict response.
- Continue signed scope/document evidence, deliverable history, Indonesian regulatory workflows, and Sharia istisna' review as separate follow-up milestones.

This implementation does not itself establish statutory or Sharia compliance.
