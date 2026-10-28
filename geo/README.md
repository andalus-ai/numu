# Geo capability

Geo owns provider-neutral geographic primitives and deterministic spatial semantics shared by Numou products. It is a reusable capability, not a mandatory service or a reason to make every consumer depend on a map vendor.

## Domain scope

The `numou-geo-domain` artifact currently provides:

- validated WGS84 points in decimal degrees (`GeoPoint`);
- great-circle distance and path-length estimates in metres (`GeoDistance`);
- inclusive geographic bounds, including bounds crossing the antimeridian (`GeoBounds`);
- configurable angular grid bucketing with resolution-aware cell identity (`GeoGrid`, `GeoGridCell`);
- a provider-neutral precision-5 encoded-polyline codec (`GeoPolylineCodec`);
- deliberately rounded coordinate string representations to reduce accidental precision leakage in logs.

Encoded polylines are quantized transport representations, not lossless coordinate storage. Geo's distance and path-length calculations are spherical approximations. Angular grid cells are coarse candidate buckets, not equal-area cells or distance guarantees; exact geographic predicates must filter candidates.

The domain artifact does **not** currently provide road routing, travel-time estimates, geocoding, address validation, topology-aware polygon operations, administrative-boundary datasets, or a PostGIS adapter.

## Ownership and reuse

- **Geo:** coordinate reference and units, validated geographic primitives, bounds, deterministic geometry helpers, configurable angular bucketing and provider-neutral encodings.
- **Routing:** road-network routes, route geometry, traffic-aware ETA, route constraints and provider/network integrations.
- **Mobility/Ride:** ride lifecycle, driver dispatch, fixed driver-index grid resolution, GPS plausibility/movement policy, fare estimation policy and service coverage decisions.
- **Courier/fulfillment:** pickup and delivery workflow, coverage policy and shipment tracking.
- **Fleet/vehicle service:** vehicle/workshop location and service-area policy.
- **Travel/hospitality:** destinations and venue references.
- **Construction:** project/site geometry and measurement workflows.

Consumers should depend on Geo only when its public semantics are appropriate. Mobility's `LatLng` and `Polyline` types remain compatibility facades and convert to/from the shared Geo primitives without changing public signatures. The Mobility polyline facade delegates encoding/decoding to `GeoPolylineCodec`, and `GeoMath` delegates great-circle distance and path-length accumulation to `GeoDistance`. Mobility's `GeoCell.of` delegates angular cell calculation to `GeoGrid` while preserving the existing index and database key format.

Mobility's fixed `GeoCell` size (0.01 degrees), legacy key format and radius-to-candidate-cell enumeration remain Mobility-owned because they are part of driver-index/surge-zone behavior. Geo's grid key includes its resolution to prevent collisions between grids. Grid indices do not wrap at the antimeridian; a spatial query must explicitly handle longitude wrap and polar coverage before using grid cells as a complete candidate set. Mobility's route-estimation fallback, movement rules, local-projection segment distance, path simplification and service-area policies also remain Mobility-owned until shared semantics and real non-Mobility consumers justify extraction.

Inventory's existing `BigDecimal` coordinate model is not migrated by this capability change.

## Milestones

- **M1:** standalone domain artifact, validated point/bounds primitives, spherical distance, tests and ownership documentation.
- **M2:** audit consumer semantics and document compatibility/migration boundaries.
- **M3:** explicit Mobility `LatLng` conversion to/from the shared Geo point, validation/precision compatibility tests, and cross-reactor CI installation order.
- **M4:** Mobility's great-circle distance delegates to Geo's canonical haversine implementation while preserving `GeoMath` signatures and the public Earth-radius constant.
- **M5:** extract precision-5 encoded-polyline encoding/decoding into the neutral Geo domain; add configurable resolution-aware angular grid cells; retain the Mobility `Polyline` and `GeoCell` APIs as compatibility facades; centralize path-length validation and accumulation; add shared and cross-capability regression tests.
- **M6:** preserve consumer coordinate compatibility, including Inventory's precision-sensitive `BigDecimal` model and explicit boundary tests.
- **M7 (contract proposal):** define provider-neutral spatial query semantics before adding query APIs or database adapters. See [Spatial Query Contract M7](docs/SPATIAL-QUERY-CONTRACT-M7.md). This document is a design contract, not evidence that a query port or adapter already exists.
- **Next:** choose one concrete consumer query, then implement the minimal provider-neutral port and one adapter with exact-predicate, antimeridian/polar, tenancy, ordering and failure-mode tests. Add other adapters only when a real deployment needs them. Do not silently turn straight-line estimates into road routes or move consumer policies into Geo.

## Compatibility and security

- Preserve existing Mobility public method signatures and indexed grid key format during extraction; adapt at the boundary rather than forcing all consumers to migrate at once.
- Do not treat encoded polylines as exact coordinate archives. Encoding rounds coordinates to precision 5.
- Precise location can be sensitive. Do not include raw coordinates in logs, metric labels, traces or exception messages by default. Avoid using coordinates as stable identifiers.
- Private saved locations need tenant scoping and authorization at the application boundary; possession of a location identifier is not proof of access.
- Imported geographic datasets must record source, version, license, import timestamp and integrity information.
- Provider calls need timeouts, bounded retries, caching policy and explicit data-sharing rules.

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

These are the intended narrow verification commands. This milestone is not considered verified until CI confirms both reactors and their tests.
