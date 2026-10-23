# Neutral Planning — Milestone 18: Schedule Performance and Forecasting

## Goal
Estimate finish dates and schedule variance from immutable progress observations without requiring construction-specific quantities, budgets, or earned-value assumptions.

## Forecast method
- Input: two or more immutable progress snapshots for the same tenant, WBS structure and node, plus an explicit planned finish date.
- Observations are sorted by reporting date and recorded time.
- A linear least-squares regression estimates progress percentage points per elapsed calendar day.
- Remaining duration is the ceiling of remaining percentage points divided by the estimated rate.
- Forecast finish is the latest reporting date plus estimated remaining calendar days.
- Schedule variance is forecast finish minus planned finish in calendar days. Positive is late; negative is early.

## Guardrails
- Mixed tenant or WBS scopes are rejected.
- At least two distinct reporting dates are required.
- Completed work, zero/negative trend, or non-finite forecasts are rejected rather than manufacturing an ETA.
- Forecast output includes method version, observation count, latest snapshot ID/date, completion and rate so consumers can explain the result.

## Limitations
This is a deterministic trend projection, not a confidence interval or guarantee. It assumes the observed trend remains representative and does not account for future resource changes, dependencies, calendars, constraints, or remaining-work estimates. The planned finish date is supplied by the caller; this milestone does not infer schedule dates from an absent or unverified plan model.

## Validation
Tests are authored but have not been executed in this remote editing session. Run the planning-domain test suite before merge.
