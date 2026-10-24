# Cargo & Courier — Milestone 6: Pricing, Billing and COD

## Scope
M6 establishes tenant-owned effective-dated courier tariffs, deterministic shipment quote snapshots, and an explicit Cash-on-Delivery (COD) collection/settlement lifecycle. It does not replace the shared Finance ledger or the separately delegated Supply Chain domain.

## Pricing contract
- Rate cards are versioned; changes publish a new version.
- Tariffs are scoped by tenant, service level, origin zone, destination zone, currency and effective window.
- Dimensional weight is parcel volume in cm³ divided by the tariff's cm³/kg divisor. Actual and dimensional weights are summed across pieces, rounded to 3 decimals, and chargeable weight is their maximum.
- Quote money is rounded half-up to two decimals. The quote stores rate-card ID/version and all calculation components; later tariff changes do not recalculate an existing quote.
- Quote calculation rejects tenant, service, zone and effective-window mismatches.

## COD contract
- One COD obligation per shipment and tenant.
- State transitions: DUE → COLLECTED → SETTLED or VARIANCE_RECORDED.
- Collection stores collector, branch, amount and time. Settlement stores reference, amount/time and explicit variance (collected minus settled).
- Variance is never silently discarded. Branch closeout reports group collections by tenant and collection branch.
- Finance owns ledger postings and accounting reconciliation. Courier stores operational collection facts and must use a Finance application port for journal posting; no second ledger is introduced.

## Persistence and isolation
V8 adds rate cards, quote snapshots and COD obligations. Composite tenant foreign keys prevent cross-tenant shipment/rate-card references. Tenant ID alone is not authorization; handlers must also enforce branch and actor permissions.

## Follow-on before production
- Add tenant-explicit JDBC repositories and transactional application handlers for rate cards, quotes and COD.
- Enforce non-overlapping effective windows per route/service.
- Add idempotency keys, audit/outbox events, branch closeout workflow and shared Finance ledger integration.
- Run Maven unit and PostgreSQL Testcontainers integration tests. Migration has not been verified against a live PostgreSQL instance in this environment.
