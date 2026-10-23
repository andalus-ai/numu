# Neutral Planning — Milestone 22: Baseline Schedule Variance

## Goal
Compare an explicitly approved, immutable schedule baseline with a current dependency-aware schedule forecast, without embedding industry-specific rules in the planning core.

## Included
- Approved baseline metadata: tenant, structure, plan, revision, approval timestamp, project dates, and activity dates.
- Activity-level start/finish variance and criticality exposure.
- Project finish variance and counts for late and newly critical activities.
- Structured findings for baseline activities absent from the current forecast and new forecast activities absent from the baseline.
- Scope/revision validation, deterministic output ordering, and unit tests.

## Semantics and limitations
Positive variance means the forecast date is later than the baseline; negative variance means earlier. The current schedule forecast model does not preserve baseline criticality, so baseline criticality is conservatively represented as false in activity entries. Newly-critical counts are provisional until baseline criticality is explicitly stored. The calculator does not approve or mutate baselines or automatically reconcile missing activities.

## Validation
Tests are authored but have not been executed in this remote editing session. Run planning-domain tests and the full module build before merge.
