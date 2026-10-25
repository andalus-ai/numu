# Neutral Planning — Milestone 42: Authoritative cost-source integration gate

## Outcome

Prepare the neutral earned-value pipeline for a production finance/ERP/BoQ provider without inventing a persistence schema or silently treating estimates as ledger facts. M40 defines the reader port, M41 validates its returned evidence, and the M39 use case composes that evidence into a report. This milestone records the concrete source-selection and integration requirements that must be satisfied before a production provider can be implemented safely.

## Repository inspection result

The planning module exposes the following stable integration boundary:

- `CostEvidenceReader.findApprovedBudgets(tenantId, structureId, baselineId, nodeIds)`
- `CostEvidenceReader.findApprovedActualCosts(tenantId, structureId, asOfDate, nodeIds)`
- `ValidatingCostEvidenceReader` for defense-in-depth validation of returned rows
- `CalculateEarnedValueReport` for evidence completeness, report-scope, currency, and earned-value calculation

The repository paths inspected for this milestone did not establish an authoritative, queryable finance ledger / approved budget source and its schema. A guessed SQL adapter would couple planning to an unverified table layout and could report costs that are not reconciled or approved. No such adapter is added here.

## Required source-selection decision

Before implementing the concrete provider, the owning finance/ERP/BoQ module must identify and document:

1. **Budget authority** — the canonical approved budget/commitment record, baseline relationship, versioning rules, currency, and approval transition.
2. **Actual-cost authority** — the source of posted/reconciled actuals (not draft requisitions, purchase orders, estimates, or unposted journals), reversal/credit treatment, and the accounting cutoff policy.
3. **WBS allocation** — the durable mapping from source cost lines to neutral planning WBS nodes, including split allocations and unmapped-line handling.
4. **Tenant boundary** — how the tenant is bound to the authenticated principal and enforced in SQL/RLS or the equivalent persistence boundary.
5. **Idempotency and corrections** — how source transaction IDs, reversals, restatements, and re-runs avoid duplicate cumulative actuals.
6. **Currency policy** — the reporting currency, exchange-rate source/date, and whether the provider returns only native-currency evidence or an explicitly converted, traceable amount. Until conversion is specified, mixed currencies must fail closed.
7. **Audit provenance** — source document/transaction reference, approval timestamp, source version or cutoff, and a way to trace every amount back to source records.

## Provider acceptance contract

A production adapter is ready only when all of the following are demonstrated against the selected source:

- Tenant, structure, baseline, date, approval state, and requested WBS-node scope are constrained in the database query—not merely filtered in application memory.
- Only approved budget records and posted/reconciled actual-cost records are returned.
- Each requested node has at most one normalized result per budget baseline or reporting cutoff; source-line aggregation is deterministic and explainable.
- Reversals and credits are handled without double-counting, and repeated reads for the same cutoff are stable.
- Unknown/unmapped WBS allocations, missing source values, ambiguous approval state, mixed currency, and inconsistent source snapshots produce an explicit failure or a governed exception—not a silent zero.
- Integration tests use PostgreSQL (or the actual authoritative store), create at least two tenants, and prove a request for tenant A cannot return tenant B's evidence.
- Tests cover approval changes, baseline changes, reporting cutoffs, duplicate source rows, reversal/credit transactions, multiple source lines allocated to one WBS node, missing mappings, and query replay.
- The provider is wired through `ValidatingCostEvidenceReader` and the existing planning source adapter; the application use case remains independent of finance-specific persistence models.

## Next implementation step

Once the canonical source and schema are confirmed, implement the concrete adapter in the owning module (preferably alongside the source system's existing repositories/transaction semantics), then add PostgreSQL integration tests and dependency-injection wiring. Do not add a second planning-owned ledger or copy finance tables into planning merely to satisfy the report API.

## Scope intentionally excluded

This milestone does not claim to implement a finance ledger, invent schema, convert currencies, infer approval from record presence, or treat procurement commitments as actual costs. The existing earned-value calculation remains evidence-driven and neutral across construction, software delivery, and other project domains.

## Validation status

Documentation-only decision gate. No production provider or executable integration test was added because the authoritative source/schema could not be verified from the repository paths inspected. This is an explicit integration blocker, not a claim that the source integration is complete.
