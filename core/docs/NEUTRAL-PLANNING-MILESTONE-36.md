# M36 — Integrated neutral project controls

## Purpose

Provide one domain-level report that combines schedule variance with progress variance for a single tenant, WBS structure, schedule plan, and reporting date. This is reusable across construction, software delivery, internal projects, and other work-management contexts.

## Included

- `ProjectControlsReport` combines an existing `ScheduleVarianceReport` with an explicit list of `ProgressVariance` values.
- Validates tenant, structure, plan, and report-date consistency.
- Allows only one progress variance per WBS node in the report.
- Summarizes nodes behind baseline (negative percentage-point variance), ahead of baseline (positive), and on baseline (zero).
- Computes the average progress variance using two-decimal, HALF_UP rounding.
- Handles an empty progress list explicitly as zero average and zero counts.
- Exposes convenience indicators for late project finish and any node behind baseline.

## Boundaries and semantics

This report is a composition/projection over existing schedule and progress evidence. It does not mutate plans, snapshots, baselines, or approval decisions. Callers must provide progress variances based on the approved snapshots and baselines selected for the report date; this aggregate does not replace authorization or governance checks.

This milestone deliberately does **not** calculate earned value, cost variance, CPI, or SPI. Completion percentages and schedule dates do not provide the budgeted/actual cost evidence required for defensible earned-value calculations. Cost integration should be added only through an explicit, tenant-scoped cost/budget evidence contract rather than guessed from quantities or construction-specific BoQ assumptions.

## Verification

Unit tests cover successful composition, lateness and progress counts, empty progress data, mismatched report dates/structures, duplicate WBS nodes, and inconsistent precomputed summaries. Run the planning domain and application Maven tests and full reactor checks before merge; tests were authored in the remote editing session but not executed there.
