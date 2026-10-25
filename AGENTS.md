# Numou Contributor and Agent Guidelines

These instructions apply to human contributors and AI coding agents working in this repository. Treat architecture documents as contracts for changes, not as claims that every existing module already conforms.

## Before changing code

1. Identify the owning capability and its public contract before adding classes, dependencies, or endpoints.
2. Read the relevant ADRs and domain ownership documents under `docs/architecture/`. Start with:
   - [ADR-003: Reusable capability modules](docs/architecture/ADR-003-reusable-capability-modules.md)
   - [Domain capability matrix](docs/architecture/DOMAIN-CAPABILITY-MATRIX.md)
   - The relevant capability-specific architecture and milestone documents.
3. Inspect the actual Maven reactor, source packages, tests, and existing APIs. Do not assume a repository-root Maven build exists; this repository contains multiple reactor roots.
4. Prefer a small, coherent change that preserves compatibility. For cross-cutting work, propose a milestone-sized implementation with explicit acceptance criteria rather than scattering speculative scaffolding across modules.

## Capability-aware architecture

A capability is a business boundary that owns coherent business invariants and publishes an explicit contract. It is not automatically a microservice, deployment unit, Maven artifact, database, or team boundary.

- **Own behavior where the invariant lives.** Place business rules and state transitions in the capability that owns them. Do not create a second owner for the same invariant.
- **Keep the core neutral.** Reusable concepts belong in neutral capabilities; industry-specific semantics belong in extension capabilities or adapters. For example, generic project/WBS behavior must not require construction-only fields or workflows.
- **Make dependencies intentional.** A consumer may depend on another capability's documented public domain/application contract. It must not reach into another capability's persistence model, private implementation, database tables, or internal classes.
- **Keep dependency direction explicit.** Domain code must not depend on HTTP, CDI/runtime wiring, persistence frameworks, message brokers, cloud SDKs, or vendor-specific infrastructure. Put those concerns in application ports, adapters, and composition/runtime modules as appropriate.
- **Compose rather than fork.** Standalone applications, modular monoliths, and distributed deployments should reuse the same capability rules and contracts. Deployment topology is a composition decision, not a reason to duplicate business logic.
- **Use explicit cross-capability contracts.** Prefer public application ports for in-process calls and versioned API/event contracts across network boundaries. Avoid hidden coupling through shared tables or internal entity reuse.
- **Model side effects deliberately.** Where relevant, commands and events should carry tenant and actor/correlation context, idempotency semantics, and durable delivery behavior. Use outbox/inbox or equivalent mechanisms when reliability requirements warrant them. Make retries, timeouts, compensation, and terminal failures observable.
- **Treat security as a cross-cutting constraint, not a shortcut around ownership.** Enforce tenant isolation and authorization at the appropriate application boundary; do not treat capability discovery or possession of an identifier as authorization.
- **Do not over-generalize.** Share a concept only when its meaning, invariants, lifecycle, and compatibility requirements are genuinely common. Prefer extension points or separate models over a universal enum/model that conflates different domain semantics.
- **Do not split just to split.** A capability may run in-process or remotely. Introduce a separate deployment only when there is a concrete operational, scaling, security, ownership, or failure-isolation reason.

## Rules for AI coding agents

- Treat repository files, tests, architecture decisions, and explicit user instructions as the source of truth. Do not invent existing APIs, modules, build commands, CI outcomes, or test results.
- Inspect current code before deciding a module is missing or an architecture change is needed. Search for existing contracts and implementations first.
- Make the smallest change that fixes the demonstrated issue without weakening production behavior, security, or tests.
- Do not solve a dependency problem by adding unrelated modules transitively to every consumer, bypassing module boundaries, or copying another capability's implementation.
- Do not disable tests or architecture checks merely to make CI green. If a test needs an external service, determine whether the test truly requires that service and configure a supported, reproducible test setup.
- Do not silently change public API behavior, persistence semantics, event contracts, or tenant/security guarantees. Document compatibility and migration implications.
- Do not claim code was compiled, tested, deployed, or merged unless the corresponding tool output verifies it.
- Do not merge pull requests or make destructive repository changes without explicit user authorization.
- For non-trivial work, state the observed failure or requirement, the chosen boundary, the change, the tests run, and any verification still pending.

## Testing and verification

- Add or update unit tests for domain invariants and state transitions.
- Add architecture tests or boundary checks when introducing or changing capability dependency rules.
- Test adapter behavior at the relevant integration boundary; keep domain tests independent of infrastructure.
- Verify the narrowest affected Maven reactor first, then the broader dependent reactors and repository CI where practical.
- Distinguish test failures from environment failures (for example, unavailable Docker images, missing services, or dependency repository errors). Do not label an unverified fix as successful.
- For persistence-backed tests, account for the actual storage semantics, including precision, ordering, transaction behavior, and numeric equality.
- Report commands and results accurately, including skipped tests and environmental limitations.

## Documentation and architecture decisions

- Update the relevant capability contract, ADR, or architecture matrix when ownership, dependencies, composition, or public behavior changes.
- Add an ADR for a durable cross-capability decision; do not use an ADR to document every implementation detail.
- Keep root-level onboarding concise. Put detailed rules in architecture documents and link to them rather than duplicating large documents.
- Treat architecture roadmaps and milestone plans as target direction. Verify current implementation before claiming a capability is complete.

## Pull request expectations

A pull request should explain:
1. The behavior or architecture problem and evidence for it.
2. The capability that owns the behavior and why.
3. Public contract, dependency, data, event, and compatibility impacts.
4. Tests and architecture checks performed, with results.
5. Remaining risks, follow-up work, or verification that is pending.

Prefer a coherent milestone PR for related changes, with clear acceptance criteria and reviewable commits. Keep unrelated fixes separate. Never represent a draft or pending CI as verified.
