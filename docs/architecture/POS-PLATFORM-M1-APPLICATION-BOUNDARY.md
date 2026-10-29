# POS Platform M1: Application command boundary

**Status:** Proposed for review; persistence and runtime adapters remain pending  
**Roadmap:** [Issue #1102](https://github.com/andalus-ai/numu/issues/1102)

## Included

- Application use cases to open a sale, add a line, complete a sale, and void an open sale.
- Trusted command context containing tenant, actor, and correlation identifiers.
- A shift-access port requiring an authorized operator and an open shift for the tenant, branch, and register.
- A tenant-scoped sale repository contract with explicit versions and compare-and-set update semantics.
- Unit tests using in-memory ports for command flow, tenant scoping, and stale-version rejection.

## Adapter obligations

The repository adapter must scope reads by tenant. Updates must atomically compare the expected version and reject stale writes; an in-memory test fake is not a production persistence adapter. The shift-access adapter must verify tenant, branch, register, shift, operator permissions, and current shift status. Identifiers alone are not authorization.

## Not included or claimed

No database adapter, HTTP endpoint, durable command idempotency, audit/outbox, catalog/pricing/tax integration, payment orchestration, inventory reservation/movement, accounting, receipts, or offline synchronization is implemented here. Existing payment and inventory contracts must be verified before integrating them. Completing a POS sale only changes POS sale state; it does not assert external tender, stock, or ledger side effects succeeded.

No Maven command was run during authoring. Review the CI result before promoting this PR from draft.
