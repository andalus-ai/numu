# M40 — Shipment Dependency and Persistence Trace

- **Status:** Source-level trace; extraction remains blocked on additional consumer/runtime verification
- **Repository:** `andalus-ai/numu`
- **Related:** [M38 reuse audit](SHIPMENT-CAPABILITY-REUSE-AUDIT.md), [M39 extraction contract](SHIPMENT-CAPABILITY-EXTRACTION-AND-COMPOSITION.md), [M41 POD authorization and event boundary](SHIPMENT-POD-AUTHORIZATION-AND-EVENT-BOUNDARY.md), [Capability packaging contract](CAPABILITY-PACKAGING-AND-COMPOSITION.md)
- **Evidence basis:** Current `main` source files and migration scripts inspected for this milestone. This is not a complete repository-wide static-analysis report and does not claim tests passed.

## 1. Executive finding

**Do not extract `Shipment` as a standalone artifact yet.** The current model is a tenant-scoped booking aggregate that owns a collection of `Parcel` values; its PostgreSQL repository saves the shipment and parcels in one transaction. Tracking facts reference both shipment and parcel, and the database enforces tenant-consistent composite foreign keys. Shipment delivery completion is also coordinated with proof-of-delivery (POD) projection behavior, while the same Postgres adapter owns many unrelated courier migrations and operational components.

The safest first code step is therefore a consumer-safe application contract around the existing authoritative implementation, plus explicit composition/dependency tests. A class/package move or a new shipment schema is not justified by this trace alone.

## 2. Class-level trace

| Type / surface | Observed responsibility and dependency | Extraction implication |
| --- | --- | --- |
| `Shipment` | Tenant-owned aggregate; validates reference, lifecycle timestamps, status, and parcel membership; restores persisted state with delivery outcome | Keep the existing aggregate authoritative; move only after all callers and invariants are traced |
| `Parcel` / `ParcelId` | Physical-piece identity and positive dimensions/weight; parcels are held by the shipment aggregate and independently scannable | Parcel is not currently an independent package aggregate boundary; do not duplicate or move it casually |
| `ShipmentRepository` | Tenant-explicit async persistence port: save, find by ID/reference, delete draft | A viable internal application/domain port, but its package/type surface is currently courier-domain API |
| `BookShipmentCommand` / `BookShipmentHandler` | Command carries tenant, shipment ID, addresses, service level, parcel list and occurrence time; handler constructs aggregate, adds parcels, books, then saves | Existing booking behavior can be wrapped by a stable public application contract; preserve validation and save semantics |
| `ShipmentStatus` | `DRAFT`, `BOOKED`, `CANCELLED`, `DELIVERED`; detailed movement is kept in tracking history | Do not conflate booking lifecycle with transport/custody status in consumer models |
| `TrackingEvent` | Immutable tenant-scoped event with shipment ID, optional parcel ID, occurrence/recording times, source, actor reference and idempotency key | Tracking is tightly linked to shipment and parcel identities; expose a stable read/append contract, not adapter entities |
| `TrackingEventRepository` | Tenant-explicit append-only and idempotent append; paginated shipment timeline with stable cursor | Preserve idempotency conflict semantics and stable ordering across local/remote adapters |
| `JdbcShipmentRepository` | Quarkus `@ApplicationScoped` JDBC adapter; checks tenant matches aggregate; saves shipment and parcel rows transactionally; blocks saving delivered status because POD projector owns delivery completion | Adapter is not suitable as the public library API; transaction and delivery ownership must remain explicit |
| `JdbcTrackingEventRepository` | Quarkus-scoped JDBC adapter; tenant check, append-only insert, idempotency replay comparison and stable timeline query | Keep optional; persistence implementation and SQL must not leak into consumer contracts |
| `JdbcProofOfDeliveryTrackingProjector` and POD use case | Delivery outcome is projected from POD evidence rather than arbitrary shipment repository saves | Trace its transaction and event guarantees before relocating delivery transition ownership |
| `CourierEventPublisher`, outbox operations/workers | Existing courier adapter contains publication and operational worker components | Shipment-only consumers must not pull these in transitively unless the selected composition requires them |

### Source files reviewed

- `logistics/numou-courier-domain/src/main/java/tech/kayys/numou/courier/domain/Shipment.java`
- `.../ShipmentStatus.java`, `.../Parcel.java`, `.../ShipmentRepository.java`
- `.../TrackingEvent.java`, `.../TrackingEventRepository.java`
- `logistics/numou-courier-application/src/main/java/tech/kayys/numou/courier/application/command/BookShipmentCommand.java`
- `.../BookShipmentHandler.java`
- `logistics/numou-courier-postgres-adapter/src/main/java/tech/kayys/numou/courier/adapter/postgres/JdbcShipmentRepository.java`
- `.../JdbcTrackingEventRepository.java`
- `.../JdbcProofOfDeliveryTrackingProjector.java`
- Courier migrations V3, V4, V8, V22 and V24.

## 3. Persistence and migration coupling

### Shipment and parcel

Migration `V3__shipment_and_parcel_foundation.sql` creates `courier_shipments` and `courier_parcels`. Both use tenant-scoped composite primary keys. Parcel rows reference the parent shipment with `(tenant_id, shipment_id)`, and the JDBC shipment repository writes shipment plus all parcel rows within one JDBC transaction. The aggregate and persistence adapter therefore agree that shipment booking and its parcel set are atomic.

