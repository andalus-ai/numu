# Neutral Planning — Milestone 2

## Scope

This milestone introduces an industry-neutral planning domain separate from construction and project-specific implementations.

- **Schedule plan**: owns editable schedule activities, precedence dependencies, and baseline history.
- **Schedule activity**: planned start/finish dates and an optional typed external work-item reference. The reference can identify a neutral scope node, software backlog item, deliverable, procurement package, or another vertical-specific item without introducing a dependency on that module.
- **Dependencies**: finish-to-start precedence links are constrained to activities in the same plan; self-links and cycles are rejected.
- **Baseline**: immutable, sequenced snapshot of activities and dependencies. The working forecast may change after a baseline without rewriting it.
- **Application ports/use cases**: create a uniquely coded plan, revise a working activity while preserving its identity, and create a baseline with an injected clock.

## Boundary decisions

Planning has no construction, BOQ, budget, persistence, HTTP, or framework dependency. Persistence adapters must store the plan and its baseline history atomically. Baseline creation is append-only and sequence numbers are allocated within the owning plan. Concurrent writers therefore require adapter-level optimistic locking or a plan-scoped lock before production deployment.

This milestone provides the domain and use-case contracts, not a production persistence adapter or approval workflow. Approval authorization, audit actor identity, tenant scoping, optimistic concurrency tokens, and persistence-level append-only enforcement are acceptance gates for the next adapter/production-readiness increment.

## Acceptance tests

- Baseline snapshot stays unchanged when working dates move.
- Baseline collections are defensive immutable copies.
- Duplicate activity codes (case-insensitive), invalid date ranges, missing dependency endpoints, and cyclic dependencies are rejected.
- Retired plans reject mutation and baseline creation.
- External work-item references remain generic and do not couple the module to a vertical.
