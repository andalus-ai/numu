# Cargo & Courier — Milestone 8: ETA, Optimization and Analytics

## Scope
M8 establishes auditable ETA snapshots and versioned courier route plans. It deliberately does not claim a production-grade machine-learning predictor or an optimizer without validated operational data.

## ETA snapshots
- Each estimate is tenant-scoped and linked to a shipment.
- Estimates retain a time window, confidence, source, model/rules version and explanation code.
- A new estimate is appended rather than overwriting prior estimates. The estimate does not alter a contractual promised-delivery timestamp.
- Confidence is bounded from 0 to 1 and invalid/inverted ETA windows are rejected.

## Route plans
- A plan is versioned by tenant, branch, courier and service date.
- Ordered stops have contiguous sequence numbers, unique references, typed stop purpose and planned arrival timestamps.
- Planned distance and duration are optional metrics; absent data is not fabricated.
- PostgreSQL composite tenant keys and constraints isolate estimates, plans and stops.

## Production follow-up
- Implement tenant-explicit repositories and application handlers, branch/courier authorization, and audit events.
- Connect a pluggable ETA provider using calibrated historical transit times and/or a traffic provider. Track model version, calibration and prediction error.
- Implement optimization behind a provider port with constraints for capacity, service windows, pickup/delivery precedence, breaks, driver shifts and hub cutoffs. Store input/output versions for reproducibility.
- Add event-fed analytics with late-event handling, privacy controls, retention policies and tenant-level aggregation.
- Run Maven domain tests and PostgreSQL migration/Testcontainers suites; they have not been executed in this environment.
