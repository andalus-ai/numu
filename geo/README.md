# Geo capability

Geo owns provider-neutral geographic primitives and spatial semantics shared by products. It is a reusable capability, not a mandatory service or a reason to make every consumer depend on a map vendor.

## Domain scope

The `numou-geo-domain` artifact provides:

- validated WGS84 points in decimal degrees;
- great-circle distance and path-length estimates in metres;
- inclusive geographic bounds, including bounds crossing the antimeridian;
- deliberately rounded coordinate string representations to reduce accidental precision leakage in logs.

These are deterministic geographic calculations. They do **not** provide road routing, travel-time estimates, geocoding, polygon topology, address validation, administrative boundaries, or a PostGIS adapter.

## Ownership and reuse

- **Geo:** coordinate reference and units, bounds, distance primitives, future spatial query/geofence contracts.
- **Routing:** road-network routes, route geometry, traffic-aware ETA and provider/network-specific constraints. Straight-line distance is not a route.
- **Transport:** stops, ordered passenger journey segments and segment capacity.
- **Courier/fulfillment:** pickup and delivery workflow, coverage policy and shipment tracking.
- **Fleet/vehicle service:** vehicle/workshop location and service-area policy.
- **Travel/hospitality:** destinations and venue references.
- **Construction:** project/site geometry and measurement workflows.

Consumers should depend on Geo only when its public semantics are appropriate. The Mobility `LatLng` model remains available as a compatibility type and converts explicitly to/from `GeoPoint` without rounding. Mobility-specific grid, route-estimation, movement and service-area policies remain Mobility-owned. Inventory's `BigDecimal` coordinates are not migrated by this change.

## Milestones

- **M1:** standalone domain artifact, validated point/bounds primitives, spherical distance, tests and ownership documentation.
- **M2:** audit consumer semantics and document compatibility/migration boundaries.
- **M3 (this change):** explicit Mobility `LatLng` conversion to/from the shared Geo point, validation/precision compatibility tests, and cross-reactor CI installation order. No existing Mobility public method signatures are removed.
- **Next:** inspect and verify Geo/Mobility reactor CI, then consider extracting only proven shared math. Do not add PostGIS, geocoding, routing, a universal Location aggregate, or polygon services without concrete consumer requirements and a separate milestone.

## Security and operational constraints

Precise location can be sensitive. Do not include raw coordinates in logs, metric labels, traces or exception messages by default. Private saved locations need tenant scoping and authorization at the application boundary; a location identifier is not proof of access. Imported geographic datasets must record source, version, license, import timestamp and integrity information. Provider calls need timeouts, bounded retries, caching policy and explicit data-sharing rules.

## Build and verification

The focused Geo reactor can be verified with:

```sh
mvn -B -ntp -f geo/pom.xml verify
```

Because Mobility is a separate Maven reactor, install Geo before building Mobility:

```sh
mvn -B -ntp -f geo/pom.xml install
mvn -B -ntp -f mobility/pom.xml -pl mobility-kernel -am verify
```

These commands are the intended narrow verification sequence. The milestone is not considered verified until CI results confirm them.
