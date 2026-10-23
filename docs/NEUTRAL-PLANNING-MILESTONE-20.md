# Neutral Planning — Milestone 20: WBS Schedule Risk Projection

## Goal
Connect the dependency-aware schedule network to WBS nodes through the existing explicit activity-link model and surface schedule exposure alongside progress trend forecasts.

## Included
- Immutable per-node schedule risk summary with linked activity identifiers, critical activity count, earliest activity start, latest linked activity finish, and optional progress forecast.
- Aggregation calculator that projects activity-level critical-path outputs onto WBS nodes only through explicit tenant/structure/plan-scoped links.
- Scope validation for links and progress forecasts, duplicate activity mapping checks, and validation that supplied progress forecasts correspond to mapped WBS nodes.
- Tests for multi-activity node aggregation, critical exposure, dates, cross-tenant rejection, and unmapped progress forecasts.

## Semantics and limitations
The summary reports the earliest start and latest earliest-finish date among linked activities; it does not recalculate the WBS hierarchy or infer parent/child rollups. Critical exposure means at least one linked activity has zero total float in the supplied schedule network. A progress trend forecast is displayed as evidence alongside the schedule calculation; it does not automatically alter activity duration or dependency dates. Calendar-day and finish-to-start assumptions from Milestone 19 remain.

## Validation
Tests are authored but have not been executed in this remote editing session.
