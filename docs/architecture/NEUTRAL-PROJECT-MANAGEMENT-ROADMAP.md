# Neutral project management: implementation roadmap

**Status:** Implementation plan  
**Canonical repository:** https://github.com/andalus-ai/numu  
**Related documents:** [ADR-001](ADR-001-neutral-project-management-core.md), [Capability ownership matrix](DOMAIN-CAPABILITY-MATRIX.md)

## Goal

Make project management a reusable platform capability. Construction remains a rich extension; software delivery, manufacturing, professional services and future project domains should use the same core without importing construction-specific concepts.

This plan is deliberately incremental. It avoids a risky mass rename or a migration that breaks current construction workflows, tenant safety, BOQ revisions or the concurrency fixes already present in the repository.

## Current repository observations

The repository already contains useful building blocks:

- a domain/application/SPI/adapter split in the project module;
- a generic project aggregate and generic project tasks, milestones, phases, risks and issues;
- a separate scheduling bounded context;
- a separate quantity domain with typed quantities, units and quantity-schedule lines;
- construction-specific WBS and BOQ aggregates, including revision and PostgreSQL workflow infrastructure;
- a proposed ADR describing domain-neutral project management.

The important gap is not simply a missing CRUD module. The ownership and contracts between generic project/scope/scheduling/controls and industry-specific workflows need to be explicit, enforceable and demonstrated by more than one industry.

## Delivery strategy

Work in coherent batches and open a pull request only after a vertical slice is integrated, tested and documented. Keep commits within a branch as needed; do not create a separate PR for every small refactor. Avoid mixing unrelated Syirkah/finance changes into the project-management PR.

### Phase 0 — Baseline and inventory

- Record the main-branch commit and current build/test state before changing behavior.
- Inventory project, task, WBS, scheduling, quantity, budget and construction BOQ types, repositories, migrations, commands, event consumers and API consumers.
- Build a dependency map for domain, application, SPI, adapter and module POMs.
- Identify existing construction WBS/BOQ workflows and tests that must remain compatible.
- Categorize failures as pre-existing or introduced by the branch.

**Exit:** an explicit dependency map and reproducible test baseline, including documented pre-existing failures.

### Phase 1 — Enforce neutral project boundaries

- Keep generic project identity/lifecycle independent of industry packages.
- Add architecture tests that fail if the project domain imports construction or future industry extension packages.
- Extend the same rule to any neutral scope, scheduling and quantity domains as those contracts are introduced.
- Keep the dependency direction extension -> neutral capability, never neutral capability -> extension.
- Review generic enums and fields for industry-specific values; prefer extension-owned policies and typed metadata over a universal enum containing every industry's workflow states.

**Exit:** boundary tests run in CI and fail on a deliberate reverse dependency.

### Phase 2 — Extract a neutral breakdown structure

- Define a framework-free scope/breakdown contract: structure ID/type, node ID, project reference, optional parent ID, code, label/description, ordering and lifecycle/revision metadata where required.
- Enforce valid parent references, no cycles, no cross-project parentage, stable node identity and configured code uniqueness.
- Keep scheduling activities separate from WBS nodes; link them through IDs or explicit ports.
- Keep task linking as an integration capability, not a mandatory construction dependency.
- Add an adapter mapping construction WBS APIs and persisted data to the neutral scope model. Do not mass-move classes until compatibility is proven.
- Use database constraints/transactional locking where required to close concurrent hierarchy mutation races; domain-only checks cannot protect against two concurrent writes.

**Exit:** neutral scope tests pass; construction WBS behavior and persisted records remain compatible; no construction imports exist in the neutral scope domain.

### Phase 3 — Keep quantity schedules distinct from BOQ

- Reuse the existing quantity primitives where their semantics fit; improve unit validation, normalization, precision, rounding and conversion policies only through explicit contracts.
- Keep a generic quantity schedule distinct from a construction Bill of Quantities.
- Keep BOQ revision allocation, approval, authorization, tenant scoping, snapshots, outbox coordination, measurement standards, rate analysis and payment valuation in construction until each boundary is proven.
- Define mappings between construction BOQ lines and neutral quantity-schedule lines without making the quantity domain depend on construction.
- Test incompatible units, duplicate identifiers/codes/order, precision/rounding, and snapshot immutability.

**Exit:** quantity schedules are reusable without construction imports and existing BOQ lifecycle/concurrency tests remain green.

### Phase 4 — Strengthen planning and project controls

