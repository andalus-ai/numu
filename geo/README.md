# Geo capability

Geo owns provider-neutral geographic primitives and deterministic spatial semantics shared by Numou products. It is a reusable capability, not a mandatory service or a reason to make every consumer depend on a map vendor.

## Domain scope

The `numou-geo-domain` artifact currently provides:

- validated WGS84 points in decimal degrees (`GeoPoint`);
- great-circle distance and path-length estimates in metres (`GeoDistance`);
- inclusive geographic bounds, including bounds crossing the antimeridian (`GeoBounds`);
- a provider-neutral precision-5 encoded-polyline codec (`GeoPolylineCodec`);
- deliberately rounded coordinate string representations to reduce accidental precision leakage in logs.

Encoded polylines are quantized transport representations, not lossless coordinate storage. Geo's distance and path-length calculations are spherical approximations. They must not be presented as road distance or ETA.

The domain artifact does **not** currently provide road routing, travel-time estimates, geocoding, address validation, topology-aware polygon operations, administrative-boundary datasets, or a PostGIS adapter.

## Ownership and reuse

- **Geo:** coordinate reference and units, validated geographic primitives, bounds, deterministic geometry helpers and provider-neutral encodings.
- **Routing:** road-network routes, route geometry, traffic-aware ETA, route constraints and provider/network integrations.
- **Mobility/Ride:** ride lifecycle, driver dispatch, driver-index cell sizing, GPS plausibility/movement policy, fare estimation policy and service coverage decisions.
- **Courier/fulfillment:** pickup and delivery workflow, coverage policy and shipment tracking.
- **Fleet/vehicle service:** vehicle/workshop location and service-area policy.
- **Travel/hospitality:** destinations and venue references.
- **Construction:** project/site geometry and measurement workflows.

Consumers should depend on Geo only when its public semantics are appropriate. Mobility's `LatLng` and `Polyline` types remain compatibility facades and convert to/from the shared Geo primitives without changing public signatures. The Mobility polyline facade delegates encoding/decoding to `GeoPolylineCodec`, and `GeoMath` delegates great-circle distance and path-length accumulation to `GeoDistance`.

Mobility's fixed `GeoCell` grid remains Mobility-owned for now because its 0.01-degree cell size and key format are part of its driver-index/surge-zone behavior. A future shared spatial-index contract must use explicit configurable resolution and document antimeridian/polar behavior before this policy is generalized. Mobility's route-estimation fallback, movement rules, local-projection segment distance, path simplification and service-area policies also remain Mobility-owned until shared semantics and real non-Mobility consumers justify extraction.

Inventory's existing `BigDecimal` coordinate model is not migrated by this capability change.

## Milestones

- **M1:** standalone domain artifact, validated point/bounds primitives, spherical distance, tests and ownership documentation.
- **M2:** audit consumer semantics and document compatibility/migration boundaries.
- **M3:** explicit Mobility `LatLng` conversion to/from the shared Geo point, validation/precision compatibility tests, and cross-reactor CI installation order.
- **M4:** Mobility's great-circle distance delegates to Geo's canonical haversine implementation while preserving `GeoMath` signatures and the public Earth-radius constant.
- **M5 (this change):** extract precision-5 encoded-polyline encoding/decoding into the neutral Geo domain; retain the Mobility `Polyline` API as a compatibility facade; centralize path-length validation and accumulation; add shared codec and compatibility regression tests.
- **Next:** consider a configurable neutral grid/spatial-index primitive and a provider-neutral route request/result contract only after their coordinate semantics, precision, antimeridian behavior, failure model, and consumers are audited. Do not silently turn straight-line estimates into road routes or move ride policies into Geo.

## Compatibility and security

- Preserve existing Mobility public method signatures during extraction; adapt at the boundary rather than forcing all consumers to migrate at once.
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
