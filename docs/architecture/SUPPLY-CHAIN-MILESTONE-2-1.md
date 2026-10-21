# Supply Chain Milestone 2.1 — Idempotent Inventory Commands

- Added a scoped command key and explicit idempotent application-service overloads for receipt, issue, and transfer.
- The in-memory adapter atomically returns original movement(s) on a same-payload replay and rejects key reuse with a different business payload.
- Existing non-idempotent APIs remain for compatibility. Integrations should use stable keys across retries.
- The in-memory registry is volatile and process-local. Persistent adapters must save command receipts and movements in one database transaction with a unique constraint.
- The scope should come from authenticated tenant/integration identity; it is not authorization. This increment does not implement end-to-end tenant isolation.
- Reservation-aware availability and persistent adapter transactions remain production gates. CI and Maven tests must be verified before declaring the increment complete.