- Keep project scope, schedule activities, dependencies, resource availability, budget lines, actual costs and progress observations distinct but linkable.
- Support schedule versions and approved baselines; forecast changes must not silently rewrite the baseline.
- Separate planned value, earned value, actual cost, committed cost, forecast cost, certified value and cash paid.
- Require every progress observation to declare its source, measurement basis, period, status and approver/evidence references where applicable.
- Add application-level orchestration for cross-context use cases instead of expanding a single aggregate across the project lifecycle.
- Preserve atomicity, optimistic concurrency, idempotency, tenant context, audit and transactional outbox guarantees.

**Exit:** an end-to-end project-controls scenario can reconcile baseline scope, planned dates/cost, accepted progress, actual/committed cost and forecast variance.

### Phase 5 — Prove the extension model with software delivery

Implement a deliberately small but complete software-delivery vertical slice that uses neutral project/scope/planning/control contracts and owns software-specific concepts:

- backlog items such as epic, feature, story, task and defect;
- acceptance criteria and review/verification states;
- iteration/release planning and dependencies;
- links to repository, pull request, build, test and deployment evidence;
- effort-based estimation and progress policy;
- release gates and change history.

The software extension must build and test without the construction module on its compile/test classpath. Do not represent stories as BOQ lines or force story points into physical quantity units.

**Exit:** a non-construction workflow demonstrates that the neutral core is useful in practice.

### Phase 6 — Complete construction integration and migration

- Connect neutral project/scope/planning/control IDs to construction BOQ, field progress, procurement, contracts, variations, quality/HSE, payments and closeout through explicit application ports/events.
- Preserve the existing construction-specific commercial workflow and API compatibility while migrating consumers.
- Add migration preflight/postflight checks, data reconciliation and rollback instructions for any schema or identity migration.
- Remove compatibility code only after all consumers have migrated and tests cover persisted legacy records.

**Exit:** construction remains functional as an extension, with traceability from approved scope/baseline through verified progress and commercial valuation.

### Phase 7 — Broaden the extension ecosystem

Only after the neutral boundary is proven, evaluate manufacturing and professional-services profiles. Reuse stable contracts; do not pre-build empty modules or invent abstractions without a second concrete use case.

## Required cross-cutting quality gates

### Domain and architecture

- No reverse dependency from neutral modules to industry modules.
- Domain code contains no persistence/framework dependencies.
- Aggregates validate restored and newly created state.
- Hierarchies reject cycles, invalid parentage and cross-project references.
- Unit conversion is explicit; incompatible units are never silently summed.
- Approved snapshots/baselines are immutable.

### Persistence and reliability

- Commands are atomic; partial updates roll back.
- Concurrent hierarchy, revision and approval writes cannot bypass invariants.
- Retry after timeout is idempotent for externally observable commands.
- Tenant-scoped reads/writes cannot cross tenant boundaries.
- Outbox publication is reliable and consumer handling is idempotent where required.
- Audit history records actor, time, decision and reason for controlled transitions.

### End-to-end acceptance scenarios

1. Create a neutral project and scope tree without construction fields.
2. Reject a cycle, invalid parent, cross-project parent and configured duplicate code.
3. Create a schedule and approved baseline; update forecast without modifying the baseline.
4. Capture progress from a declared measurement basis and retain approval/evidence history.
5. Revise a construction BOQ while preserving the prior approved snapshot and authorization history.
6. Reconcile an approved construction quantity to progress valuation without double certification.
7. Run a software backlog/iteration/release workflow with no construction dependency.
8. Prove duplicate retries, concurrent approvals, rollback and tenant isolation against the real persistence adapter.
9. Build the relevant Maven reactor and downstream dependents; report any known pre-existing failures separately.

## Pull-request batching policy

Use a single long-lived implementation branch for each coherent vertical slice. Batch related domain, application, adapter, test, migration and documentation changes together. Prefer one reviewable PR for each major acceptance milestone rather than many PRs for individual classes.

A PR should include:
- the problem and domain boundary being changed;
- compatibility/migration notes;
- tests run and their exact results;
- known failures not introduced by the branch;
- explicit acceptance criteria checked;
- follow-up work intentionally excluded.

Do not merge solely because code compiles. Do not claim the build is green unless the relevant tests were actually run.

## Immediate next implementation slice

1. Add the neutral project-domain dependency rule and keep it green.
2. Inspect and map current WBS invariants, repository behavior and scope-lock concurrency guarantees.
3. Design the neutral scope contract and compatibility mapping before moving production classes.
4. Add scope-domain tests and a construction adapter in the same implementation batch.
5. Integrate quantity-schedule mapping and tests without moving the construction BOQ lifecycle.
6. Run module and reactor tests, then open one consolidated PR for the completed slice.
