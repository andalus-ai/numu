# Capability Packaging and Composition Contract

- **Status:** Accepted implementation contract
- **Scope:** Numou framework and all business capabilities
- **Related:** [ADR-003](ADR-003-reusable-capability-modules.md), [Capability module architecture audit](CAPABILITY-MODULE-ARCHITECTURE-AUDIT.md), [Domain capability matrix](DOMAIN-CAPABILITY-MATRIX.md)

## Purpose

A Numou capability must be reusable by multiple products without cloning its domain behavior. It may be consumed inside an application, composed into a modular monolith, or exposed as a separately deployed service. These are packaging and composition choices around one business capability, not separate implementations.

This document defines the target contract. It does not assert that every existing Maven module already conforms; current conformance must be established by audit and automated verification.

## 1. Terms that must not be conflated

- **Capability:** a business boundary that owns its invariants and lifecycle.
- **Maven artifact:** a build/distribution unit. One capability may publish several artifacts.
- **Application:** a product-specific composition of capabilities and user-facing workflows.
- **Runtime/deployment unit:** a process or service that can be started, scaled, upgraded, and operated.
- **Deployment topology:** how application/runtime units are placed and communicate.

A capability is not automatically a Maven artifact, application, database, or microservice. Splitting one capability into several artifacts can preserve one business owner; splitting it into services is a separate operational decision.

## 2. Artifact roles

Use these roles where they fit the existing capability and migration constraints; do not rename or split existing artifacts mechanically just to match suffixes.

| Role | Responsibility | Must not own |
| --- | --- | --- |
| Domain | Entities/value objects, domain policies, invariants, domain events | HTTP, CDI, persistence frameworks, brokers, cloud SDKs |
| Application | Use cases, transaction boundary coordination, ports, authorization checks | Vendor-specific adapters or host application presentation |
| Public contract / SPI | Stable consumer-facing interfaces and versioned integration DTOs/events | Private persistence entities or implementation details |
| Persistence adapter | Implements application/domain persistence ports and migrations | The authoritative business rules of another capability |
| Transport adapter | HTTP/gRPC, messaging, webhook, or partner protocol translation | A second copy of domain decisions |
| Runtime / composition | Bootstrapping, dependency injection, health, configuration, wiring | Business invariants that belong in the capability |
| Product application | UX, product-specific workflows, selected capability composition | Forked copies of reusable capability logic |

A capability may use fewer artifacts if that is appropriate. The separation is about dependency direction and optionality, not maximizing artifact count.

## 3. Dependency direction

The intended direction is:

```text
Product application / runtime
  -> capability application contract
       -> capability domain
       -> ports (owned by the capability)
  -> selected adapters implement those ports

Remote integration:
  consumer -> versioned API/event contract -> provider transport adapter
```

Rules:

1. Domain artifacts remain infrastructure-neutral and independently unit-testable.
2. Application code may depend on its own domain and stable public contracts of other capabilities.
3. A capability must not depend on another capability's private persistence model, tables, or implementation package.
4. Adapters depend inward on the contracts they implement; domain code must not depend outward on adapters.
5. Product applications select only the capabilities and adapters they require. Optional integrations must not become mandatory transitive dependencies of every consumer.
6. Cross-capability references should be stable identifiers or published contracts, not shared mutable entity instances.
7. A remote API and an in-process port must preserve equivalent business semantics, authorization, tenant context, idempotency, and error meaning. Transport changes must not silently change the domain contract.
8. Dependency rules must be introduced incrementally from observed repository evidence. Do not disable tests or add broad exceptions to force a pass.

## 4. Supported composition profiles

| Profile | Composition | When to use |
| --- | --- | --- |
| Embedded/library | Product directly uses selected capability artifacts and adapters | Small product, local integration, or reusable library use |
| Standalone application | One runtime boots selected capabilities and required adapters | Edge, internal, single-customer, or self-hosted application |
| Modular monolith | Multiple capabilities in one process with enforced module boundaries | Shared transactions and simpler operations are useful |
| Distributed services | Selected capabilities are exposed through versioned network/event contracts | Independent scaling, ownership, security, or failure isolation justifies the cost |
| Cloud / enterprise / on-premises | Any of the above plus environment-specific identity, storage, secrets, observability, deployment and governance adapters | SaaS, private cloud, regulated or customer-managed environments |

