# Cargo & Courier — Milestone 2: Shipment and Parcel Foundation

**Status:** Implementation in progress  
**Scope:** Shipment booking domain, multi-piece parcel identity, tenant-scoped persistence, and application booking use case.

## Domain model

- A shipment belongs to exactly one tenant and has a tenant-local normalized reference.
- Sender and recipient contact/address data is captured as an immutable booking-time snapshot.
- A shipment selects a delivery service level (ECONOMY, STANDARD, EXPRESS, or SAME_DAY).
- A shipment can be drafted, booked, or cancelled. Booking requires at least one parcel; parcel additions are draft-only.
- Each parcel has a stable ID, shipment-local reference, positive weight and dimensions, and optional handling instructions.
- Volumetric weight is calculated from dimensions and an explicit divisor; the divisor is not silently hard-coded because it may vary by carrier/service.
- Shipment and parcel identity is separate from physical tracking events, hub scans, and custody history, which belong to later milestones.

## Persistence

- Migration V3 creates tenant-scoped shipment and parcel tables.
- The shipment key is (tenant_id, id); a unique (tenant_id, normalized_reference) constraint is the final concurrency guard.
- Parcels have a composite foreign key (tenant_id, shipment_id) to the owning shipment, preventing cross-tenant attachment at the database layer.
- JdbcShipmentRepository writes shipment and parcel rows in one transaction and scopes reads/deletes by tenant.
- Shipment.reconstitute restores persisted state without replaying booking/cancellation transitions.
- Flyway migrations are owned by the PostgreSQL adapter module, not the domain module, avoiding duplicate migration versions on a runtime classpath.

## Application flow

BookShipmentHandler creates a draft aggregate, adds validated parcels, books the aggregate, and delegates the atomic save to ShipmentRepository. Empty parcel lists are rejected before persistence.

## Verification included

- Domain tests for required parcels, lifecycle immutability, parcel identity/reference uniqueness, invalid dimensions, monotonic timestamps, volumetric-weight arithmetic, and reconstitution after cancellation.
- Application test for multi-piece booking and rejection of an empty parcel list.
- Docker-backed PostgreSQL tests for aggregate round-trip, tenant-isolated reads, tenant-local shipment-reference uniqueness, and concurrent hub-code uniqueness inherited from M1.

## Remaining acceptance work

- Execute the Maven reactor and Testcontainers suite in CI; source changes alone do not imply passing tests.
- Add idempotency-key semantics and durable request replay handling before exposing booking APIs.
- Add carrier-specific dimensional-weight rules and shipment price quotation in the pricing milestone.
- Add a public tracking/reference policy, label generation, and immutable tracking events in M3.
- Add API authentication, authorization, request validation, and observability in the consuming runtime before production exposure.
