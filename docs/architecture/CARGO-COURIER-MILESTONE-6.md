# Cargo & Courier — Milestone 6: Pricing, Billing and COD

## Pricing
- Effective-dated rate cards are scoped by tenant, service level, route zones, currency and tariff version.
- Dimensional weight is each parcel's volume in cm³ divided by the tariff divisor; actual and dimensional weights are summed and rounded to 3 decimals. Chargeable weight is the maximum.
- Quotes snapshot the rate-card ID/version, weights, fees, tax and total. Monetary components use half-up rounding to two decimals.
- Quote calculation rejects tenant, service, zone and effective-window mismatches.

## COD lifecycle and closeout
- COD state transitions: DUE → COLLECTED → SETTLED or VARIANCE_RECORDED.
- Collection captures collector, branch, amount and timestamp. Settlement captures reference, amount, timestamp and the explicit difference between collected and settled amounts.
- `CodBranchCloseout` calculates an immutable report for one tenant, branch, currency and half-open time window. It reports collected/settled totals, counts and outstanding/variance amounts without mutating obligations.
- Mixed currencies and cross-tenant inputs are rejected. Closeout is a report, not a ledger posting.

## Boundary with Finance and Supply Chain
Courier owns operational tariff and COD facts. The shared Finance module remains the ledger/accounting authority; Courier must integrate through an application port rather than maintain a second ledger. The separately delegated Supply Chain module remains responsible for its own procurement/inventory workflows.

## Verification and production follow-up
Domain tests cover tariff math and COD transitions/closeout invariants. Tests have not been run in this environment. JDBC persistence for tariffs, quotes and COD exists, but production readiness still requires transactional application handlers, command idempotency, audit/outbox integration, database enforcement of non-overlapping tariff windows, Finance posting integration, and PostgreSQL Testcontainers verification.
