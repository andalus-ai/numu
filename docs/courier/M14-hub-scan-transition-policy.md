# M14 — Hub scan transition policy

## Purpose

M14 adds a pure domain guard for validating hub scans against tenant-scoped transport-container and dispatch-wave state.

## Rules covered

- A container can be sealed only when open, non-empty, and scanned at its origin hub.
- A dispatch-load scan requires a loading wave, an expected parcel, and the wave origin.
- A container dispatch scan requires a sealed container and a departed wave with the same route.
- A container receipt scan requires a dispatched container and the destination hub.
- A general hub receipt is checked against container state when a container context is supplied.

## Integration contract

The policy is pure and does not access a database. Callers must load contexts under the command tenant and keep the corresponding row locks until the receipt, state transition, tracking fact, and outbox event commit. They must not use unscoped or stale cached contexts.

M15 connects custody-sensitive scans to `JdbcHubOperationsStore` with explicit container/wave references, row locks, transition checks, and atomic custody/receipt/tracking/outbox writes. See [M15 transactional custody enforcement](M15-transactional-custody-enforcement.md) for implementation details and remaining limitations. The policy class remains a pure reusable guard; the JDBC adapter currently enforces the equivalent persistence checks directly while transitioning database rows.

## Exception authorization

`EXCEPTION_RECORDED` is an evidence event, not permission to bypass a guard. Authorization must be enforced by an application use case using authenticated actor identity and a permission policy; a reason field alone is not proof of authorization.

## Verification

Domain tests cover valid and invalid sealing, load eligibility, dispatch route matching, and destination receipt. Maven execution has not been confirmed in this environment.
