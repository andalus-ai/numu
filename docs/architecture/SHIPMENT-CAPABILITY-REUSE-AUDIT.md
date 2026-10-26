# M38 — Shipment Capability Reuse Audit

- **Status:** Evidence-based assessment; extraction decisions remain provisional
- **Repository:** `andalus-ai/numu`
- **Scope:** Reuse of shipment lifecycle by e-commerce, fulfillment, logistics, and enterprise applications
- **Related:** [ADR-003](ADR-003-reusable-capability-modules.md), [Capability packaging contract](CAPABILITY-PACKAGING-AND-COMPOSITION.md), [Capability module architecture audit](CAPABILITY-MODULE-ARCHITECTURE-AUDIT.md)

## 1. Goal

Determine how Numou can make shipment lifecycle reusable without forcing every consumer to adopt the whole Courier/Cargo capability or creating a second shipment implementation.

This is an audit, not an assertion that the existing shipment model is already a standalone capability. No class moves or schema changes are proposed until repository-wide consumer, persistence, API, and migration tracing is complete.

## 2. Evidence inspected

The current `logistics/pom.xml` reactor is named `numou-fms` and aggregates:

- `numou-fleet`
- `numou-courier-domain`
- `numou-courier-application`
- `numou-courier-postgres-adapter`

The current `numou-courier-domain` Maven artifact declares dependencies on `numou-foundation` and `numou-organization-domain`. Its source package includes:

- Shipment lifecycle: `Shipment`, `ShipmentId`, `ShipmentAddress`, `ShipmentStatus`, `ShipmentRepository`
- Parcel and tracking: `Parcel`, `ParcelId`, `TrackingEvent`, `TrackingCursor`
- Pickup and delivery: `PickupRequest`, `DeliveryTask`, proof-of-delivery application use case
- Hub and dispatch operations: `Hub`, `HubTransfer`, `DispatchWave`, `TransportContainer`
- Courier commercial/partner concerns: `CourierRateCard`, `CodObligation`, `CourierPartnerAgreement`, `CourierNetworkPartner`
- Route planning and ETA: `CourierRoutePlan`, `CourierEtaEstimate`

The current `numou-courier-application` artifact depends on `numou-courier-domain` and `numou-organization-domain`. It contains booking/quoting shipment use cases and courier operational use cases.

The current `numou-courier-postgres-adapter` artifact brings Quarkus Arc, scheduler, PostgreSQL JDBC, Flyway, and Testcontainers dependencies. It contains JDBC shipment/tracking repositories and operational projections/workers. These infrastructure dependencies are correctly not listed in the domain POM, but the adapter is a logistics runtime adapter rather than a general-purpose shipment library.

These findings describe the inspected files, not a full transitive dependency or runtime-use analysis.

## 3. Initial assessment

**High confidence:** The current courier domain artifact is broader than shipment lifecycle alone. It contains a useful shipment aggregate, but also hub custody, line-haul/dispatch, courier pricing, COD, network partner, and route-planning concepts. A consumer that only needs shipment creation and status may have to take a much larger conceptual boundary than it needs.

**Medium confidence:** Shipment identity, parcel facts, status transitions, tracking history, and shipment-facing application contracts may form a reusable capability shared by multiple products. This needs validation against actual lifecycle invariants and current consumers before separation.

**Not yet established:** Whether shipment can be separated from parcel custody, pickup, proof of delivery, COD, and carrier orchestration without moving an invariant away from its current owner. Whether the current adapter is deployable without the larger Courier application also requires checking actual Quarkus wiring and configuration.

## 4. Candidate responsibility map

