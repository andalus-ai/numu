# M38 — Earned-value roll-ups and cost-source ports

## Purpose

Provide a neutral roll-up for approved node-level earned-value metrics and stable application ports through which finance, ERP, BoQ, or software-cost integrations can supply evidence.

## Included

- `EarnedValuePortfolioReport` sums BAC, PV, EV, and AC across same-tenant, same-structure, same-baseline, same-date, same-currency WBS node metrics.
- CV, SV, CPI, and SPI are recalculated from the totals; portfolio indices are not averaged from node-level indices.
- Duplicate WBS nodes and mixed scopes/currencies are rejected.
- Application output ports define tenant-scoped retrieval contracts for approved budget and actual-cost evidence.
- Tests cover roll-up arithmetic, ratio recomputation, duplicate nodes, mixed currencies, and undefined portfolio ratios.

## Double-counting and currency policy

The roll-up can only guarantee that the supplied node IDs are unique. It cannot infer WBS ancestry from earned-value metrics alone. Callers must provide a mutually exclusive scope set (typically leaf cost nodes only, or another explicitly governed allocation policy) to avoid summing both parent summaries and child details. A future hierarchy-aware adapter should enforce this policy against the authoritative WBS structure.

Mixed-currency amounts are rejected. No implicit exchange rate, conversion date, or rate provider is assumed. Currency conversion requires a separately governed rate source and explicit policy.

## Integration boundaries

The source ports are contracts only; this milestone does not add finance/ERP/BoQ adapters, persistence, API endpoints, or authorization wiring. Adapter implementations must enforce tenant isolation and source-specific reconciliation/approval. Application use cases should verify requested node IDs and completeness of returned evidence before calculating reports.

## Verification

Tests are authored but not executed in this remote editing session. Run planning-domain and planning-application Maven tests, then the full reactor and integration tests for each configured evidence adapter before merge.
