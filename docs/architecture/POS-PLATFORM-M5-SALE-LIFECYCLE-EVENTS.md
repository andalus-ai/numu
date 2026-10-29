# POS Platform M5: Sale lifecycle event contracts

**Status:** Draft implementation; not production-verified.  
**Depends on:** [M4 transactional outbox](https://github.com/andalus-ai/numu/pull/1182).

## Scope

Adds an application-layer mapper for versioned integration envelopes for:
- `pos.sale.opened`
- `pos.sale.completed`
- `pos.sale.voided`

Each payload contains only sale identifiers, operational context, currency, timestamps, and line count where relevant. It deliberately excludes customer details, payment credentials, and line-level personal or pricing data.

## Producer rules

- A command must append the event only after it has produced a real lifecycle transition.
- Append the event through `PosOutboxWriter` in the same transaction as the sale write and idempotency result.
- Do not emit an event for a replay that returns the previously stored idempotent result.
- Do not publish directly to a broker from the command.
- Event consumers must deduplicate by `event_id`; delivery is at least once, not exactly once.

## Explicit limitation

This milestone provides the event mapping contract, but it does **not** claim the existing command handlers have been wired to it. The application command-handler source could not be reliably located through the connected repository interface during this pass. Wiring must be done against the actual command implementation, with integration tests proving transition-to-outbox atomicity and replay behavior, before this is production-ready.

## Verification

No Maven build or PostgreSQL integration tests were run for this draft.