**Implication:** a consumer-facing contract may refer to parcels, but extraction must not create a second package model or split parcel writes without a deliberate aggregate/transaction redesign.

### Tracking and custody references

Migration `V4__tracking_events_and_custody.sql` creates append-only `courier_tracking_events`, unique by `(tenant_id, id)` and `(tenant_id, idempotency_key)`. Composite foreign keys require the referenced shipment and optional parcel to belong to the same tenant; the parcel foreign key also binds the parcel to the same shipment. Runtime guidance in the migration calls for INSERT/SELECT rather than UPDATE/DELETE.

The JDBC tracking adapter implements replay semantics: the same tenant-scoped idempotency key with the same fact returns the original event; reuse for a different fact conflicts. Timeline queries use descending `occurred_at, id` ordering and an exclusive cursor.

**Implication:** tracking is a stable candidate contract but is not persistence-independent today. Preserve tenant scoping, append-only semantics, conflict behavior and cursor ordering if introducing in-process or remote adapters.

### Courier-only migrations share the same adapter

Migration `V8__courier_pricing_quotes_and_cod.sql` adds rate cards, shipment quotes and COD obligations referencing `courier_shipments`. Migration `V22__courier_pod_transactional_outbox.sql` adds the courier event outbox. Migration `V24__courier_shipment_delivery_completion.sql` adds `delivered_at`, extends shipment status constraints, and enforces delivery timestamp consistency.

These scripts currently live in the same courier Postgres adapter migration directory. This proves storage/migration packaging is mixed; it does **not** by itself establish the safest migration split or whether all migration versions can be relocated independently.

**Implication:** do not move or rename existing migrations in-place. First determine deployment history and Flyway checksum expectations, then design a backward-compatible new installation/upgrade path with one migration owner for each table.

## 4. Confirmed boundary constraints

1. **Tenant context is part of the contract.** Tenant ID is stored on shipment, booking commands and tracking facts; JDBC ports verify tenant ownership and SQL reads are tenant-scoped. A public API must not accept a shipment ID as authorization.
2. **Booking and delivery completion are different paths.** Booking creates and persists a booked aggregate. Delivered status is managed through POD projection and the repository rejects direct persistence of a delivered shipment. Preserve that ownership until the POD transaction path is fully traced.
3. **Shipment and parcel are transactionally coupled.** Do not split their writes in the first extraction.
4. **Tracking is append-only and idempotent.** Preserve replay/conflict behavior and timeline cursor stability.
5. **Courier infrastructure is optional.** The Quarkus/Postgres adapter includes courier-wide migrations and workers; a shipment contract must not make it a transitive requirement.
6. **Service-level pricing is adjacent, not necessarily owned by shipment lifecycle.** V8 links quotes and COD to shipment IDs. A shipment consumer may need the stable ID but should not be forced to load rate-card/COD policy modules.

## 5. Gaps still open before code extraction

This source-level pass does not yet prove:

- all production and test consumers of shipment and tracking types across every reactor;
- the full transaction boundary across POD evidence, shipment status projection, outbox insertion and event publication;
- complete Flyway migration history and deployed-schema compatibility;
- current authorization wiring for each booking, tracking and POD use case;
- whether a remote API already exists or has compatibility commitments;
- actual build/test/CI results for the relevant reactor.

These are explicit follow-up verification tasks, not assumed facts.

## 6. Recommended next code slice

Implement a **narrow Shipment Application Contract** in a separately consumable package/artifact only if the existing Maven graph supports it without introducing an unnecessary new deployment unit. The contract should expose:

- tenant-scoped booking command and result;
- shipment lookup by ID/reference;
- tracking timeline query with stable cursor;
- append-tracking operation only where the caller is authorized and the domain policy permits it;
- typed outcomes for validation, not-found, lifecycle conflict and idempotency conflict;
- actor/correlation context where required by existing authorization/audit conventions.

Then provide an in-process adapter over the current use cases. Do not add a parallel `Shipment` entity, change table names, move migrations, or implement a second lifecycle. Add architecture tests proving that the public contract has no Quarkus/JDBC/Flyway/COD/fleet dependency, then add consumer fixtures using existing repository test conventions. Remote transport is a later optional adapter after contract and compatibility tests exist.

## 7. Completion checklist

- [x] Shipment, parcel, tracking ports and primary JDBC adapters inspected.
- [x] V3/V4 shipment, parcel and tracking constraints inspected.
- [x] V8 commercial/COD coupling and V22/V24 POD/outbox/delivery migrations inspected.
- [x] High-risk extraction constraints recorded.
- [ ] Full repository-wide consumer inventory completed.
- [ ] POD transaction and event/outbox path traced end to end.
- [ ] Flyway deployed-history and upgrade compatibility verified.
- [ ] Public application contract implemented without duplicate lifecycle.
- [ ] In-process adapter and consumer composition tests pass.
- [ ] Relevant logistics reactor and CI pass.

## 8. Verification statement

This milestone is a source inspection and architecture trace. No Maven command, migration test, integration test, or CI run is claimed. The extraction remains incomplete until the unchecked items above have evidence.
