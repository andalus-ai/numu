# Numou Runtime Platform — M0 Repository Audit and Boundary Decision

- **Status:** M0 targeted repository audit complete; implementation not started.
- **Repository:** `andalus-ai/numu`
- **Baseline:** `main`, inspected 2026-11-05.
- **Related:** `AGENTS.md`, ADR-003, Capability Packaging and Composition Contract, Application Platform M1–M7 documents.

## 1. Outcome

Numou needs a general-purpose application execution runtime in addition to its reusable business capabilities and the existing Application Platform.

Do not create a competing application registry, plugin registry, capability resolver, tenant registry, identity system, authorization system, or workflow engine.

The existing Application Platform and the proposed Runtime have different responsibilities:

- **Application Control Plane:** application package identity and catalog, manifest validation, capability compatibility, tenant installation state, entitlement/authorization integration, configuration lifecycle and provisioning coordination.
- **Application Runtime / Data Plane:** process bootstrap, selected capability composition, adapter wiring, API/transport hosting, worker lifecycle, health/readiness, runtime configuration, packaging and deployment profile execution.

The control plane requests and reconciles application lifecycle operations. A runtime host performs them through versioned contracts and reports observed state.

## 2. Verified repository evidence

### Architecture and repository rules

- `AGENTS.md` requires reading ADR-003 and the capability ownership documents before cross-cutting changes. It explicitly says a capability is a business boundary, not automatically a deployment unit, Maven artifact, database or team boundary.
- ADR-003 accepts standalone, modular-monolith, distributed, cloud and on-premises composition profiles while preserving deployment-neutral domain behavior.
- The repository has multiple Maven reactor roots and no root `pom.xml`. The shared framework parent is `core/framework/pom.xml`.
- The existing architecture audit explicitly limits its coverage: it is not a complete 369-POM dependency graph, source call graph, persistence inventory or deployment inventory.

### Existing application platform

- `core/framework/plugin` is already the owner of executable plugin lifecycle and capability registry/dependency resolution.
- M3 application manifest validation and capability resolution are implemented in the existing plugin reactor; application manifests are declarative and are not executable plugins.
- `application-platform` is a separate optional Maven reactor containing domain, application and PostgreSQL adapter artifacts.
- M4 models tenant-scoped installation lifecycle and host-owned authorization/entitlement checks.
- M5 provides PostgreSQL persistence, forced RLS, idempotency, append-only transition history and durable outbox.
- M6 defines host-owned idempotent provisioning behind a port and a managed worker. The documented delivery guarantee is at-least-once, not exactly-once.
- M7 documentation describes atomic terminal failure reconciliation, but its status is implementation-branch documentation and must not be treated as merged without checking the branch/PR and CI.

### Runtime and ecosystem constraints

- `core/framework/pom.xml` targets Java 21 and contains the framework plugin and workflow reactors.
- The M2 Gamelan audit records that current Gamelan Maven artifacts target Java 25. Directly loading them into the Java 21 Numou framework is not a safe assumption. The accepted integration direction is remote-first until compatibility and publication policy are explicitly resolved.
- Existing Gamelan integration documentation warns that Numou's class named `GamelanWorkflowEngineAdapter` is an in-memory DAG implementation, not a production Gamelan integration. Do not treat it as the runtime workflow engine.
- Existing worker lifecycle abstractions and observability/reliability capabilities should be inspected and reused; do not create parallel schedulers or worker frameworks.

## 3. Boundary decision

The target layering is:

```text
Product application / application package
        |
Application Control Plane
  - catalog / manifest / compatibility
  - tenant installation / entitlement
  - desired lifecycle / provisioning
        |
Versioned runtime host contract
        |
Application Runtime / Data Plane
  - host bootstrap and composition
  - capability provider selection
  - adapter and transport wiring
  - workers / events / health / shutdown
        |
Existing Numou capability contracts and adapters
        |
Selected infrastructure providers
```

The runtime must not own business invariants. It composes capability contracts and adapters. The control plane must not assume that every installation is a process or that every capability is independently deployed.

## 4. Application composition profiles

M0 confirms these target profiles, but does not claim they are implemented:

1. **Embedded:** a consumer directly composes selected contracts/adapters without requiring a hosted control plane.
2. **Standalone:** one independently packaged host starts a selected application composition.
3. **Modular monolith:** multiple capabilities execute in one process with explicit ownership and transaction boundaries.
4. **Distributed:** selected capabilities are independently deployed behind versioned APIs/events with tenant, actor, correlation and idempotency propagation.
5. **Managed SaaS / private cloud / on-premises:** the same application package is hosted under environment-specific security, configuration, secrets, storage and operational policies.

