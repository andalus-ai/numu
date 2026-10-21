# Supply Chain Milestone 2.2 — Reservation-aware inventory

## Scope
This increment integrates the existing reservation domain model with inventory availability. Reservations are created only when unreserved stock is sufficient, normal issues and transfers cannot consume reserved quantities, and a reserved issue posts the ledger movement and commits the reservation under one repository atomic boundary.

## Contracts
- Available = on-hand minus active, non-expired reservations (clamped to zero in the read model).
- Reservation creation, status transitions, and reserved issue are repository operations so database adapters can provide transaction/locking semantics.
- Reservation updates use optimistic version checks in the in-memory adapter.
- Idempotency replay matching covers item, warehouse, movement type, quantity, currency, and reference. Derived unit cost is intentionally excluded because issue/transfer valuation is calculated from current ledger state; receipt valuation fingerprinting should be formalized in a future command-receipt schema.

## Explicit limitations
- The in-memory implementation is process-local and not durable.
- Inventory item/warehouse/movement identity is not yet tenant-scoped. Reservation tenantId is metadata only here; it is not a security boundary. Shared item/warehouse pools currently share availability across tenants.
- Idempotency replay records are currently volatile and do not yet persist a canonical request fingerprint for all command inputs.
- Database-backed locking, durable reservation uniqueness, expiration workers, tenant-scoped item/warehouse/ledger identities, and integration with order/fulfillment workflows remain required before production use.
- This change is not considered verified until the relevant Maven tests and CI run successfully.
