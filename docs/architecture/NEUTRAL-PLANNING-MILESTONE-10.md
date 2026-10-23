# Neutral Planning — Milestone 10: Progress Rollups

## Objective

Provide a reusable primitive for summarizing activity progress at work-package, schedule, or project level without embedding construction, software delivery, procurement, or other domain rules.

## Included

- `ProgressContribution` carries an explicit tenant, activity, as-of date, bounded completion percentage, and caller-supplied positive weight.
- `ProgressRollup` computes a weighted completion percentage with deterministic decimal rounding.
- Rollups reject duplicate contribution IDs, duplicate activity IDs, mixed tenants, mixed as-of dates, invalid percentages, non-positive weights, and empty input.
- Tests cover weighted calculations and the main integrity guards.

## Aggregation policy

The formula is:

`sum(activity completion percentage × approved weight) / sum(approved weight)`

Weights must be defined by the host application's approved planning policy. The framework does not assume that quantities in different units can be added or compared. A rollup must select a non-overlapping set of activities: the same activity cannot appear twice, which prevents a common accidental double-count when callers pass both a parent summary and one of its descendants.

This is an explicit contribution-based primitive, not an implicit WBS traversal. The current schedule model does not itself define a canonical parent/child hierarchy or approved weight policy, so those must be supplied by the owning application rather than guessed by the framework.

## Security and consistency boundaries

The tenant check prevents accidental cross-tenant aggregation inside this calculation, but does not authorize a caller. Repository access and tenant identity must still be bound to trusted application authentication. All inputs must represent the same as-of date; callers should obtain accepted progress from the historical projection for that date.

## Validation

Tests are authored but have not been executed in this remote GitHub editing session. Run the planning Maven reactor and the PostgreSQL/Flyway integration suite before marking the milestone ready.
