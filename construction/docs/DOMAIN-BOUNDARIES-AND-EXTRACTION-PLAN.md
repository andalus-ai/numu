# Construction Domain Boundaries and Extraction Plan

**Status:** Proposed  
**Scope:** First step toward extracting reusable capabilities from `numou-construction` without breaking existing construction workflows.

## Executive decision

Keep `numou-construction` as the construction-industry specialization. Extract reusable concepts only when their rules and lifecycle are genuinely independent of construction. Start with **scope breakdown structures (WBS)**, then **measured quantity schedules**, and only then consider **progress measurement**. Do not perform a package rename or mass move as a single change: BOQ revisioning, authorization, tenant scoping, persistence, and integration tests already form a meaningful workflow.

The first change should establish explicit ownership and migration contracts. Follow-up implementation PRs can then move one bounded context at a time with compatibility adapters and tests.

## What the current source tells us

The current construction domain has separate packages for `wbs`, `boq`, and `progress` under:

`construction-domain/src/main/java/tech/kayys/numou/construction/domain/`

The current `WbsNode` stores `projectId`, `parentNodeId`, `code`, `name`, a construction WBS node type, and an optional `projectTaskId`. It raises `WbsNodeCreated`. This is a useful starting point, but task linking and construction-specific node types should not become mandatory dependencies of a generic breakdown structure.

The current `Boq` owns a draft/review/approved lifecycle and a current revision. BOQ behavior also spans revision snapshots, tenant-aware PostgreSQL storage, revision allocation, authorization, outbox handling, and integration tests. That lifecycle should be preserved during extraction rather than recreated in a generic module.

The current `ProgressMeasurement` references both a BOQ and a BOQ item, as well as a project. Its current shape is therefore construction/commercially coupled; it is not yet a generic progress model.

The construction domain POM depends on both `numou-foundation` and `numou-project-domain`. The construction aggregator has four modules: domain, SPI, application, and adapter. Keep the existing dependency direction: adapters depend inward on application/domain, and domain modules must not depend on adapters or framework runtimes.

## Proposed ownership matrix

| Capability | Target ownership | What belongs there | What remains construction-specific |
|---|---|---|---|
| Project identity and project lifecycle | `numou-project-domain` (existing) | Project identity and lifecycle already owned by Project | Site execution and construction delivery rules |
| Work/scope breakdown structure | Candidate `numou-scope-domain` | Hierarchical breakdowns, node IDs, codes, labels, parent/child invariants, structure type, baseline/revision identity | Construction WBS templates, trade/work-package conventions, project-task linking policy |
| Measured quantity schedule | Candidate `numou-quantity-domain` | Quantity lines, typed units, quantities, line identity, schedule revisions and validation independent of pricing | Construction BOQ conventions, take-off evidence, contractual measurement rules, standard method-of-measurement rules |
| Commercial BOQ and BOQ revision workflow | Initially remains in `numou-construction` | Existing BOQ lifecycle, approval, authorization, tenant scope, revision allocation, snapshots and transactional workflow | All existing construction contract and commercial semantics |
| Progress measurement | Initially remains in `numou-construction`; reassess later | Current approved measurement workflow remains intact | BOQ-item linkage, site evidence, construction acceptance and certification |
| Budget and schedule | Existing budget/scheduling modules | Budget controls and schedule planning in their current bounded contexts | Construction-specific cost codes, calendars, productivity rules, and site execution integration |
| Construction execution | `numou-construction` | Site, daily reports, HSE, QA/QC, variations, payment certificates, handover, warranty and closeout | All industry-specific behavior |

The candidate module names are proposals, not modules that already exist.

## Boundary rules

1. **Do not make a generic module depend on construction.** Reusable scope/quantity domain modules must not import `tech.kayys.numou.construction.*`.
2. **Do not make a generic scope model own a project aggregate.** Reference a project through an identifier or a narrow port; avoid importing project persistence or application classes into the scope domain.
3. **Keep bounded contexts separate.** A WBS node may be referenced by a BOQ line, schedule activity, budget line, or task, but the WBS aggregate must not own their lifecycle.
4. **Separate quantities from prices.** Quantity/unit validation can be reusable; rates, currencies, taxes, contractual pricing, and payment certification need explicit commercial ownership.
5. **Keep unit semantics explicit.** Never compare or aggregate quantities with incompatible units. Define conversion policy explicitly and test rounding/precision.
6. **Preserve revisions as business history.** Approved baselines and BOQ revisions must not be silently overwritten. A revision must identify what was approved and by whom/when where the existing workflow requires it.
7. **Avoid oversized aggregates.** A large WBS or BOQ should not require loading every descendant/line for a small change. Enforce hierarchy and uniqueness invariants through appropriate domain rules and repository constraints.
8. **Keep tenancy and authorization at application/workflow boundaries.** Do not move authorization decisions into a generic value object, and do not weaken the existing tenant-scoped BOQ persistence or revision protocol.
9. **Prefer explicit integration contracts over cross-module entity sharing.** Link contexts with stable IDs and events/ports; map to local read models where needed.

