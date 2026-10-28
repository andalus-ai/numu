# Construction Consultancy — Persistence Readiness

## Why aggregate reconstitution is explicit

A persistence adapter must restore an engagement's persisted identity, scope revision, lifecycle status, and cancellation reason without replaying business commands. Replaying commands during hydration can create unintended transitions and makes stored state difficult to validate.

`ConsultancyEngagement.reconstitute(...)` is the domain hydration boundary. It validates invariants that must hold for stored state:

- scope revision is positive;
- status is present;
- a cancelled engagement has a nonblank cancellation reason;
- non-cancelled engagements cannot carry a cancellation reason;
- client and consultant organizations remain distinct.

It deliberately does not perform tenant authorization. The adapter must load by both tenant ID and aggregate ID, and must never hydrate a record selected only by a caller-supplied aggregate ID.

## Required persistence implementation

The next adapter slice must be built on the repository's existing PostgreSQL migration bundle and transaction-runner conventions, not a second migration mechanism. Before enabling the command handler, it must:

1. persist all aggregate fields needed to reconstitute state;
2. enforce unique `(tenant_id, engagement_number)` and `(tenant_id, engagement_id)` constraints in PostgreSQL;
3. scope every read and write by tenant;
4. use the same transaction-bound SQL connection as idempotency reservation/completion;
5. map duplicate-key violations to a stable application conflict without retrying outside the transaction;
6. add adapter-level tests for insert/read round-trip, tenant isolation, concurrent duplicate engagement numbers, and rollback when idempotency completion fails.

## Status

This change adds the domain hydration API and unit tests for its invariants. It does **not** add or claim a production persistence adapter, migration, HTTP endpoint, Indonesian regulatory compliance, or Sharia/istisna' compliance. The persistence bundle's actual migration conventions and transaction-context implementation must be confirmed before wiring the adapter.
