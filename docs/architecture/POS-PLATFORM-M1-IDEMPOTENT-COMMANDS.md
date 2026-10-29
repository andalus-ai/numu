# POS Platform M1: Idempotent sale commands

**Status:** Proposed for review; durable adapter is required before production use  
**Roadmap:** [Issue #1102](https://github.com/andalus-ai/numu/issues/1102)

## Contract

Sale commands now require an explicit idempotency key. The application derives a SHA-256 request fingerprint from canonical command fields and delegates execution to `SaleCommandIdempotency`.

The port contract requires an implementation to:

- Scope keys by tenant and actor, with an operation name and request fingerprint.
- Atomically reserve a key so concurrent retries cannot execute the command more than once.
- Return the original completed result for a replay with the same operation and fingerprint.
- Reject key reuse with a different operation or payload.
- Persist the command effect and idempotency result in one transaction, or use an equivalent recoverable protocol so a crash cannot leave a committed sale with no replay record.

The in-memory test implementation demonstrates replay and conflict behavior only; it is not durable and is not safe for concurrent production use.

## Command behavior

- `open`, `addLine`, `complete`, and `voidOpenSale` require a nonblank key (maximum 200 characters).
- Tenant and actor scope come from trusted command context.
- Shift authorization and expected-version checks still apply to mutations.
- The application does not silently generate a key from a correlation ID; clients or trusted upstream command handlers must supply a stable key for retries.

## Not yet implemented

There is no database-backed idempotency adapter, transaction manager integration, audit/outbox, API endpoint, payment/inventory orchestration, or reconciliation worker in this change. The repository adapter and idempotency adapter must share a transaction or implement a documented recovery protocol before the contract can be considered production-ready.

No Maven command was run during authoring. CI must be reviewed before this PR is promoted from draft.