## Staged migration

### Phase 0 — Baseline and inventory

- Run the full Maven reactor and record existing failures before moving code.
- Map every current WBS/BOQ/progress class, SPI, command/handler, adapter, migration, test, and script.
- Record package dependencies and consumer imports.
- Preserve the current BOQ revision, tenant, authorization, idempotency, and concurrency guarantees.

**Exit criteria:** a dependency map and a green baseline, or a documented list of pre-existing failures that is not attributed to extraction work.

### Phase 1 — Extract scope breakdown (WBS)

- Introduce a framework-free `numou-scope-domain` with a generic breakdown/structure model.
- Model the structure type explicitly so WBS is one supported breakdown rather than the only possible one.
- Keep generic node identity, parent relationship, code/name, and structural invariants in scope.
- Keep project-task linking behind a project/scheduling integration contract, not as a required generic domain dependency.
- Add a construction mapping/adapter that preserves current WBS APIs while consumers migrate.
- Add tests for parent/child validity, invalid references, duplicate codes within the agreed scope, node type rules, and baseline/revision behavior where applicable.

**Exit criteria:** generic scope domain has no construction imports; construction consumers continue to work through the compatibility layer; reactor and architecture tests pass.

### Phase 2 — Extract measured quantities, not the entire BOQ workflow

- Introduce a framework-free quantity schedule/line model only for invariants proven to be reusable.
- Decide explicitly whether the target is a generic quantity schedule or a commercial BOQ. Do not disguise a commercial BOQ as a generic quantity list.
- Keep BOQ revision snapshots, approval, authorization, tenant scoping, outbox/transaction coordination, and construction measurement standards in the construction context initially.
- Provide mapping between construction BOQ items and reusable quantity lines without allowing generic code to import construction classes.
- Test incompatible units, precision/rounding, revision immutability, and mapping compatibility.

**Exit criteria:** reusable quantity model has no construction imports and existing BOQ lifecycle/integration tests remain intact.

### Phase 3 — Reassess progress and commercial ownership

- Separate generic progress concepts only if a real non-construction consumer exists.
- Keep construction evidence, BOQ item linkage, approval, measurement certification, payment certificates, and contract-specific valuation in construction until their boundaries are explicitly modeled.
- Integrate budget and scheduling through stable IDs/ports instead of merging their aggregates into construction.

### Phase 4 — Remove compatibility code only after consumers migrate

- Migrate all known callers.
- Add module-level dependency/architecture tests.
- Remove old types only in a separate, reviewable change with a migration note.
- Update module catalogues, dependency documentation, and examples.

## Required tests and quality gates

- **Domain tests:** hierarchy invariants, node identity, duplicate-code policy, valid transitions, quantity/unit compatibility, rounding, and revision behavior.
- **Architecture tests:** scope/quantity domain packages do not depend on construction, adapters, Quarkus, or persistence implementations.
- **Application tests:** authorization, tenant context, command validation, and idempotency continue to be enforced.
- **Persistence/integration tests:** BOQ revision allocation, concurrent updates, tenant isolation, outbox atomicity, and full lifecycle.
- **Compatibility tests:** old construction entry points map correctly to the new generic models during migration.
- **Build:** run the repository's documented Maven reactor/CI checks and report pre-existing failures separately.

## Non-goals for the first extraction PR

- No immediate renaming or deleting of existing WBS/BOQ classes.
- No rewriting of the BOQ approval or revision workflow.
- No moving construction-specific contract, tax, payment certification, HSE, QA/QC, or handover behavior into generic modules.
- No introducing persistence/framework dependencies into domain modules.
- No claiming that a generic module is reusable until its construction imports and domain assumptions have been removed and tested.

## Suggested follow-up PR sequence

1. `feat(scope): introduce generic breakdown domain`
2. `refactor(construction): adapt WBS to scope domain`
3. `feat(quantity): introduce reusable measured quantity schedule`
4. `refactor(construction): map BOQ items to quantity schedule`
5. `refactor(construction): isolate generic progress only where justified`

Each follow-up should be independently buildable and should preserve the existing construction workflows.