Do not require every capability to support every profile. The capability contract and evidence should state which modes are supported. A module becomes a service only when there is an operational reason to do so.

## 5. Example compositions

These examples are conceptual and must be mapped to actual artifacts after inspecting the repository. They do not authorize creating duplicate models.

- **E-commerce distribution:** Order + Inventory + Fulfillment + Shipment.
- **Fulfillment product:** Warehouse + Inventory + Fulfillment + Shipment.
- **Third-party logistics:** Shipment + Transportation + carrier adapters + Tracking.
- **Enterprise supply chain:** Planning + Procurement + Warehouse + Fulfillment + Transportation + Finance.

For instance, Shipment remains the owner of shipment lifecycle and parcel custody. Fulfillment owns the process of satisfying demand through allocation, picking, and packing. Transportation owns transport execution and capacity. An integration orchestrates these capabilities without taking over their invariants.

## 6. Persistence and transaction boundaries

- A capability owns its write model and schema migrations.
- Other capabilities must not mutate its tables directly.
- In a modular monolith, a shared database does not imply shared ownership or permission to bypass application contracts.
- Local transactions may cover multiple records owned by one capability. Cross-capability distributed workflows must define idempotency, retries, timeouts, compensation or manual resolution, and audit behavior.
- A distributed deployment must not claim a global ACID transaction where none exists.
- Read projections and reporting integrations must be explicitly owned, rebuildable where possible, and must not become an alternative source of truth.

## 7. Versioning and distribution

- Publish only APIs, SPIs, events, and configuration keys that consumers are intended to rely on.
- Treat internal classes, database tables, migration filenames, and package-private implementation as non-contractual.
- Version public HTTP/event schemas independently when necessary; document compatibility and deprecation windows.
- Keep Maven coordinates and module versions compatible with the repository's current release approach until a deliberate versioning migration is approved.
- Optional infrastructure drivers and transport adapters must be separately selectable.
- Provide dependency examples for consumers; do not require consumers to build the entire repository when published artifacts are available.

## 8. Required evidence for a capability to claim reuse

A capability's support matrix must identify:

- public domain/application contracts and their compatibility policy;
- supported artifact roles and optional adapters;
- persistence owner and migration boundary;
- in-process composition support;
- remote API/event support, if offered;
- tenant and actor context propagation;
- authorization and idempotency behavior;
- required runtime dependencies and external services;
- test evidence for supported profiles;
- operational and upgrade constraints.

At framework level, acceptance requires representative proof rather than a checkbox on every module:

1. At least one capability is consumed by two distinct host compositions without copying its domain logic.
2. The same application contract is exercised in-process and through a transport adapter.
3. A minimal composition excludes unrelated capability and infrastructure dependencies.
4. Architecture checks reject a representative forbidden dependency direction.
5. CI executes these checks from the repository root and reports their results.
6. Documentation names unsupported modes honestly; no profile is claimed solely because the architecture could theoretically allow it.

## 9. Incremental implementation sequence

1. Inventory actual artifact graphs, public contracts, runtime wiring, schemas, and consumers. Read `AGENTS.md` and relevant capability ADRs first.
2. Select one representative capability with multiple real consumers. Shipment is a strong candidate, but its existing contract and artifact graph must be audited before selection is finalized.
3. Build a minimal consumer composition and test it without unrelated modules.
4. Add a second composition using the same contracts and domain rules.
5. Add a transport adapter only where a real distributed use case requires it; test semantic parity and failure behavior.
6. Add enforceable dependency checks for the boundaries demonstrated by the sample.
7. Expand the pattern to other capabilities based on audit findings; migrate compatibility-sensitive boundaries gradually.

## 10. Definition of done

A milestone is complete only when code and tests demonstrate the promised packaging modes, the module dependency graph is reviewed, compatibility and migrations are documented, and the relevant CI checks pass. A written target architecture or a successful commit upload alone is not implementation evidence.
