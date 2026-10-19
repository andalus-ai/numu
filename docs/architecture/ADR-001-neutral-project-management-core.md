# ADR-001: Domain-neutral project management with industry extensions

- **Status:** Proposed
- **Date:** 2026-10-19
- **Decision owners:** Numu maintainers

## Context

Numu has construction capabilities such as WBS, BOQ/quantity schedules, progress measurements, project controls, contracts, procurement, quality, field execution, and closeout. Project management should also support software delivery, manufacturing, professional services, and other project-based work.

Construction must not become the dependency root for generic project management. At the same time, the design must not erase meaningful differences between a construction BOQ, a software backlog, and a manufacturing production order.

## Decision

Make project management domain-neutral. Treat construction and other industries as extensions that consume neutral capabilities and add their own aggregates, policies, workflows, and adapters.

### Logical capability layers

1. **Platform:** tenancy, identity, authorization, audit, transaction boundaries, event delivery.
2. **Project management:** project identity and lifecycle, ownership, portfolio/program relationships, milestones, governance and project configuration.
3. **Scope and planning:** scope hierarchy/WBS, deliverables, activities, dependencies, calendars, schedule versions, approved baselines, resource planning.
4. **Project controls:** progress observations, approvals, budgets, commitments, actuals, forecasts, variance and performance snapshots.
5. **Reusable business capabilities:** quantity schedules, contracts, procurement, documents, quality and change control where the semantics are genuinely shared.
6. **Industry extensions:** construction, software delivery, manufacturing, professional services, and future domains.

This is a logical decomposition, not a mandate to create a Maven module for every bullet immediately. Physical modules should follow existing repository conventions and be split when the dependency boundary is clear.

## Domain boundaries

### Project

The neutral Project owns identity, tenant/organization scope, name/description, lifecycle, project profile, ownership and generic governance metadata. It must not contain construction-only fields such as site address, building permit, concrete volume, drawing number, or construction contract type.

Industry-specific project context belongs in extension aggregates linked by project ID.

### WBS and scope

WBS is a neutral hierarchy for decomposing scope. Its core invariants include stable node identity, valid parent references, no cycles, no cross-project parent references, deterministic ordering, and uniqueness rules for sibling identifiers/codes as defined by policy.

A WBS node must not require a physical quantity, construction measurement unit, site, drawing, or subcontractor. WBS is not a schedule: schedule activities and dependency relationships are modeled separately and linked to scope nodes.

### Scheduling and baselines

The neutral scheduling model should represent activities, dates/durations, dependency relationships, calendars, constraints, and schedule versions. An approved baseline is an immutable reference for comparison; replanning must not silently rewrite the approved baseline.

Industry-specific planning methods (for example, CPM conventions or sprint planning) belong in policies/extensions, not a universal project-type conditional spread across the domain.

### Progress and project controls

Keep reported, submitted, verified, approved, and rejected progress states distinct where the workflow requires them. Shared controls can represent budgets, commitments, actual costs, forecasts, and versioned performance snapshots, but must preserve the basis and source of every measurement.

Construction physical quantities, accepted software work items, and manufacturing output are different measurement semantics. A neutral progress contract may support these through policy/strategy interfaces; it must not assume all progress is a quantity or story-point percentage.

### BOQ and quantity schedules

Keep the reusable quantity-schedule capability separate from the construction-specific Bill of Quantities.

The reusable capability may support versioned lines, descriptions, quantities, units, prices/rates, currency, totals, approval, and immutable revisions where those concepts are applicable. Construction adds measurement rules, takeoff, construction classifications, rate analysis, contractual valuation, variations, and payment certificates.

Do not force software development into BOQ semantics. Software delivery will typically use backlog/work-item concepts; manufacturing may use bills of material and routings. These may reuse common primitives where appropriate, but must retain their own domain meaning.

### Change, documents, quality and procurement

Generic change control can model proposals, decisions, approvals, impact references, and audit history. Construction variations, software change requests, and manufacturing engineering changes provide their own policy and workflow details.

Document versioning and traceability may be shared capabilities. Construction drawings/RFIs, software source/release artifacts, and manufacturing specifications remain industry-specific records.

## Dependency rules

- The neutral project-management domain must not import construction domain packages.
- Neutral scope/WBS and scheduling must not depend on construction BOQ types.
- Construction may depend on neutral project, scope, planning, controls, quantity-schedule, and contract contracts.
- A software-delivery extension must be able to use project, scope, planning, resources, and generic change control without depending on construction packages or construction database tables.
- Cross-domain orchestration belongs in application services using explicit ports and domain events; one domain must not access another domain's internal repositories.
- Shared primitives should be extracted only when their semantics are genuinely shared. Avoid a generic abstraction that makes distinct business concepts indistinguishable.
- Persisted snapshots, approvals, and commercial revisions must remain reproducible and auditable.

## Migration approach

1. **Inventory current modules and dependencies.** Identify which types are neutral already, especially scope/WBS validation and quantity-schedule primitives. Preserve current APIs and data compatibility where possible.
2. **Establish neutral project/scope contracts.** Define invariants and ownership for Project, WBS/scope, deliverables, activities, baselines, progress and resource planning before broad code movement.
3. **Make construction consume those contracts.** Keep construction-only behavior in the construction extension; avoid a large-bang package rename or migration.
4. **Add architecture enforcement.** Add module dependency checks so neutral modules cannot import construction packages. Add domain tests for tree validation, tenant/project boundaries, baseline immutability, revision immutability, idempotency, and concurrent approvals.
5. **Prove the boundary with a second vertical slice.** Implement a small software-delivery flow using the neutral core and backlog-specific extension. It must build and test without the construction extension.
6. **Split physical Maven modules incrementally.** Align module boundaries with proven domain ownership and existing reactor conventions; do not add empty modules solely to mirror the logical diagram.

## Acceptance criteria

- A project and WBS can be created without construction-specific fields or construction module dependencies.
- WBS validation rejects cycles, invalid parents, cross-project references, and configured duplicate sibling identifiers/codes before persistence or event publication.
- Schedule revisions do not mutate an approved baseline.
- Progress and cost snapshots identify their source, version, status and measurement basis.
- Quantity-schedule revisions remain immutable and support reconciliation; construction-specific valuation rules remain outside the neutral core.
- A software-delivery use case can represent epics/stories, acceptance, iteration/release planning and progress without construction classes.
- Dependency checks fail CI if a neutral module imports construction packages.
- Persistence and application tests cover rollback, duplicate command retries, optimistic concurrency and audit/event consistency where applicable.

## Consequences

### Positive

- Construction remains a first-class, rich extension without owning generic project management.
- Software delivery and other industries can reuse core planning and governance without inheriting construction assumptions.
- Domain boundaries become testable and enforceable.

### Trade-offs

- Some workflows will share orchestration patterns but require different domain policies and data models.
- Existing construction code may need incremental extraction and compatibility adapters.
- The first implementation should be a narrow vertical slice; attempting to deliver every capability at once would increase migration risk.

## First implementation slice

The first code slice should establish and test the neutral Project and scope/WBS contracts, then adapt construction to consume them. Do not start by moving all construction classes or by making BOQ universal. The next vertical slice should connect an approved scope baseline to planning and progress; a software-delivery example should then prove that these contracts are genuinely neutral.
