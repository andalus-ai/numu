# M2 — Application Descriptor and Capability Compatibility Contract

- **Status:** Draft contract baseline; schema and example are normative for the v1alpha1 shape, while runtime validation is not yet implemented.
- **Repository:** `andalus-ai/numu`
- **Depends on:** M1 [Application Platform Foundation](NUMOU-APPLICATION-PLATFORM-M1-FOUNDATION.md)
- **Workflow engine:** [andalus-ai/gamelan](https://github.com/andalus-ai/gamelan)
- **Schema:** [application-v1alpha1.schema.json](schemas/application-v1alpha1.schema.json)
- **Example:** [retail-application-v1alpha1.json](examples/retail-application-v1alpha1.json)

## 1. M2 outcome

M2 defines a strict, declarative application manifest and the rules a future host-side compatibility validator must enforce.

It deliberately does **not** implement an application installer, execute package code, create a marketplace, introduce a second workflow engine, or add a new Maven module. The source audit found a significant runtime/build compatibility issue with Gamelan; that issue must shape the integration boundary before dependency wiring is attempted.

## 2. Verified repository findings

### Numou

- `core/framework/pom.xml` declares Java 21 (`maven.compiler.release=21`).
- `core/framework/workflow/pom.xml` depends on Numou foundation and Mutiny, but has no Gamelan artifact dependency.
- `NumouWorkflowEngine` is a small local contract exposing definition registration, start, resume and execution lookup. It has no tenant argument or explicit durable run identity contract.
- `GamelanWorkflowEngineAdapter` currently contains its own `ConcurrentHashMap` definition/execution/runtime stores and its own in-memory DAG scheduling. It does not call Gamelan APIs or repositories despite its name.
- The adapter's JavaDoc says the local scheduler exists so the framework can build without requiring Gamelan artifacts from an authenticated package registry.
- The current implementation therefore demonstrates basic DAG/approval behavior, not production Gamelan integration or durable workflow execution.

### Gamelan

- The repository is `andalus-ai/gamelan`, default branch `main`.
- Its Maven parent version is `0.1.1`; the parent declares `maven.compiler.release=25`.
- Gamelan documents Gradle profile tasks as its primary build strategy and also maintains Maven POMs / a Maven publication profile. Do not assume a Maven artifact is already published or resolvable in every Numou build environment.
- The engine SPI exposes tenant-aware `WorkflowDefinitionService`, `WorkflowRunManager`, `WorkflowDefinitionRepository` and `WorkflowRunRepository` contracts.
- The workflow domain includes tenant-bound definitions/runs, run snapshots, execution history, state transitions, signals and compensation concepts.
- The SDK client core exposes transport-agnostic workflow definition/run clients and REST, gRPC and local client modes.
- Gamelan's `RequestContext` carries request ID, optional `TenantId` and user ID; its `SecurityContext` exposes subject, tenant, roles and scopes. Runtime code also includes tenant-aware HTTP/gRPC security components.
- Gamelan includes persistence/recovery components and workflow wakeup outbox/dead-letter contracts. These are engine-owned concerns and should not be recreated in Numou's application platform.

### Compatibility conclusion

Numou's framework targets Java 21 while the current Gamelan Maven parent targets Java 25. A Java 21 Numou runtime cannot safely load Java 25-compiled Gamelan classes. Directly adding `gamelan-engine-spi`, `gamelan-sdk-client-core` or the runtime artifacts to the current Numou framework would therefore be an invalid assumption.

**M2 decision:** treat Gamelan as a separately deployed workflow runtime behind a versioned Numou workflow integration contract initially. A future in-process adapter is allowed only after the Java baseline and artifact publication/compatibility policy are explicitly aligned. Do not raise Numou's Java baseline as a side effect of this integration.

## 3. Manifest v1alpha1

The schema defines:

- `apiVersion` and `kind` as fixed protocol identifiers.
- `metadata.id` as a stable reverse-DNS-style application identifier.
- `metadata.version` as a SemVer 2.0.0 package version.
- `metadata.publisher` as a stable publisher namespace.
- Required and optional capability requirements, each with an explicit contract range.
- A declared permission request list.
- An optional versioned configuration schema reference.
- Declarative navigation contributions, each tied to a required permission.

Unknown properties are rejected at every object level. This makes accidental misspellings and unreviewed contract expansion visible rather than silently accepted.

### Contract range semantics

The v1alpha1 validator must support only:

- Exact version: `1.4.2`
- Compatible major range: `^1.4.2`
- Compatible minor range: `~1.4.2`

Use SemVer 2.0.0 precedence. A caret range follows standard SemVer compatibility semantics, including the special behavior for zero-major versions. A tilde range permits patch updates within the specified minor line. Prerelease versions are not selected by a range unless the range explicitly names a prerelease. Build metadata does not affect precedence.

Do not add wildcard, OR, hyphen, or arbitrary comparator expressions to v1alpha1. If richer ranges become necessary, introduce a new contract version with deterministic test vectors rather than silently changing this grammar.

### Cross-field validation beyond JSON Schema

JSON Schema validates shape, but the host validator must additionally enforce:

1. Application ID uniqueness within the package catalog and immutable publisher ownership.
2. No duplicate capability IDs across required and optional dependency lists.
3. No capability can be both required and optional.
4. No duplicate navigation contribution IDs within an application.
5. All requested permission strings must be known to the host policy catalog; unknown permissions fail closed.
6. A navigation contribution's required permission must also be declared in the package's permission list.
7. Every required capability must resolve to exactly one compatible provider in the selected host runtime profile.
8. Optional capabilities may be absent; if present, they must satisfy the declared range.
9. Dependency cycles are rejected with a deterministic cycle path.
10. The resolved dependency graph is deterministic regardless of manifest/property iteration order.
11. Prerelease and build metadata comparisons follow the pinned SemVer implementation and test vectors.
12. A manifest cannot grant permissions, choose a tenant, supply a secret, select arbitrary database connections, or execute code.

The JSON Schema `default` keyword is annotation only. The validator must not mutate a manifest or silently inject defaults.

## 4. Capability identity and resolution

A capability requirement is a contract dependency, not a Maven artifact coordinate and not a deployment instruction.

The resolver must distinguish:

- **Capability ID:** stable business/platform contract identity.
- **Contract version:** compatibility version exposed by the capability.
- **Artifact coordinate:** optional implementation packaging detail.
- **Runtime provider:** the actual host-supplied implementation.
- **Deployment location:** embedded, same-process, or remote.

A single capability may have multiple artifact and deployment forms. A package may not pin a Maven artifact, database, host, service URL or topology.

For v1alpha1, the example uses `numou.workflow.orchestration` as a **proposed capability ID**, not as a claim that a repository-wide capability registry has already published that ID. M3 must reconcile it with the existing workflow module contract and assign a stable owner before implementation.

## 5. Gamelan workflow integration boundary

### Ownership

| Responsibility | Owner |
|---|---|
| Business process intent, business trigger mapping and business-specific workflow templates | Numou business capability/application |
| Workflow definitions, validation/compilation, workflow runs, node execution, scheduling, signals, recovery, history and compensation | Gamelan |
| Tenant authentication, actor authorization and entitlement decision for Numou business actions | Numou host/security capabilities |
| Workflow-engine tenant binding and workflow-level authorization | Gamelan runtime, using trusted propagated context |
| Cross-system correlation, durable command intent and business-side event publication | Numou integration/outbox boundary |
| Workflow execution events and engine persistence/recovery | Gamelan |

Numou should not copy Gamelan's workflow aggregate, run repository, event store, scheduler, executor registry or recovery sweeper.

### Initial topology

`Numou business application → Numou workflow port → remote Gamelan adapter → Gamelan REST/gRPC runtime`

The adapter is a transport/integration boundary, not a second workflow engine. The package manifest requires a Numou workflow capability contract; the selected host runtime profile supplies that capability through the Gamelan adapter.

The application manifest must not contain Gamelan endpoint URLs, API keys, credentials, tenant IDs or implementation-specific Maven coordinates. Runtime endpoints and credentials are host configuration; secrets are resolved through the existing Numou Vault boundary.

### Context and security propagation

- Derive tenant identity from authenticated Numou request/service context. Never trust a tenant ID supplied by an application manifest or browser body.
- Map the canonical Numou tenant identifier to Gamelan's `TenantId` at the adapter boundary.
- Propagate a stable request/correlation ID and authenticated actor/service subject.
- Use an explicit service-to-service credential and least-privilege scopes for the adapter.
- Do not forward an end-user bearer token to Gamelan by default. Define delegated actor context only through an explicit, verified trust contract.
- Re-authorize each business action in Numou even when a workflow run was initiated by an authorized actor.
- Verify tenant ownership on every definition/run lookup, resume, signal and cancellation operation.
- Treat workflow input/output payloads as untrusted data; enforce size, schema, redaction and secret-handling policy.
- Never log API keys, bearer tokens, raw secret values or unrestricted sensitive workflow payloads.

### Delivery semantics and idempotency gap

Gamelan's `CreateRunRequest` includes workflow ID/version, inputs, correlation ID and auto-start. The inspected client interface also has signal overloads with an idempotency key, while the run manager documents deterministic/idempotent external interactions.

However, the inspected create-run request does not expose a dedicated client idempotency key. **A correlation ID must not be assumed to provide create-run deduplication.**

Before enabling business-triggered automatic starts, M3 must specify and test:

1. Stable command identity derived from the Numou business event/command.
2. Durable Numou-side dispatch intent and retry policy.
3. Gamelan-side deduplication or a safe lookup/reconciliation operation for a retried create request.
4. Behavior after timeout when Gamelan may have accepted the request but Numou did not receive the response.
5. Exactly-once business effect is not claimed; use at-least-once delivery with idempotent effects and explicit reconciliation.
6. Signal/resume retries preserve a stable idempotency key and cannot approve/complete a human task twice.
7. Workflow completion events are consumed idempotently and correlated to the originating business aggregate.

Do not introduce a second workflow persistence store in Numou to mask missing engine-side deduplication.

### Java and packaging compatibility

- Keep the Numou domain/application workflow port free of Gamelan classes.
- Do not add Gamelan 0.1.1 Maven artifacts to the Java 21 core reactor.
- The initial adapter may use a protocol implementation that is independently compatible with Java 21, but only after the concrete REST/gRPC wire contract, authentication and generated-client compatibility are verified.
- An embedded/local Gamelan mode is a future option only if Gamelan publishes a Java 21-compatible SPI/client artifact or Numou deliberately adopts a coordinated Java 25 baseline through a separate platform-wide decision.
- Pin the Gamelan server/client protocol contract independently of the Gamelan implementation release.

## 6. M2 conformance fixtures and expected validator behavior

| Fixture / scenario | Expected result |
|---|---|
| Retail manifest example | Schema-valid |
| Unknown root or nested property | Reject |
| Missing publisher or package version | Reject |
| Invalid SemVer package version | Reject |
| Capability range outside exact/caret/tilde grammar | Reject |
| Duplicate required capability ID | Reject in semantic validation |
| Same capability listed as required and optional | Reject in semantic validation |
| Missing required provider | Reject resolution |
| Missing optional provider | Accept with optional capability unavailable |
| Provider outside declared contract range | Reject resolution |
| Dependency cycle | Reject resolution and report stable cycle path |
| Unknown permission | Reject closed |
| Navigation permission not declared by package | Reject semantic validation |
| Tenant ID, credential or secret field in manifest | Reject as unknown property |
| Package attempts to grant itself a permission | Reject; package permissions are requests only |

The positive retail example is stored alongside the schema. Runtime tests must use the same fixtures; a future validator must not reimplement a looser grammar.

## 7. Security, trust and package lifecycle constraints

- Schema validity does not prove publisher authenticity.
- Package signature and digest verification belong to a distribution envelope/registry trust layer, not to a self-asserted manifest field.
- Publisher identity must be bound to a trusted signing key and revocation policy before installation.
- Manifest permissions are requests; tenant/actor grants remain controlled by Numou authorization policy.
- Configuration schema references must resolve only through an allowlisted package content root or trusted registry. No arbitrary network fetch during validation.
- Configuration values are tenant-scoped and versioned. Secret values remain in Vault and exports carry only references or redacted placeholders.
- Package installation must be disabled until signature, dependency, entitlement and authorization gates are implemented.

## 8. M2 acceptance

- [x] Verify Numou Java baseline and current workflow module dependencies.
- [x] Inspect Gamelan Maven/Gradle build strategy and Java baseline.
- [x] Inspect Gamelan workflow SPI, tenant/security context, client and runtime contracts.
- [x] Identify that Numou's current class named `GamelanWorkflowEngineAdapter` is an in-memory implementation, not a Gamelan adapter.
- [x] Define application manifest v1alpha1 schema and positive example.
- [x] Define dependency range semantics and semantic validation requirements.
- [x] Define Gamelan ownership, remote-first topology, context propagation and idempotency gaps.
- [ ] Implement and test a production manifest parser/compatibility resolver in an owner-selected module.
- [ ] Verify the concrete Gamelan REST/gRPC wire contract and service-to-service authentication.
- [ ] Add end-to-end tenant-isolation and create-run retry/reconciliation tests.
- [ ] Resolve Java 21/25 policy before any in-process Gamelan dependency.

M2 establishes a versioned contract and source-backed integration decision. It does not claim a production parser, resolver or Gamelan adapter is delivered.

## 9. Next milestone

**M3 — Application Descriptor Validator and Gamelan Workflow Port Contract**

1. Confirm the stable capability ID and owning module for workflow orchestration.
2. Implement schema loading and strict manifest validation with pinned JSON Schema and SemVer libraries compatible with Java 21.
3. Add deterministic semantic validation and dependency graph resolution.
4. Add fixture-driven unit tests for all positive/negative cases in this document.
5. Refactor the misleading in-memory `GamelanWorkflowEngineAdapter` naming only after checking every consumer and preserving a clearly named test/local implementation.
6. Define a Gamelan remote adapter against a verified wire contract; do not compile against Java 25 SDK artifacts from the Java 21 reactor.
7. Add tenant/actor/correlation propagation and explicit create-run idempotency/reconciliation tests before connecting business events to workflow starts.
8. Run the exact core framework reactor and relevant CI workflows; report any unrelated failures separately.

