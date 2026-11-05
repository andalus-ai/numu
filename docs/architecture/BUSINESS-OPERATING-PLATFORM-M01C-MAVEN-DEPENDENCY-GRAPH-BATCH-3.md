# Business Operating Platform — M01-C Maven Dependency Graph (Batch 3)

- **Status:** Representative direct-dependency subgraph captured; full repository graph and repository-wide cycle detection remain open.
- **Repository:** andalus-ai/numu
- **Branch:** docs/business-platform-m01c-maven-graph-batch3
- **Baseline:** main at commit 4d858ad1f4186a810a46cd0f598a7729aa711248 (2026-11-04).
- **Inventory:** 462 files named pom.xml in the recursive Git tree at this baseline.
- **Method:** Read the actual POM blobs for 15 representative modules and extracted only direct dependencies with groupId tech.kayys.numou. Kept Maven scope and optional flag. Did not count dependencyManagement entries, parent POM declarations, or transitive dependencies as direct module edges.

## 1. Scope and limitations

This is a **bounded representative subgraph**, not the complete 462-POM dependency graph. The Git tree confirms 462 POM files, but this inspection batch fetched and parsed 15 selected POMs. A full graph requires retrieving and parsing every POM, resolving parent/property/BOM versions and profiles, distinguishing reactor aggregator POMs from published modules, and validating the result with Maven effective-POM/dependency tooling in a checkout.

No repository-wide cycle-free claim is made. Cycle detection below is limited to the selected subgraph and the direct compile edges observed in those POMs.

## 2. Observed direct internal edges

All edges below are declared in dependencies in the inspected module POMs, not merely in dependency management.

| Source module | Direct internal dependency | Scope |
|---|---|---|
| CRM Domain | Foundation | compile |
| CRM Domain | Ecosystem Domain | compile |
| CRM Domain | Identity Domain | compile |
| CRM Domain | Foundation Testing | test |
| CRM Application | CRM Domain | compile |
| CRM Application | Foundation Application | compile |
| CRM Application | Ecosystem Application | compile |
| CRM Application | Foundation Testing | test |
| CRM Adapter | CRM Application | compile |
| CRM Adapter | Ecosystem Application | compile |
| CRM Adapter | Foundation Adapter | compile |
| Commerce Subscription Application | Foundation Application | compile |
| Commerce Subscription Application | Subscription Domain | compile |
| Commerce Subscription Application | Subscription SPI | compile |
| Commerce Subscription Application | Offering SPI | compile |
| Commerce Subscription Domain | Foundation | compile |
| Commerce Subscription Domain | Offering Domain | compile |
| Accounting Application | Accounting Domain | compile |
| Accounting Application | Accounting Payables | compile |
| Accounting Application | Accounting Receivables | compile |
| Accounting Application | Accounting Consolidation | compile |
| Accounting Application | Accounting Treasury | compile |
| Accounting Application | Document | compile |
| Accounting Application | Accounting Islamic | compile |
| Accounting Application | Accounting MDM | compile |
| Accounting Application | Accounting Multitenancy | compile |
| Accounting Application | Accounting Report | compile |
| Accounting Application | Accounting Workflow | compile |
| Accounting Application | Accounting Tax | compile |
| Accounting Application | Accounting Cost | compile |
| Accounting Application | Accounting Currency | compile |
| Accounting Application | Accounting Compliance | compile |
| Accounting Application | Foundation Application | compile |
| Document Application | Document Domain | compile |
| Document Application | Foundation Application | compile |
| Workspace Application | Workspace Domain | compile |
| Application Platform Application | Foundation Application | compile |
| Application Platform Application | Plugin | compile |
| Application Platform Application | Application Platform Domain | compile |
| Application Platform Domain | Foundation | compile |
| Developer Platform Application | Developer Platform Domain | compile |
| Identity Domain | Foundation | compile |
| Identity Domain | Foundation Testing | test |

The selected Documents Storage SPI POM declared no internal tech.kayys.numou dependency in the inspected direct dependency section. Foundation Domain also declared no internal dependency in that section.

## 3. Selected graph view

