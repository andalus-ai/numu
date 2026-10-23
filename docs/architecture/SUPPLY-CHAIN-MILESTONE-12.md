# Supply Chain M12 — Durable recovery command payloads

## Delivered
- Added a typed `WarehouseInventoryRecoveryPayload` for receiving and reserved picking.
- Persisted item, warehouse, reservation, quantity, unit cost, currency, and reference in the execution journal.
- Added PostgreSQL migration V4 with a payload-shape constraint; older journal rows may remain payload-less.
- Bound the recovery payload to the existing tenant-scoped command key and fingerprint. Reusing a key with a different payload is rejected.
- Added `WarehouseInventoryRecoveryHandler`, which reconstructs receiving/picking calls through the recovery-aware coordinator using the original command key.
- Added payload validation tests for decimal fidelity and operation-specific required fields.

## Recovery flow
1. The coordinator creates the journal entry with the immutable recovery payload before posting inventory.
2. The JDBC journal persists payload and state in the same row.
3. A recovery worker claims the journal row and invokes the concrete handler.
4. The handler replays the same operation with the original command key; inventory and warehouse idempotency receipts prevent duplicate effects.

## Compatibility and limitations
- Legacy rows without payload cannot be automatically replayed by the concrete handler and fail explicitly for operator reconciliation.
- The payload stores decimal values as strings, avoiding floating-point conversion.
- The JDBC migration must be applied by the host application.
- Real PostgreSQL migration/round-trip tests and full Maven/CI validation remain required; this PR does not claim those checks passed.