Application template and deployment profile must be orthogonal. For example, Accounting can be standalone, modular-monolith hosted or remotely consumed; ERP can be modular monolith or distributed. A profile is not permission to weaken consistency, authorization or tenant isolation.

## 5. Reuse decisions

| Concern | Existing owner to inspect/reuse | Runtime responsibility |
|---|---|---|
| Capability discovery and plugin lifecycle | `core/framework/plugin` | Select and compose compatible providers; do not create a second registry |
| Tenant identity | `tenancy` and canonical foundation `TenantId` | Propagate verified tenant context |
| Authentication / authorization | `identity`, `security` | Integrate host security; never infer authorization from an installed package |
| Workflow | Gamelan through a verified remote contract | Invoke through a stable port; do not build another workflow engine |
| Managed workers | Existing `ManagedWorker` lifecycle | Host and supervise selected workers |
| Events / reliability / observability | Existing framework reactors | Wire existing contracts and adapters |
| Persistence | Capability-owned persistence adapters | Select and configure adapters; do not create shared cross-capability tables |
| Secrets | Existing Security Vault | Resolve authorized secret references; never put secret material in manifests |
| Binary/document storage | Existing document/storage capabilities | Configure selected providers, including RustFS where appropriate |

## 6. M0 gaps and risks

1. There is no verified general-purpose host bootstrap and application composition implementation that can turn a declarative application package into a runnable standalone application.
2. Application package validation and tenant installation do not by themselves produce a deployable executable, container or service.
3. A host runtime contract is needed between control-plane provisioning and runtime composition, including idempotent ensure/upgrade/stop behavior and observed state.
4. Runtime packaging and deployment must not require the control plane at steady state for standalone deployments.
5. The full Maven dependency graph, all reactor roots, host composition roots, runtime adapters, container builds and deployment workflows have not been exhaustively audited in M0.
6. The Java 21 / Java 25 Gamelan compatibility boundary remains a constraint.
7. The current M6/M7 branch and PR state must be rechecked before integration; architecture documents alone are not proof of merge or CI status.

## 7. M1 recommendation — Runtime Host Foundation

M1 should be a focused, independently testable runtime-host milestone, not an attempt to implement every deployment profile.

Proposed M1 scope:

- Inspect and select the existing host composition/bootstrap owner before adding modules.
- Define a small versioned `ApplicationRuntimeDescriptor` / `RuntimeProfile` contract that consumes the already validated application manifest without duplicating its schema or validator.
- Define explicit host ports for capability provider resolution, adapter assembly, configuration, lifecycle, health and runtime state reporting.
- Implement a Java 21-compatible host bootstrap that composes a small allowlisted set of existing capability providers.
- Make startup and shutdown deterministic; fail startup on missing/incompatible required providers; keep optional providers optional.
- Add tests proving minimal composition does not pull unrelated business capabilities, that provider selection is deterministic, and that failed startup cleans up already-started components.
- Add a minimal executable proof using a deliberately small sample composition. Do not claim a production Accounting or ERP product yet.
- Keep tenant installation orchestration in `application-platform`; keep executable plugin lifecycle in `core/framework/plugin`.

### M1 acceptance criteria

- No duplicate capability registry, manifest validator, tenant model, authorization model, worker framework or workflow engine.
- Runtime can start and stop a deterministic, selected capability composition on Java 21.
- Required/optional provider semantics and compatibility failures are tested.
- No business capability acquires a dependency on the host runtime.
- Runtime can be packaged independently of the control plane.
- Tests and reactor commands are recorded accurately; no unverified build/CI claims.

## 8. Explicit non-goals for M0/M1

- No arbitrary code execution from application manifests.
- No marketplace or third-party executable extension loading.
- No universal metadata/business-object engine.
- No Kubernetes control plane, autoscaler or distributed service mesh.
- No automatic conversion of every capability into a microservice.
- No Java baseline increase to Java 25 to accommodate Gamelan.
- No migration of existing capability packages or broad Maven reactor restructuring.
- No claim that all existing capabilities are currently runtime-composable.

## 9. Audit limitation

This is a targeted architecture and source-entry audit based on the default branch and the cited architecture/manifest/application-platform contracts and Maven entry points. It is not a complete source-level call graph, 369-POM dependency graph, complete deployment inventory or exhaustive API compatibility audit. M1 must inspect the actual candidate bootstrap, provider implementations and tests before choosing a concrete module location or adding dependencies.
