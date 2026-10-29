# POS Platform M1: Neutral register and shift foundation

**Status:** Initial domain foundation proposed for review  
**Roadmap:** [Issue #1102](https://github.com/andalus-ai/numu/issues/1102)

## Scope

This change introduces a small, deployment-neutral domain foundation for register identity, cash-shift lifecycle, and currency-aware amounts. It does not replace or rename the existing grocery API.

- `Register` stores opaque tenant, branch, and store IDs; organization remains the owner of location aggregates.
- `RegisterShift` is an immutable open/closed snapshot with explicit opening float, counted cash, timestamps, and operator/register references.
- `Money` enforces currency identity for arithmetic but intentionally does not impose tax, pricing, discount, or currency-rounding policy.
- Closing a shift records counted cash only. Expected-cash computation, discrepancy review, cash movement posting, and reconciliation belong in later application workflows and their owning capabilities.

## Explicit non-goals

- No catalog/product, inventory, tax, pricing, payment-provider, or accounting source of truth is introduced.
- No persistence schema, HTTP endpoint, CDI bean, hardware integration, or offline synchronization protocol is added.
- No claim is made that the existing POS adapter or Maven reactor is runnable; these require separate verification.
- Existing `tech.kayys.numou.groceries` API and behavior remain unchanged.

## Acceptance checks

- Unit tests cover currency mismatch, register identity, immutable shift close, duplicate close, negative cash, and invalid close time.
- Domain classes use only JDK types and do not depend on transport, persistence, or provider SDKs.
- Before production use, the next implementation must add application ports/use cases, persistence and concurrency controls, authenticated tenant/branch authorization, audit, idempotency, and reconciliation tests.
