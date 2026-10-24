# Neutral Planning — Milestone 39: Earned-value application use case

## Outcome

Connect the M37 approved cost-evidence model and M38 source ports through a framework-neutral application use case. Callers provide progress percentages from their governed progress-snapshot query; the use case retrieves cost evidence and calculates node-level earned-value metrics, with a portfolio roll-up when aggregate CPI/SPI denominators are defined.

## Included

- `CalculateEarnedValueReport` application use case using the budget and cumulative actual-cost source ports.
- Strict validation of tenant, structure, baseline, reporting date, currency, requested node set, approval status, duplicates, and evidence completeness.
- No partial report when required cost evidence is missing.
- Optional portfolio roll-up when aggregate actual cost and planned value are both non-zero; node metrics remain available when the ratios are undefined.
- Unit tests for successful calculation, missing evidence, cross-tenant evidence, duplicate node inputs, and undefined portfolio ratios.

## Boundaries and follow-up

- The caller remains responsible for obtaining progress percentages from an approved/governed snapshot and selecting mutually exclusive WBS cost scopes (usually leaves only). The use case does not invent progress, select baselines, or infer WBS ancestry.
- Evidence source adapters must enforce tenant isolation in addition to the use case's defensive scope checks.
- No finance, BoQ, construction, persistence, HTTP, or authorization adapter is added here.
- CI/test execution must be verified by the repository workflow; tests authored in this milestone are not claimed as passing.
