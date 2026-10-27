# M42 — Shipment consumer inventory and public-contract readiness

- **Status:** M42 inventory baseline; M43 implements the initial in-process application facade, while exhaustive consumer proof remains incomplete.
- **Depends on:** M39 extraction/composition contract, M40 dependency/persistence trace, M41 POD authorization/event-boundary review.
- **Decision:** Preserve the current shipment implementation as the single source of truth. Do not split artifacts, database ownership, or migration history until the remaining consumers and composition paths are verified.
- **Implemented follow-up:** [M43 Shipment public application contract](SHIPMENT-PUBLIC-APPLICATION-CONTRACT.md).

## 1. Current capability packaging

The repository tree shows a logistics Maven reactor at `logistics/pom.xml` (artifact `numou-fms`) with these modules:

| Module | Observed role | Implication |
| --- | --- | --- |
| `numou-courier-domain` | Shipment, parcel, tracking, and broader courier domain types and repository ports | Existing authoritative domain model; currently packaged together with hub, dispatch, pickup, partner, route, and COD concepts |
| `numou-courier-application` | Booking/quote/ETA handlers, POD use case, pickup and operational use cases, ports | Existing application behavior can be wrapped, but its public surface is currently organized under the courier package |
| `numou-courier-postgres-adapter` | JDBC adapters, Quarkus wiring, outbox workers/publishers, Flyway migrations | Infrastructure-heavy adapter; must remain optional to consumers that supply their own persistence adapters |

The application POM depends on courier domain and organization domain. The PostgreSQL adapter separately declares Quarkus Arc, scheduler, PostgreSQL JDBC, and Flyway. This is encouraging dependency direction at the artifact level, but is not a proof that all transitive dependencies or source-level references are clean.

## 2. Source-level consumer inventory baseline

### Shipment booking and aggregate

- `Shipment`, `Parcel`, `ShipmentId`, `ShipmentRepository`, and `ShipmentStatus` live in `numou-courier-domain`.
- `BookShipmentCommand` and `BookShipmentHandler` live in `numou-courier-application`.
- `JdbcShipmentRepository` lives in the PostgreSQL adapter and persists shipment and parcel state together.
- Existing application tests include `BookShipmentHandlerTest`; domain tests include `ShipmentTest`.

**Contract implication:** booking must delegate to the existing handler/aggregate, preserve the tenant-scoped repository port, and preserve shipment-plus-parcel transactional persistence. Do not expose persistence entities or return mutable aggregate internals as integration DTOs.

### Tracking

- `TrackingEvent`, `TrackingCursor`, and `TrackingEventRepository` live in courier domain.
- `JdbcTrackingEventRepository` lives in the PostgreSQL adapter.
- Tracking uses tenant-scoped idempotency and a stable descending `(occurred_at, id)` cursor.

**Contract implication:** a timeline contract must carry both cursor fields, enforce tenant scope, and distinguish an identical replay from reuse of a key for a different fact. Avoid a timestamp-only cursor or a generic untyped event bag.

### Proof of delivery and delivery completion

- `RecordProofOfDelivery` and `ProofOfDeliveryStore` live in courier application.
- `JdbcProofOfDeliveryStore`, `JdbcProofOfDeliveryTrackingProjector`, and `ProofOfDeliveryProjectionWorker` live in the PostgreSQL adapter.
- Existing source comments say the POD command does not directly advance shipment state; the projector processes committed outbox events and coordinates tracking projection.

**Contract implication:** do not include POD in the first shipment contract by default. First prove the authorization entry points and lifecycle semantics. Evidence accepted, tracking projected, and shipment delivered are different outcomes.

### Broader courier-only dependencies

The same domain module contains hub, hub transfer, dispatch wave, delivery task, pickup, transport container/seal, network partner, route planning, ETA, commercial pricing, quote, and COD types. The PostgreSQL adapter owns corresponding repositories and operational workers.

**Contract implication:** Shipment should not acquire transitive dependencies on COD, fleet, hub operations, or partner networking just because those types share a Maven artifact today. The first contract must remain deliberately narrow and must not move or rename these existing modules as a side effect.

## 3. Transport/API discovery

