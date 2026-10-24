# Neutral Planning — Milestone 41: Cost-evidence provider integrity

## Outcome

Add a reusable defensive decorator around any finance, ERP, procurement, or BoQ cost-evidence reader. It validates provider responses against the exact request scope before those responses reach the planning application.

## Included

- ValidatingCostEvidenceReader, a framework-neutral decorator for the M40 CostEvidenceReader port.
- Rejects null responses or rows, cross-tenant and cross-structure records, wrong baselines/reporting dates, unapproved evidence, unrequested nodes, and duplicate node evidence.
- Immutable response snapshots.
- Unit tests for valid scoped evidence and key provider-integrity failure cases.

## Security boundary

The decorator is defense in depth. A production persistence provider must constrain tenant and scope in the underlying database query and authorization context; post-query validation is not a substitute for tenant isolation. It also does not claim to reconcile ledger transactions or select the canonical finance source.

## Follow-up

The repository structure inspected for this milestone did not expose a verified canonical cost ledger schema or authoritative finance/BoQ query path. Therefore this milestone deliberately does not invent database tables or represent an in-memory implementation as production persistence. The next step remains wiring a concrete provider to the selected authoritative source and adding database-backed reconciliation tests.

## Validation status

Tests are authored but have not been executed in this session.
