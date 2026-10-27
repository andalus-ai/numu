# Geo capability

Geo owns provider-neutral geographic primitives and spatial semantics shared by products. It is a reusable capability, not a mandatory service or a reason to make every consumer depend on a map vendor.

## M1 domain scope

The initial `numou-geo-domain` artifact provides:

- validated WGS84 points in decimal degrees;
- great-circle distance and path-length estimates in metres;
- inclusive geographic bounds, including bounds crossing the antimeridian;
- deliberately rounded coordinate string representations to reduce accidental precision leakage in logs.

These are deterministic geographic calculations. They do **not** provide road routing, travel-time estimates, geocoding, polygon topology, address validation, administrative boundaries, or a PostGIS adapter.

## Ownership and reuse

- **Geo:** coordinate reference and units, bounds, distance primitives, future spatial query/geofence contracts and provider-neutral location references.
- **Routing:** road-network routes, route geometry, traffic-aware ETA and provider/network-specific constraints. Straight-line distance is not a route.
- **Transport:** stops, ordered passenger journey segments and segment capacity.
- **Courier/fulfillment:** pickup and delivery workflow, coverage policy and shipment tracking.
- **Fleet/vehicle service:** vehicle/workshop location and service-area policy.
- **Travel/hospitality:** destinations and venue references.
- **Construction:** project/site geometry and measurement workflows.

Consumers should depend on Geo only when its public semantics are appropriate. Existing mobility and inventory location models are not silently replaced in this milestone; migration needs consumer-by-consumer compatibility tests.

## Planned milestones

- **M1 (this change):** standalone domain artifact, validated point/bounds primitives, spherical distance, tests and ownership documentation.
- **M2:** audit consumer semantics and publish application/SPI contracts for geocoding, spatial queries and geofences only where multiple real use cases require them.
- **M3:** optional PostgreSQL/PostGIS adapter with tenant-safe query contracts and migration ownership.
- **M4:** provider adapters and versioned, provenance-aware Indonesia administrative-boundary data, subject to source licensing and update policy.
- **M5:** compatibility-preserving integrations with mobility, transport, courier, fleet/vehicle service, travel and construction.

## Security and operational constraints

Precise location can be sensitive. Do not include raw coordinates in logs, metric labels, traces or exception messages by default. Private saved locations need tenant scoping and authorization at the application boundary; a location identifier is not proof of access. Imported geographic datasets must record source, version, license, import timestamp and integrity information. Provider calls need timeouts, bounded retries, caching policy and explicit data-sharing rules.

## Build

From the repository root, run:

```sh
mvn -B -ntp -f geo/pom.xml verify
```

This command is a proposed narrow verification entry point; it must be run in CI before the milestone is considered verified.
