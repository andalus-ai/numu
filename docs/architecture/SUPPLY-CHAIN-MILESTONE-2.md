# Supply Chain & Logistics — Milestone 2

## Goal

Make stock issue and inter-warehouse transfer atomic at the repository boundary, strengthen purchase-order receipt invariants, and make the persistence contract explicit for future adapters.

## Implementation scope

- Inventory repository operations must check available on-hand stock and persist an issue as one atomic operation.
- Transfer must validate source stock and persist both sides together, or persist neither side.
- Receipt deltas must be positive and cannot silently over-receive ordered quantities. Any future over-receipt allowance must be an explicit, tenant-configured policy with approval/audit behavior.
- Regression tests cover insufficient stock, transfer rollback behavior, concurrent issue attempts, and invalid/replayed receipt deltas where the API can identify a replay.
- Persistence adapters must implement these operations with real transaction/locking or conditional-update semantics. In-memory synchronization only guarantees correctness within that single process and is not a substitute for database concurrency controls.

## Multi-tenancy and idempotency gate

M2 also defines the required next step for durable deployments: all inventory reads and writes must be tenant-scoped at repository and storage layers, and side-effecting commands must carry a durable idempotency key. These are not satisfied by a tenant string on a reservation alone. Existing APIs without tenant context must not be advertised as safe for multi-tenant production use.

## Acceptance tests

1. Given one unit in stock, two concurrent issues of one unit produce exactly one success.
2. An issue exceeding stock leaves the ledger unchanged.
3. A transfer with insufficient source stock leaves both source and destination unchanged.
4. A transfer is not partially persisted if destination posting fails.
5. Receipt quantity must be positive; received quantity cannot become negative or silently exceed ordered quantity.
6. A production persistence adapter proves these guarantees using database transactions/locking or atomic conditional updates, not process-local synchronization alone.

## Limitation

This milestone's in-memory adapter is a deterministic test/reference adapter. The production guarantee is the SPI contract plus adapter-specific transaction implementation and integration tests. No distributed database or tenant-security claim is made without a real persistence adapter and tenant-context enforcement.
