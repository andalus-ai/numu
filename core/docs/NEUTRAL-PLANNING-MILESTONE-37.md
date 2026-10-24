# M37 — Neutral cost evidence and earned-value foundations

## Purpose

Introduce evidence contracts and a domain calculator that support defensible project cost controls without making the planning core depend on construction BoQ, a particular finance module, or a specific persistence technology.

## Included

- `BudgetedCostEvidence`: tenant, WBS structure/node, baseline, currency, amount, source reference, governance status, and approval timestamp.
- `CumulativeActualCostEvidence`: tenant, WBS structure/node, reporting date, currency, cumulative actual cost, source reference, governance status, and approval timestamp.
- `CostEvidenceStatus`: explicit DRAFT / APPROVED / REJECTED state. Calculations accept only APPROVED evidence.
- `EarnedValueCalculator`: computes BAC, PV, EV, AC, CV, SV, CPI and SPI for one WBS node using approved evidence and supplied planned/actual progress percentages.
- Scope and currency matching, reporting-date matching, percentage bounds, nonnegative amounts, and deterministic rounding.
- CPI is undefined when actual cost is zero; SPI is undefined when planned value is zero. The API represents these as empty optionals, not infinity or a fabricated zero.
- Unit tests for calculation, zero denominators, rejected evidence, tenant/currency mismatch, report-date mismatch, and invalid percentages.

## Formulae

- Budget at Completion (BAC) = approved budget for the WBS node.
- Planned Value (PV) = BAC × planned completion percentage.
- Earned Value (EV) = BAC × actual completion percentage.
- Actual Cost (AC) = approved cumulative actual cost at the report date.
- Cost Variance (CV) = EV − AC.
- Schedule Variance (SV) = EV − PV.
- Cost Performance Index (CPI) = EV / AC, when AC is nonzero.
- Schedule Performance Index (SPI) = EV / PV, when PV is nonzero.

Percentages are supplied as values from 0 through 100. Monetary values are rounded to two decimal places; indices to four decimal places, using HALF_UP rounding.

## Integration boundaries

Finance/ERP adapters must reconcile and approve actual-cost evidence. Budget evidence must come from an approved baseline or budget source. Progress adapters must supply governed, same-report-date planned and actual percentages. This milestone does not invent cost data, roll up parent WBS values, infer budget from dates or completion, or introduce BoQ/construction types into the planning domain. Aggregation and adapters should be separate milestones so currency conversion, parent roll-ups, and allocation rules remain explicit.

## Verification

Tests are authored but have not been executed in this remote editing session. Before merge, run the planning-domain Maven tests, the full reactor, and integration checks for source adapters and authorization at the application boundary.
