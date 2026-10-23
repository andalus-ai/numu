# Neutral Planning — Milestone 11: Work Breakdown Structure

## Objective

Add a reusable, domain-neutral work-breakdown tree that can represent project phases, software epics/features, procurement packages, research tasks, and construction work packages without making any of those domains the core model.

## Included

- Immutable `WorkBreakdownNode` with stable ID, optional parent ID, code, name, and sibling position.
- Immutable `WorkBreakdownStructure` with exactly one root and validated parent references.
- Deterministic child ordering and depth-first descendant traversal.
- Validation for duplicate IDs, missing parents, cycles, multiple roots, duplicate sibling codes (case-insensitive), and duplicate sibling positions.
- Tests for traversal and integrity constraints.

## Deliberate boundaries

The WBS stores structure only. It does not own schedule activities, cost accounts, BoQ lines, quantities, currencies, or domain-specific approval state. Modules may reference WBS nodes through stable IDs and map their own semantics at their boundary. Rollup weighting remains an explicit application policy; the framework does not infer weights from node depth or sum unlike quantities.

## Validation

Tests have been authored but not executed in this remote GitHub editing session. Run the planning Maven reactor before marking the PR ready.
