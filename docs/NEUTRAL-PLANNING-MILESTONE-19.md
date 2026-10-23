# Neutral Planning — Milestone 19: Dependency-Aware Schedule Forecasting

## Goal
Calculate a deterministic project finish forecast from a generic schedule activity network without assuming construction, software delivery, or another industry.

## Model
- Activities have a stable ActivityId and planned duration in calendar days.
- Dependencies are finish-to-start links with non-negative calendar-day lag.
- The calculator performs a topological sort, forward pass for earliest dates, and backward pass for latest dates.
- Total float is latest start minus earliest start. Zero-float activities are marked critical.
- The immutable result includes activity early/late dates, float, project finish, method version, and critical activities in deterministic topological order.

## Guardrails
- Reject duplicate activities and directed dependencies.
- Reject references to unknown activities, self-dependencies, negative durations/lags, and dependency cycles.
- Zero-duration activities are supported for milestones.
- Deterministic queue ordering keeps results stable for the same network.

## Semantics and limitations
All calculations use calendar days. Working calendars, resource leveling, actual progress, constraints, leads/negative lags, alternative dependency types, and probabilistic uncertainty are not modeled. This is a schedule-network calculation, not a guarantee of real-world delivery. Integrations should map schedule activities to WBS nodes through the neutral activity link rather than embedding domain-specific assumptions.

## Validation
Domain tests cover parallel branches, critical-path selection, float, invalid dependencies, cycles, and zero-duration milestones. They have not been executed in this remote editing session.
