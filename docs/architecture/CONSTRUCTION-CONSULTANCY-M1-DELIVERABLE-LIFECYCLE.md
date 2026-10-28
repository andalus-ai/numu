# Construction Consultancy M1 — Deliverable Lifecycle Commands

## Scope

This milestone adds application-level commands for the persisted consultancy deliverable lifecycle: submit, accept, reject, and revise. Creation remains available through the persistence contract but must be exposed through an authorized creation command before a public API is added.

## Ownership and command behavior

- The construction consultancy domain owns valid deliverable state transitions.
- The application handler derives the client or consultant organization from the tenant-scoped persisted aggregate. The caller cannot choose the organization whose authority is checked.
- `SUBMIT` and `REVISE` require consultant-organization authorization; `ACCEPT` and `REJECT` require client-organization review authorization.
- Each command uses a trusted tenant/actor context, a canonical SHA-256 request fingerprint, and the shared idempotent command executor.
- The deliverable compare-and-set update and outbox insert run through the same transaction context as idempotency reservation/completion. Failure of any step should roll back the entire command.
- Outbox payloads contain constrained identifiers, enum values, revision and actor ID; user-provided text is not interpolated into JSON.
- PostgreSQL compare-and-set predicates reject stale transitions and enforce expected lifecycle state and revision.

## Verification added

- Command input validation tests for trusted context, idempotency key, revision fields, and rejection reason.
- PostgreSQL integration coverage for submit, reject, revise, resubmit, accept, and stale-update rejection.

Tests were authored but have not been run in this environment. CI must run the construction domain/application tests and the PostgreSQL integration profile before merge.

## Known gaps / next acceptance criteria

1. Add an authorized, idempotent deliverable creation handler and API boundary.
2. Persist immutable revision and review-history rows in the same transaction; the current aggregate row remains a latest-state projection and is not a complete audit trail.
3. Test duplicate command replay, same-key/different-payload conflict, outbox rollback, and concurrent review races against PostgreSQL.
4. Map missing deliverable and compare-and-set conflicts to stable application/API errors.
5. Link signed drawings, reports, acceptance evidence and document versions using the public document capability contract.
6. Keep Indonesian statutory and Sharia istisna' evidence as separately reviewed extensions; this milestone makes no compliance claim.