| Responsibility | Candidate owner | Boundary rule |
| --- | --- | --- |
| Shipment identity and lifecycle | Shipment capability | Owns accepted shipment state transitions and shipment-level invariants |
| Parcel/package facts | Shipment or parcel capability, after lifecycle review | Do not create a second parcel model in Fulfillment or Warehouse |
| Tracking history and status projection | Shipment capability with tracking contracts | Source events remain auditable; projections are not a second source of truth |
| Demand allocation, picking, packing, fulfillment orders | Fulfillment | References shipment via a stable contract/identifier |
| Stock balances, reservations, locations, movements | Inventory / Warehouse | Must not own courier shipment state |
| Hub intake, sorting, custody, dispatch waves | Courier operations extension | Uses shipment references and published shipment operations |
| Transport trips, fleet capacity, carrier tendering | Transportation / fleet / carrier capabilities as found in the repository | Does not fork shipment lifecycle rules |
| Tariffs, COD, receivables, settlement | Courier commercial policies + shared Finance contracts | Courier-specific charges are policies; financial postings remain Finance-owned |
| External carrier APIs and webhooks | Carrier transport adapters | Translate external states; do not mutate shipment internals directly |

This table is a target ownership hypothesis, not permission to create similarly named modules if equivalent owners already exist.

## 5. Proposed reuse shape

The preferred target is one source of truth with layered packaging. Exact artifact names are intentionally not prescribed before the Maven graph and current consumer set are fully traced.

1. **Shipment domain contract:** shipment identifiers, lifecycle invariants, and stable domain events; no HTTP, Quarkus, database, or carrier SDK requirement.
2. **Shipment application contract:** booking, lifecycle commands/queries, ports, authorization context, and idempotency semantics.
3. **Optional persistence adapters:** selected per host (for example, PostgreSQL) and owning only the storage implementation/migrations required by that adapter.
4. **Optional transport adapters:** REST, messaging, partner/carrier integration, enabled only when a product or deployment needs them.
5. **Courier extension:** hub custody, dispatch, COD policy, rate cards, network partners, and other logistics-specific workflows depend on shipment contracts rather than forcing shipment-only consumers to depend on the entire courier extension.
6. **Host application:** e-commerce, fulfillment, 3PL, or enterprise applications select the shipment artifacts and only the adapters they need.

A modular monolith may compose all these in one process. A distributed deployment may expose selected application contracts through a versioned API or event contract. Both must reuse the same shipment rules.

## 6. Required tracing before extraction

Before moving classes or changing schema, complete these tasks:

- Search all POMs and Java/Kotlin source for references to shipment, parcel, tracking, pickup, proof of delivery, and courier domain classes.
- Trace which consumers directly use domain entities versus application ports or external DTOs.
- Map database tables, migrations, indexes, outbox events, projection workers, and foreign keys to their authoritative owner.
- Identify cross-aggregate invariants that currently require a single transaction.
- Review tenant/organization context, actor authorization, idempotency keys, and event replay behavior.
- Inspect API contracts and any external integrations that rely on current status names or event schemas.
- Determine whether a neutral shipment package already exists in another module before introducing a new one.
- Define backward-compatible facades/adapters and migration/deprecation steps if artifact boundaries need to change.
- Select two real consumer compositions and show that each uses the same shipment domain/application implementation.

## 7. Acceptance criteria for the implementation milestone

The next implementation milestone should not be considered complete until:

1. A shipment-only consumer can depend on shipment contracts without requiring COD, fleet, hub dispatch, or Quarkus/PostgreSQL unless selected.
2. An e-commerce composition and a fulfillment/3PL composition use the same shipment lifecycle rules.
3. Courier operations use the shipment contract and cannot bypass shipment invariants through private entities or direct table writes.
4. In-process and remote adapters preserve authorization, tenant scope, idempotency, error semantics, and event meaning.
5. Existing database data and event contracts remain compatible or have a documented migration/deprecation plan.
6. Architecture tests reject newly introduced reverse dependencies from shipment core into courier-specific adapters.
7. The relevant Maven reactors and root CI pass; unexecuted tests are explicitly reported as unverified.

## 8. Decision for this milestone

Do not extract classes or create a second shipment module in this audit PR. The evidence shows a candidate for a cleaner reusable boundary, but consumer and persistence tracing must come first. The next code milestone should be limited to the smallest verified extraction or facade that provides a real second composition without breaking existing Courier operations.
