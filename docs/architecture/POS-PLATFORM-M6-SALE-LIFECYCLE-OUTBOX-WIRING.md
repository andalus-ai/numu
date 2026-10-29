# POS Platform M6: Sale lifecycle outbox wiring

**Status:** Draft implementation; not production-verified.  
**Depends on:** merged transactional outbox foundation in [PR #1182](https://github.com/andalus-ai/numu/pull/1182).

## Behavior

The sale command service now requires a `PosOutboxWriter` and appends a versioned event after the successful persistence operation for:
- opening a sale: `pos.sale.opened`
- completing a sale: `pos.sale.completed`
- voiding an open sale: `pos.sale.voided`

Adding a line is intentionally not a lifecycle event in this milestone.

## Idempotency

Event append occurs inside the idempotent command supplier. A replay returning the stored result does not execute the supplier again and therefore does not append another event. The event writer must enlist in the same transaction as sale persistence and idempotency completion; the PostgreSQL integration test in M4 remains the required verification of that adapter contract.

## Boundaries

- POS records sale lifecycle facts only.
- Payment, inventory, tax, pricing, and accounting side effects remain outside this command.
- No broker publishing or dispatcher is included.
- Consumers must deduplicate by event ID; delivery semantics remain at-least-once.

## Verification and follow-up

Unit tests cover all three transitions and replay behavior. No Maven build or database-backed integration test was run in this session. Before production, verify the actual CDI/service construction path supplies the outbox writer, and prove transaction rollback when outbox append fails.
