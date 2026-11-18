# Business Operating Platform — M01-A Repository Capability Inventory

- **Status:** Inventory baseline recorded; source-level ownership and dependency tracing remain in progress.
- **Repository:** `andalus-ai/numu`
- **Baseline:** `main`, tree `4a3c29b56ae92f85741e8c21623e2ef5bf4c8a31`
- **Inspection date:** 2026-11-04
- **Scope:** Repository tree and selected top-level Maven reactor descriptors.
- **Guardrails:** `AGENTS.md`, ADR-003, Capability Packaging and Composition Contract.

## 1. Executive result

The repository is already a broad capability platform. This milestone must therefore focus on consolidation, ownership clarity, composability and production evidence—not on adding parallel CRM, commerce, identity, tenancy, workflow, finance or workspace foundations.

The inspected Git tree contains:

| Measure | Observed |
|---|---:|
| Tracked tree entries (files and directories) | 14,271 |
| Maven POM files | 459 |
| Top-level Maven reactor roots | 40 |
| Repository-root `pom.xml` | Not present |

The prior `CAPABILITY-MODULE-ARCHITECTURE-AUDIT.md` records an earlier baseline of 11,285 tree entries and 369 POMs (2026-10-25). The current tree therefore reflects substantial repository growth. The two counts are point-in-time inventories, not evidence of equivalent increases in independently deployable capabilities.

## 2. Top-level Maven reactor inventory

Each path below is a top-level Maven reactor descriptor discovered in the current Git tree. Nested POMs are included in the 459 total but are not counted as separate top-level roots.

```text
application-platform
asset
automotive-parts-purchasing
automotive-parts
budget
commerce
common
construction
core
corporate
crm
developer-platform
document
documents
education
events
finance
geo
hospitality
identity
logistics
mobility
notification
organization
product
project
quantity
queue-management
reservation
restaurant
scheduling
security
supply
support
survey
talent
tenancy
transport
vehicle-service
workforce
workspace
```

The framework parent is located at `core/framework/pom.xml`. The repository root is not a single Maven reactor. The inspected `core/framework/pom.xml` also includes the developer-platform reactor through a relative module path. Build and CI coverage must be evaluated per actual reactor entry point; a successful build of one root cannot be generalized to the entire repository.

## 3. Existing capability landscape (initial grouping)

This is a navigation inventory, not a final bounded-context decision.

| Platform / business area | Existing repository roots or notable areas |
|---|---|
| Platform foundation | `core`, `identity`, `tenancy`, `security`, `organization`, `application-platform` |
| Customer and engagement | `crm`, `support`, `notification`, `social-graph` (under core framework) |
| Commerce and channels | `commerce`, `product`, `reservation`, `events`, `restaurant`, `hospitality` |
| Finance | `finance`, `budget` |
| Supply and fulfillment | `supply`, `logistics`, `transport`, `automotive-parts`, `automotive-parts-purchasing` |
| Workforce and industry | `workforce`, `talent`, `construction`, `education`, `vehicle-service`, `corporate` |
| Workspace and delivery | `workspace`, `project`, `quantity`, `scheduling`, `queue-management` |
| Developer ecosystem | `developer-platform`, `core/framework/plugin`, `core/framework/integration` |
| Shared technical capabilities | `common`, `document`, `documents`, `asset`, `geo`, `budget` |

The grouping is intentionally provisional. For example, the existence of both `document` and `documents` roots is a signal to inspect their contracts and consumers, not sufficient proof that one should be deleted. Similar caution applies to product/catalog concepts, subscription concepts, project/scope/planning structures, and identity/party/customer models.

## 4. Architecture observations confirmed from repository descriptors

