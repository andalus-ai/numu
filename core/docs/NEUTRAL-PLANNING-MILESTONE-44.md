# Neutral Planning — Milestone 44: Cost-evidence reader contract regressions

## Outcome

Expand the executable contract tests around the neutral `CostEvidenceReader` boundary. These tests protect the validation decorator against cross-tenant actuals, wrong structure/date, duplicate actual-cost rows, null rows, and mutation of provider-owned result lists.

## Added coverage

- Actual-cost evidence for another tenant is rejected.
- Actual-cost evidence for another structure or reporting date is rejected.
- Duplicate actual-cost rows for one WBS node are rejected.
- Null evidence rows fail explicitly instead of being ignored.
- Returned results are immutable snapshots and do not retain a provider-owned mutable list.
- Existing budget checks continue to cover tenant, structure, baseline, approval state, requested nodes, and duplicates.

## Important limitation

These are application-level contract tests with controlled stub providers. They do **not** prove database-level tenant isolation or reconciliation against posted finance ledger transactions. The repository's authoritative budget and actual-cost source/schema remains unverified, as recorded in M42. A concrete provider and PostgreSQL integration tests must follow the owning finance/ERP/BoQ source decision; no speculative schema or second ledger is introduced.

## Validation status

Tests authored through the GitHub API. Maven tests and CI have not been executed by this editing session; compilation and test execution remain unverified.
