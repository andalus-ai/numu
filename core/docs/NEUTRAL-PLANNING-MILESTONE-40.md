# Neutral Planning — Milestone 40: Cost-evidence integration boundary

## Outcome

Add a concrete adapter between a unified cost-evidence reader and the planning application's existing budget/actual-cost output ports. This gives finance, ERP, procurement, and BoQ integrations one narrow contract to implement without making planning depend on any one domain module.

## Included

- `CostEvidenceReader`: tenant- and scope-aware provider contract for approved budget evidence and date-scoped actual-cost evidence.
- `CostEvidenceSourceAdapter`: implements both planning output ports and forwards tenant, structure, baseline/date, and requested WBS node scope.
- Defensive checks for missing scope, duplicate node identifiers, null provider results, and immutable result snapshots.
- Unit tests covering scope forwarding, invalid duplicate-node requests, and null provider behavior.

## Security and correctness boundary

The reader implementation MUST enforce tenant isolation in its underlying query and return only approved evidence. The adapter does not claim to implement database authorization; the planning use case still validates returned records against requested scope and completeness.

## Explicit limitation

This milestone does not bind to a specific finance/ERP/BoQ schema because the repository's canonical cost ledger and ownership model must be selected before a concrete persistence query can be implemented safely. No database schema, endpoint, or construction-specific dependency is guessed into the neutral planning module. The next integration step is to implement `CostEvidenceReader` against the chosen authoritative cost source and prove reconciliation with integration tests.
