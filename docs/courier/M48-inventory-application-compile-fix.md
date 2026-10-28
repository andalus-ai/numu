# M48 — Restore inventory application compilation

## Evidence

CI run 40608460307 exercised the M47 workflow changes:
- The identity bootstrap succeeded and organization and quantity verification ran.
- Supply-chain verification failed during compilation of `numou-inventory-application`.
- The compiler reported that `TenantScopedInventoryService` could not resolve `InventoryRepository` at line 120.
- The public repository interface exists in `tech.kayys.numou.inventory.spi.InventoryRepository`; the application service imported the tenant provider but not this SPI type.
- Consequently, Courier/Cargo verification was still skipped. This is a compile-time prerequisite failure, not a Courier test result.

## Change

Add the explicit import for `tech.kayys.numou.inventory.spi.InventoryRepository` to `TenantScopedInventoryService`. No runtime behavior or public API changes.

## Acceptance

- `supply/pom.xml` compiles and passes its verification gates.
- CI proceeds to `logistics/pom.xml verify` and exposes the Courier/Cargo test results.
- Do not mark the milestone complete based on source inspection alone; the workflow must confirm the fix.
