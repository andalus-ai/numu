# Numu capability ownership matrix

**Status:** Working architecture contract  
**Repository:** `andalus-ai/numu`  
**Related decision:** [ADR-001 — Neutral project management core](ADR-001-neutral-project-management-core.md)

## Purpose

Numu must support project-based work across industries without turning the project module into a construction module with optional fields. Reuse the concepts that have stable shared meaning; keep specialized commercial and operational semantics in extensions.

This matrix describes target ownership, not a claim that every capability is already complete.

## Capability ownership

| Capability | Neutral core responsibility | Extension responsibility | Boundary / non-goal |
|---|---|---|---|
| Project identity and lifecycle | Identity, name, description, lifecycle, ownership references, project profile and generic governance | Construction site context; software product/repository context; manufacturing plant/order context | No mandatory site, drawing, repository, sprint, production line or permit fields on the generic Project |
| Scope breakdown / WBS | Hierarchical structures, stable IDs, parent/child integrity, ordering, codes/labels, structure type and revision references | Construction work packages/trades; software epics/features; manufacturing work breakdown | Do not require quantities or a construction WBS node type in the neutral model |
| Work items | Generic work item identity, title, description, lifecycle, owner, priority, dates, labels and links | Stories, defects, pull requests, site activities, inspection actions and production tasks | Avoid one enum containing every industry's workflow state |
| Scheduling | Activities, dependency links, calendars, constraints, schedule versions and baseline comparisons | Construction CPM and work calendars; software iterations/releases; manufacturing routings and capacity planning | A WBS hierarchy is not a schedule; avoid coupling all tasks to a single scheduling algorithm |
| Progress | Source-aware observations, submissions, verification/approval where required, status and history | Installed quantities; accepted stories; production yield; service deliverables | Do not assume all progress is a physical quantity or percentage |
| Budget and controls | Budget versions, commitments, actuals, forecasts, variance and source-linked snapshots | Construction cost codes and earned-value basis; software labor/cloud budgets; manufacturing material and conversion costs | Keep budget, actual cost, earned value, certified value and cash paid distinct |
| Quantity schedule | Reusable quantities, typed units, line IDs, ordering, explicit unit compatibility and aggregation | Construction BOQ, takeoff, measurement rules, rate analysis and valuation; manufacturing BOMs where semantics fit | A quantity schedule is not automatically a BOQ, bill of materials, or software backlog |
| Estimates and rates | Shared money/quantity primitives only when semantics and precision are compatible | Construction unit-rate analysis; software effort estimation; manufacturing standard cost | Do not put industry-specific formulas into a generic universal calculator |
| Change control | Change proposal, status, decision, rationale, impact references, authorization and audit metadata where genuinely shared | Construction variation/EOT; software change request/release approval; engineering change order | Approval must not mutate approved baselines or historical commercial records |
| Risk and issue | Generic risk/issue lifecycle, ownership, treatment/action links and audit history | Industry taxonomies, severity scales and compliance requirements | Keep classification configurable; avoid hardcoding construction-only categories |
| Contracts and commercial terms | Generic parties/terms only if independently reusable and modeled without assumptions | Construction contract administration, claims, retention, advance recovery and payment certificates; software SOWs and subscriptions | Do not classify every project change as a construction change order |
| Procurement and resources | Shared references and capability contracts where stable | Construction RFQ/PO/material delivery; software vendor/license/cloud procurement; manufacturing materials and capacity | Keep inventory, workforce, and supplier lifecycles in their own bounded contexts |
| Documents and evidence | Versioned document references, relationships, provenance and access policy | Drawings/RFIs, source/release artifacts, inspection records, manufacturing specifications | Store binary content through document/storage capabilities, not project aggregates |
| Quality and acceptance | Configurable criteria, review/acceptance decisions and evidence references | Construction inspections/NCRs; software tests/defects/release gates; manufacturing quality plans | Do not make HSE permits or site inspections mandatory for every project |
| Reporting | Common project, schedule, cost and status read models with metric provenance | Industry-specific KPIs and domain dashboards | Metrics must declare definitions, units, time window and source basis |

## Dependency rules

1. Neutral project, scope, scheduling and control domains may depend on platform primitives and narrow stable contracts, never on construction/software/manufacturing packages.
2. Extensions may depend inward on neutral contracts. The reverse dependency is forbidden.
3. Cross-context relationships use stable identifiers, explicit ports, domain events or read models; do not share another bounded context's mutable aggregate.
4. Domain policy belongs with the concept it governs. Application services orchestrate workflows but do not reimplement invariants.
5. Extract a shared abstraction only when two real use cases have the same semantics. Similar field names are not sufficient evidence.
6. Keep authorization, tenant isolation, idempotency, transaction boundaries, audit and outbox delivery intact during extraction.
7. Approved baselines, accepted snapshots and commercial revisions are historical facts. A new plan or revision supersedes them explicitly; it does not rewrite them.
8. A project profile selects enabled capabilities and policy sets. It must not cause construction conditionals to spread throughout neutral domain code.

## Representative profiles

### Construction

Neutral project + scope/WBS + schedule + budget/controls + quantity schedule, with construction BOQ/revisions, takeoff, site execution, procurement, contracts, QA/QC, HSE, variations, payment certificates and closeout.

### Software delivery

Neutral project + scope/work items + schedule/iterations + resources + budget/controls, with backlog types, story/effort estimation, repository/CI links, test and defect workflows, release approvals and deployment evidence. No construction BOQ or site execution dependency.

### Manufacturing

Neutral project/work breakdown + schedule + resources + controls, with manufacturing-specific BOMs, routings, work orders, production output, inventory and quality. Do not force a manufacturing BOM into construction BOQ rules.

## Completion evidence

A capability is not considered generalized merely because its package was renamed. Evidence should include:

- architecture tests that forbid reverse dependencies;
- domain tests for invariants and invalid transitions;
- application tests for authorization and orchestration;
- persistence tests for tenant isolation, rollback, concurrency and idempotent retries where applicable;
- at least one construction workflow and one non-construction workflow consuming the neutral contract;
- migration and compatibility tests for existing construction records and public APIs.
