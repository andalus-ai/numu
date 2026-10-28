# Notification Capability — Repository Discovery (N0)

- **Status:** Source-layout audit; no notification implementation is claimed by this document
- **Repository:** `andalus-ai/numu`
- **Related roadmap:** [Notification Capability Roadmap](NOTIFICATION-CAPABILITY-ROADMAP.md)
- **Architecture contract:** [ADR-003](ADR-003-reusable-capability-modules.md), [Capability Packaging and Composition Contract](CAPABILITY-PACKAGING-AND-COMPOSITION.md), and [Domain Capability Matrix](DOMAIN-CAPABILITY-MATRIX.md)

## 1. Confirmed repository facts

The following findings were checked against files on the repository's `main` branch.

| Concern | Confirmed source evidence | Consequence for Notification |
|---|---|---|
| Maven composition | `core/framework/pom.xml` is the `numou-framework` parent. Top-level capability aggregators such as `workspace/pom.xml` and `transport/pom.xml` resolve it using `../core/framework/pom.xml`. | Add a dedicated top-level `notification/pom.xml` reactor only after reviewing module conventions; do not add a root Maven build or assume a root `pom.xml`. |
| Domain/application separation | Workspace has separate `workspace-domain` and `workspace-application` artifacts. Its domain POM has no application/runtime dependency; its application POM depends on domain. Transport follows a similar domain/SPI/application separation. | Keep notification domain pure Java and make application contracts depend inward. |
| Persistence adapter precedent | Workspace has a distinct `workspace-persistence-jdbc` module, with JDBC/PostgreSQL-related test dependencies declared at that boundary. Transport has a distinct `transport-postgres-adapter`. | Put database implementation and migrations in a notification persistence adapter, not in domain/application. Confirm the actual migration convention before adding SQL. |
| HTTP adapter precedent | Workspace has an optional `workspace-rest-adapter` that depends on the application artifact and owns Quarkus REST dependencies. | Any inbox API should be an optional adapter; it must not make REST/Quarkus a dependency of notification domain/application. |
| Event contracts | `core/framework/event/pom.xml` declares `event-domain`, `event-application`, `event-spi`, and `event-adapter`. The event aggregator documents transport-neutral envelopes, correlation/causation, tenancy, versioning, publisher/subscriber ports, and a durable event-store concept. | Reuse the existing event capability through its public contracts if event-driven dispatch is selected. Do not create a second generic event bus. Actual outbox adapter behavior still needs source-level verification before relying on it for notification dispatch. |
| Reliability contracts | `core/framework/reliability/pom.xml` declares API, SPI, application, and adapter modules; its description explicitly covers idempotency, retries, outbox, inbox, and failure classification. | Inspect the actual reliability public APIs before deciding whether notification needs its own delivery job/outbox table or can reuse an existing contract. Do not duplicate reliability mechanisms based only on aggregator descriptions. |
| Identity | `identity/pom.xml` declares domain, application, and adapter artifacts. Its application POM uses the foundation application contract. | Notification must integrate through a verified public identity/tenant/authorization contract. The existence of the Identity capability alone does not prove which tenant-membership or recipient-authorization API is suitable. |
| Workspace inbox surface | Workspace has domain/application, JDBC persistence, and REST artifacts, but its aggregator describes workspace/knowledge management, not notification delivery. | Do not store notifications as workspace pages or make Workspace a required dependency. An optional UI may consume Notification's own application contract. |
| Existing notification capability | The current repository's notification roadmap exists, but searches performed for notification implementations and a top-level `messaging/pom.xml` did not surface a current implementation. This is a bounded search result, not proof that no notification-related code exists anywhere. | Continue targeted source inspection before coding; do not assume provider, template, inbox, or delivery APIs already exist. |

## 2. Recommended initial packaging

Use the established top-level capability pattern and keep the first implementation deliberately narrow:

- `notification/pom.xml` — reactor aggregator with parent `../core/framework/pom.xml`.
- `notification/notification-domain` — notification/inbox concepts and lifecycle invariants; no framework or persistence dependencies.
- `notification/notification-application` — commands/queries and ports for recipient authorization, persistence, clock/ID generation, and event publication where justified.
- `notification/notification-persistence-jdbc` — tenant-scoped durable inbox records and migration.
- `notification/notification-rest-adapter` — optional REST adapter following the Workspace adapter convention.

This is a proposed layout based on observed conventions, not a statement that these artifacts currently exist. Do not introduce provider SDKs, email/SMS/WhatsApp dependencies, a broker, a background worker, or a separate microservice in the first vertical slice.

## 3. N1 contract and implementation guardrails

The first slice should prove the user-facing inbox lifecycle before introducing multi-channel delivery.

1. **Create notification:** accept a stable notification type, tenant/recipient references, safe title/body or a validated payload, optional source/correlation reference, and an idempotency key. Caller-provided tenant/recipient IDs are data, not authorization proof.
2. **Read inbox:** list notifications only for the authenticated recipient and active tenant, with bounded cursor/limit pagination and deterministic ordering.
3. **Read state:** mark a notification read idempotently and only for its authorized recipient. A repeated read request must not create duplicate records or errors for an already-read item.
4. **Tenant isolation:** repository reads and writes, uniqueness constraints, and mutation predicates include tenant scope. A notification ID must not allow cross-tenant or cross-recipient access.
5. **Durability:** notification creation and idempotency record must commit atomically in the persistence adapter. External delivery is out of scope for N1.
6. **Public boundary:** application contracts expose DTOs/commands/queries, not JDBC entities or REST types. The persistence adapter implements application-owned ports.
7. **Audit and privacy:** retain source/correlation references and timestamps as appropriate; minimize message content, avoid secrets, and define retention/masking before exposing operational diagnostics.
8. **Tests:** domain lifecycle tests; application tests for authorization and duplicate requests; PostgreSQL integration tests for tenant isolation, uniqueness, transaction rollback, and ordering; REST tests for authenticated access and error mapping; architecture checks for dependency direction.

## 4. Open questions to resolve by reading actual source

Before selecting APIs or adding dependencies, inspect the public contracts and implementations for:

- `numou-reliability-api` and its outbox/inbox/idempotency ports;
- event envelope and publisher contracts, including tenant/actor/correlation metadata;
- the supported authenticated tenant/actor context and resource authorization ports;
- Workspace JDBC migration naming, transaction, repository, and test conventions;
- REST adapter authentication, pagination, error mapping, and serialization conventions;
- the top-level build/CI workflows that register new capability reactors.

If any of these contracts are unsuitable, document the gap and use a narrow notification-owned port rather than reaching into private implementation classes or tables.

## 5. N0 exit criteria

- [x] Confirmed the multi-reactor Maven parent pattern.
- [x] Confirmed domain/application, persistence, and REST adapter precedents.
- [x] Confirmed event and reliability capability aggregators exist.
- [x] Identified that aggregator POM descriptions are not sufficient evidence of concrete runtime behavior.
- [ ] Inspect the concrete public APIs and implementations listed above.
- [ ] Confirm root CI/build registration requirements and a reproducible narrow reactor command.
- [ ] Begin N1 only after tenant/authorization and persistence conventions are source-verified.

## Verification

This is a documentation/source-layout milestone. No Java code was added, no Maven build or tests were run, and no delivery provider or production-ready notification service is claimed.