The repository contains documentation for courier HTTP transport and HTTP outbox integration (`docs/courier/M29-http-transport.md` and `M30-http-outbox-integration.md`) and an `HttpCourierEventPublisher` in the PostgreSQL adapter. The repository tree scan did not reveal a separate courier HTTP transport Maven module under `logistics/`.

This is a discovery result, not proof that there is no other API composition elsewhere in the repository. The M42 code gate remains to identify the actual endpoint/resource or composition entry points, their authentication/tenant resolution, and whether consumers depend on internal types.

## 4. Consumer inventory confidence and remaining work

A repository-tree scan establishes where the obvious shipment and tracking types are defined. The available GitHub code-search connector did not return usable matches for class-name searches, so this review cannot certify an exhaustive import/call-site inventory.

Before the first public contract is treated as stable, run repository-wide source search and Maven dependency analysis in a checked-out workspace for at least:

- imports/references to `Shipment`, `ShipmentRepository`, `ShipmentId`, `Parcel`, `TrackingEventRepository`, and `BookShipmentHandler`;
- SQL or migrations that reference `courier_shipments`, `courier_parcels`, or `courier_tracking_events` outside the owning adapter;
- HTTP/resource, messaging, scheduler, webhook, and product-application call sites;
- module dependency paths that bring the PostgreSQL adapter or Quarkus into consumers;
- authorization and tenant-context construction at every externally reachable entry point.

For each call site, record its required operations, tenant source, actor source, authorization point, idempotency key, error behavior, and whether it is in-process or remote. The inventory should be checked into the repo or enforced by automated architecture tests so it can be reproduced after future changes.

## 5. Proposed first public contract (not yet implemented)

Once the remaining call sites are confirmed, introduce a small application-facing contract in the existing application module or a separately published contract artifact only if dependency analysis proves the extra artifact is useful. It should cover:

1. **Book shipment:** typed request and result, tenant explicit, stable shipment ID/reference, parcel inputs, actor/correlation context where required, idempotency semantics if the underlying operation supports them.
2. **Find shipment:** tenant-scoped lookup by stable ID and/or tenant-scoped reference; not-found is an explicit outcome.
3. **Read tracking timeline:** tenant-scoped page with events and a composite cursor based on occurrence time and event ID.
4. **Typed failures:** invalid request, not found, lifecycle conflict, idempotency replay, idempotency conflict, and infrastructure failure without leaking JDBC exceptions.

An in-process adapter should delegate to existing application handlers and ports. A future HTTP adapter should map the same outcomes to a versioned transport contract and must enforce authentication/authorization before invoking the port. Remote and in-process use must preserve the same tenant, idempotency, and lifecycle semantics.

Do not introduce a second shipment entity, duplicate booking rules, expose JDBC rows, move existing Flyway migrations, or imply POD acceptance means delivery completion.

## 6. Acceptance criteria for implementation milestone

- [ ] Repository-wide call-site/import and migration-reference inventory reviewed.
- [ ] Actual HTTP/runtime entry points and tenant/actor resolution identified.
- [ ] Public DTOs and interfaces contain no Quarkus, JDBC, Flyway, SQL, or adapter types.
- [ ] Booking delegates to existing authoritative behavior and preserves parcel atomicity.
- [ ] Lookup and timeline reads cannot cross tenant boundaries.
- [ ] Tracking cursor is stable for equal occurrence timestamps.
- [ ] Replay and conflicting idempotency-key cases have explicit, tested outcomes.
- [ ] Application module can be consumed without depending on the PostgreSQL adapter.
- [ ] No schema or migration-history changes are required for the initial contract.
- [ ] Relevant Maven reactor tests and CI are run and their results recorded.

## Validation record

- Inspected root `AGENTS.md`, ADR-003, the capability packaging contract, logistics aggregator and module POMs, repository tree paths for courier/shipment/tracking, and selected booking/POD/tracking source files in M40/M41.
- PR #664 (M40) and PR #676 (M41) were confirmed merged before this milestone began.
- The GitHub code-search connector returned no usable source matches for the tested class-name queries; no exhaustive consumer inventory is claimed.
- No Maven build, source-wide static analysis, migration test, integration test, or CI run was executed for M42.
