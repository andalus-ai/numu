# Neutral Planning — Milestone 14: WBS Progress Rollups

## Objective

Provide a deterministic progress summary for a selected WBS node, based on schedule activity assessments and an explicit weighting policy.

## Included

- Application use case `CalculateWorkBreakdownProgress`.
- Tenant-scoped resolution of the immutable WBS snapshot and its activity links.
- Scope selection based on the target node and descendants.
- Leaf-only inclusion: only activities linked to leaf nodes in the selected subtree are eligible. Activities linked to grouping/ancestor nodes are excluded so parent and descendant progress cannot both contribute.
- Explicit positive weights, one reporting date, plan-membership validation, and required assessments for every included activity.
- Tests for weighted aggregation, ancestor exclusion, missing assessments, inconsistent reporting dates, and invalid weights.

## Aggregation semantics

The use case delegates arithmetic to the existing `ProgressRollup` domain type. Completion is the weighted mean of actual percentages, rounded to two decimal places. Weights must be approved and comparable for the report. The core does not infer weights from cost, quantities, effort, or construction-specific units.

If a node has no assessed activities linked to eligible leaves, the use case rejects the request rather than inventing a zero-percent completion. A caller may explicitly choose a different empty-scope display policy at the presentation boundary.

## Security and history

The caller must load the schedule through an authorization-aware boundary and supply tenant identity from trusted authentication. The use case validates link tenant/structure and plan membership. As-of reporting is deterministic when the caller supplies assessments for the same date; persisting immutable rollup snapshots is a separate concern and is not claimed by this milestone.

## Validation

Tests are authored but have not been executed in this remote editing session. Run the planning Maven reactor and relevant PostgreSQL/Flyway integration suite before marking ready.
