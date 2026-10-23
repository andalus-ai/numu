# Neutral Planning — Milestone 23: Project Change Impact Analysis

## Goal

Compare two immutable revisions of a domain-neutral project schedule and explain schedule exposure created by their differences. The feature is suitable for construction, software delivery, product development, and other activity-based plans.

## Included

- Explicit tenant, WBS structure, plan identity, revision number, capture timestamp, project start, activity durations, and finish-to-start dependencies in each snapshot.
- Detection of added and removed activities, duration changes, and changed predecessor sets.
- Transitive downstream tracing over the union of both revisions' dependency graphs, so removed edges do not erase impact evidence.
- Before/after critical activity flags, newly critical and no-longer-critical activity lists, and forecast finish variance in calendar days.
- Stable finding identifiers derived from tenant, structure, plan, both revision numbers, change code, and activity ID.
- Deterministic ordering, immutable result collections, scope checks, and cycle/unknown-dependency validation through the existing critical-path calculator.

## Semantics and boundaries

- Positive finish variance means the new forecast finishes later; negative means earlier.
- Activities are compared by stable activity ID; the analyzer does not infer identity from names or descriptions.
- Critical exposure is based on the existing critical-path method's zero-total-float activities, not a claim that every listed critical activity belongs to one unique contiguous path.
- Dependencies are finish-to-start with calendar-day durations, matching the current forecast engine.
- Both snapshots must use the same project start, tenant, structure, and plan. Revision numbers must strictly increase and capture timestamps must not regress.
- This is analysis only: it does not approve a change, mutate a plan, estimate cost impact, or apply domain-specific governance.

## Verification

Run the planning-domain unit tests and the full Maven reactor build in CI. Remote repository editing does not execute Maven tests; no passing test claim is made here.
