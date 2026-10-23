# Neutral Planning — Milestone 21: Forecast Evidence Governance

## Goal
Make schedule-risk and progress-trend evidence safer to use in reporting with deterministic governance findings, without coupling planning to a specific industry or approval system.

## Included
- Immutable machine-readable governance findings and report.
- Tenant, WBS structure, and plan-scope verification for each schedule-risk summary.
- Duplicate WBS-node detection to prevent accidental double counting.
- Future-dated and stale progress evidence checks against an explicit report date and caller-configured maximum age.
- Forecast-method version allow-list checks.
- Informational findings when a node has no progress-trend evidence, without treating missing trend data as a schedule failure.
- Deterministic finding ordering and tests for scope mismatch, stale evidence, future evidence, method version, missing evidence, and invalid freshness windows.

## Semantics
The evaluator does not mutate forecasts, recalculate schedule dates, approve plans, or decide whether a project is acceptable. The caller supplies the reporting date, freshness window, and supported method versions. Progress evidence is checked only against metadata actually available: tenant, structure, node, latest snapshot reporting date, and method version. Plan/revision provenance is not claimed because the current progress forecast model does not contain that metadata.

ERROR findings represent scope or temporal integrity issues; WARNING findings represent stale or unsupported-method evidence; missing progress evidence is INFO because dependency-derived schedule dates can still be reported.

## Validation
Tests are authored but have not been executed in this remote editing session. Run planning-domain tests and the full module build before merging.
