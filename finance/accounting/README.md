# Accounting Services

This directory contains the executable service boundaries derived from the
accounting plans. The existing accounting modules remain the system of record
for ledger, invoice, reporting, and compliance behavior.

## Service modules

| Module | Responsibility |
| --- | --- |
| `numou-accounting-domain` | Shared ledger, accounting policy, and foundational accounting model |
| `numou-accounting-application` | Commands, queries, CQRS, and application services |
| `numou-accounting-payables` | Vendor invoice domain, 3-way matching, and payment scheduling |
| `numou-accounting-receivables` | Focused AR invoice and customer-payment application services with repository ports and in-memory adapters |
| `numou-accounting-consolidation` | In-memory group hierarchy and ownership, run lifecycle, intercompany balances, reciprocal/profit/dividend elimination-rule SPI, translation results, minority-interest calculation, trial-balance views, and repository/query seams |
| `numou-accounting-treasury` | Cash positions, direct/indirect forecasts, liquidity plans, payment-batch lifecycle, netting, FX exposure/hedge effectiveness, cash pools, debt draws/repayments/interest, investments, bank connectivity and payment-file SPIs, and dataset views |
| `numou-accounting-adapter` | Quarkus REST and infrastructure adapters |

The AP, AR, consolidation, and treasury domain packages now live with their
owning capability modules rather than in the shared accounting domain module.

Budget planning, commitments, control, forecasting, and allocation now live in
the standalone hexagonal capability at `../budget`.
Purchase requisitions, purchase orders, goods receipts, and their domain events
now belong to `../../supply/purchasing/numou-purchasing-domain`.
Inventory catalog, warehouse, stock movement, and valuation capabilities now
live in the hexagonal modules under `../../supply/inventory`.

The two modules now provide the principal plan surfaces as Java 21 records,
in-memory services, and explicit ports. They intentionally do not ship
production persistence, bank credentials/connectors, ledger posting, ISO
schema validation, scheduling, or country-specific compliance adapters.
Those infrastructure adapters should depend on these boundaries rather than
being embedded in the aggregates.

The detailed plans remain in `../Docs/plan04.md` and
`../Docs/plan05-treasury.md` as the implementation backlog and acceptance
reference.