\`\`\`text
CRM Adapter
  ├──> CRM Application
  │      ├──> CRM Domain
  │      │      ├──> Foundation
  │      │      ├──> Ecosystem Domain
  │      │      └──> Identity Domain
  │      ├──> Foundation Application
  │      └──> Ecosystem Application
  ├──> Ecosystem Application
  └──> Foundation Adapter

Commerce Subscription Application
  ├──> Subscription Domain
  │      ├──> Foundation
  │      └──> Offering Domain
  ├──> Subscription SPI
  ├──> Offering SPI
  └──> Foundation Application

Accounting Application
  ├──> Accounting Domain and accounting capability modules
  ├──> Document
  └──> Foundation Application

Document Application
  ├──> Document Domain
  └──> Foundation Application

Workspace Application ──> Workspace Domain

Application Platform Application
  ├──> Application Platform Domain ──> Foundation
  ├──> Plugin
  └──> Foundation Application

Developer Platform Application ──> Developer Platform Domain
Identity Domain ──> Foundation
\`\`\`

Arrows represent direct declared dependency edges only; this diagram is not a runtime deployment graph.

## 4. Architecture observations

### Positive patterns

- Commerce Subscription Application depends on Offering SPI rather than an Offering adapter. This is a useful application-to-capability contract pattern.
- Workspace Application depends on Workspace Domain, a narrow and unsurprising inward dependency.
- Application Platform Application composes its domain and shared platform capabilities at the application layer.
- Foundation Testing is explicitly test-scoped in the inspected CRM Domain and Identity Domain POMs; it should not be mistaken for a production dependency.

### Boundary risks to carry forward

1. **CRM Domain → Identity Domain:** direct compile edge remains confirmed. The selected Account source uses Ecosystem ParticipantId; sampled Customer source does not use Identity types. Full CRM source import audit remains required.
2. **CRM Application → Ecosystem Application:** may be valid orchestration, but confirm whether CRM should depend on Ecosystem application use cases or instead a narrow Ecosystem SPI/port. Inspect actual imports and call paths.
3. **Accounting Application breadth:** 16 direct internal compile dependencies were observed. This may be a deliberate accounting composition/application facade, but its role should be explicit. Determine whether it is a reusable application layer or effectively a host composition root.
4. **Accounting Application → Document:** the direct compile edge coexists with an Accounting-local document/evidence service and storage SPI identified in Batch 2. Trace actual imports before calling the edge stale.
5. **Module-name collisions:** commerce Subscription and finance Subscription use distinct artifact IDs and bounded contexts. Do not merge them based on similar names; verify commercial lifecycle versus billing/receivable ownership.

## 5. Cycle detection result

A graph was constructed from the inspected direct internal edges. No directed cycle was found **within this selected subgraph**. This is not evidence that the repository-wide Maven graph is acyclic.

The full cycle check must:
- include every actual Maven project POM, excluding aggregator-only and BOM-only POMs as graph vertices unless they publish artifacts;
- resolve artifact coordinates, property substitutions, parent inheritance and active profiles;
- retain compile/runtime/provided/test scopes separately;
- report strongly connected components and the exact POM/dependency edge for each cycle;
- distinguish Maven reactor ordering constraints from architectural package/runtime dependencies.

## 6. Next steps

1. Run an automated parser against all 462 POMs in a repository checkout, emitting CSV/JSON edge inventory and Graphviz/Mermaid views.
2. Use Maven effective-POM output for representative reactors to validate inheritance, profiles and dependency management resolution.
3. Run strongly connected component detection on compile/runtime internal edges; separately report test/provided edges.
4. Review the CRM → Ecosystem Application and Accounting Application breadth with source-level import/call-path evidence.
5. Add architecture tests only after the allowed dependency rules and intentional composition roots are documented.

**Decision:** This batch establishes a reliable sampled edge set and confirms why dependencyManagement must not be mistaken for actual dependencies. Do not use it to authorize dependency deletion or to certify the repository as cycle-free. No source code, dependency declaration, API, event or schema was changed. No Maven build or CI run is claimed.