1. **Multiple reactor roots are intentional.** ADR-003 explicitly documents this layout and the framework parent location.
2. **Commerce is already decomposed.** Its aggregator includes offering, configuration, pricing, promotion, affiliate, subscription, POS, kiosk, omnichannel and marketplace.
3. **Finance is already decomposed.** Its aggregator includes accounting, billing, common finance, payment, subscription, Syirkah, tax and wallet artifacts.
4. **CRM is a separate bounded-context reactor** with domain, application and adapter artifacts.
5. **Identity, tenancy and security are separate reactors.** Their separation is consistent with the platform's target ownership, but actual call paths, authorization enforcement and tenant propagation still need source-level verification.
6. **The application platform already exists.** Its M1–M7 documents and implementation are a separate initiative; this broader Business Operating Platform M01 must reuse it.
7. **The root CI has multiple targeted workflows.** The tree contains dedicated workflows for application platform, CRM/PostgreSQL, developer platform, events, mobility, notification, payment, social graph, transport and workspace, in addition to the main CI workflow. Workflow presence is not proof that every reactor is covered or currently green.

## 5. Known overlap candidates to verify (not migration decisions)

The existing architecture audit already records concrete candidates:

- Document lifecycle and binary object storage have parallel local/S3-compatible implementations across `document` and `documents`.
- Finance and Commerce each contain subscription-related models with potentially overlapping lifecycle ownership.
- Finance common and accounting currency capabilities contain exchange-rate concepts whose master-data and valuation responsibilities need to be made explicit.
- Scope and Planning both define work-breakdown hierarchy/node concepts.
- The Construction aggregator spans a wide range of responsibilities and needs consumer/dependency tracing before any extraction.

These are audit leads, not authorization to rename, move, or delete modules. Any consolidation requires consumer/API/schema/migration evidence and compatibility adapters where needed.

## 6. What this inventory does not prove

This pass does **not** establish:

- the complete Maven dependency graph or all transitive runtime dependencies;
- which modules are actually deployed together or independently;
- whether every reactor builds successfully on the current baseline;
- the authoritative owner of every duplicated-looking class or table;
- complete API, event-schema, migration, or database ownership;
- tenant isolation and authorization correctness across every entry point;
- whether each capability can be consumed without pulling unrelated infrastructure;
- support for standalone, modular-monolith, distributed, cloud or on-premises profiles.

Do not use POM names, directory names, README claims, or module suffixes as substitutes for source-level evidence.

## 7. Next M01 audit batches

| Batch | Work | Required evidence |
|---|---|---|
| M01-B | Domain ownership and duplicate-model tracing | Source declarations, invariants, repositories, migrations, consumers, compatibility |
| M01-C | Dependency and coupling graph | POM dependency edges, source imports, forbidden reverse dependencies, cycles |
| M01-D | Application composition | Real host/runtime wiring for CRM, commerce, retail/POS, workspace and selected verticals |
| M01-E | Identity, tenant and authorization trace | Context propagation, policy enforcement points, negative cross-tenant tests |
| M01-F | Persistence and event ownership | Migration ownership, transaction boundaries, outbox/inbox, schema and event versions |
| M01-G | Deployment and packaging profiles | Runtime entry points, optional adapters, external dependencies, deployment manifests |
| M01-H | Architecture baseline and prioritized remediation | Evidence-linked capability map, risk register, milestone backlog and acceptance criteria |

## 8. Acceptance criteria for M01

M01 is complete only when the inventory is joined to source-level evidence and can answer, for each selected high-value capability:

- Who owns the invariants and authoritative write model?
- Which public contracts are supported and versioned?
- Which capabilities and adapters may depend on it?
- Which runtime compositions actually consume it?
- How are tenant, actor, authorization and idempotency context enforced?
- Which schema, migrations, events and outbox are owned by it?
- Which deployment profiles are demonstrated by tests and CI?
- What overlaps are proven, and what migration is safe without breaking consumers?

**Decision:** No new generic CRM, commerce, identity, tenancy, workflow or workspace foundation is introduced by M01-A. Continue with evidence-led audit and consolidate only after ownership and compatibility are demonstrated.
