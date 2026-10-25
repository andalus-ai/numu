# Neutral Planning — Milestone 43: Earned-value evidence boundary regressions

## Outcome

Expand application-level regression coverage around the earned-value use case's existing evidence validation. This milestone protects the neutral planning boundary while the authoritative finance/ERP/BoQ source remains unselected.

## Added regression cases

- Budget evidence must match the requested baseline.
- Actual-cost evidence must match the requested structure and reporting date.
- Evidence in a currency different from the report currency is rejected; no implicit currency conversion is attempted.
- Duplicate budget rows for a WBS node are rejected.
- Evidence for an unrequested WBS node is rejected.
- Null provider responses are rejected for both budget and actual-cost sources.

These tests complement existing coverage for approved evidence, cross-tenant records, missing evidence, duplicate progress nodes, and undefined portfolio ratios.

## Design boundary

The tests exercise the existing `CalculateEarnedValueReport` use case and do not introduce finance-specific persistence, schema assumptions, or fake actual-cost data. Provider-level tenant isolation and ledger reconciliation still require integration tests against the authoritative source selected by the owning finance/ERP/BoQ module.

## Validation status

Tests were added but have not been executed in this remote editing session. CI must confirm compilation and execution before merge.
