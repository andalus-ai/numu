# Geo M7: provider-neutral spatial query contract

**Status:** Contract proposal for review; no query implementation or database adapter is implied.

## Purpose

Define stable semantics for geographic search across Numou capabilities before introducing an in-memory index, SQL adapter, PostGIS integration, or remote provider. The contract must be usable by standalone modules and by modular-monolith or distributed compositions without making the Geo domain depend on storage or transport technology.

## Ownership boundaries

- **Geo domain:** coordinate reference system, units, validated primitives, deterministic geometry predicates, and documented edge semantics.
- **Geo application/query port (future, only when a concrete consumer needs it):** query input/output, result semantics, paging/limits, and provider-neutral failure behavior.
- **Adapters:** database/index-specific candidate retrieval, query translation, and capability reporting. An adapter must not silently weaken the contract.
- **Consumer capability:** business eligibility, service coverage, tenancy, authorization, retention, purpose limitation, and whether a location may be disclosed.
- **Routing capability/provider:** road-network paths, traffic, travel time, route constraints, and provider-specific behavior. A straight-line distance query is not routing.

Do not create a universal Location aggregate, mandatory Geo service, or a database schema as part of this contract alone.

## Query categories and correctness

A future API must name the predicate being requested instead of exposing an ambiguous generic `searchNearby` operation.

1. **Within radius:** input point, non-negative finite radius, and explicit distance unit (canonical internal distance is metres). Exact results satisfy the documented distance metric and boundary rule.
2. **Within bounds:** input validated `GeoBounds`; bounds are inclusive. A longitude interval may cross the antimeridian when west is greater than east.
3. **Intersecting geometry:** not part of the initial contract. Polygon topology, holes, self-intersections, and geometry validity need a separate reviewed contract before exposure.
4. **Nearest:** must state whether it means nearest by spherical distance or by a provider-supplied metric. It must define tie ordering and whether a maximum radius applies.

Candidate retrieval is an implementation detail, not proof of a match. Grid cells, bounding boxes, geohashes, and database index operators may over-select. If an API promises exact radius results, candidates must be filtered by the canonical exact predicate before being returned. If a future API exposes candidates directly, its name and response must make that approximation explicit.

## Coordinate and distance rules

- Coordinates use WGS84 latitude/longitude in decimal degrees unless an explicit future contract says otherwise.
- Latitude is in [-90, 90], longitude in [-180, 180], with finite values only.
- Canonical distance inputs and outputs use metres. Unit conversion must be explicit at API boundaries.
- The existing `GeoDistance` implementation is a spherical approximation. It must not be described as ellipsoidal-geodesic or road distance.
- The contract must identify the distance algorithm and its accuracy limitations. Changing the algorithm later is a behavior change that requires compatibility review and regression tests.
- Conversions from precision-sensitive `BigDecimal` models to binary `double` must be explicit at consumer boundaries; document whether the conversion rounds, preserves a configured decimal scale, or rejects precision loss. Never rewrite consumer persistence values as a side effect of querying.
- Encoded polylines are quantized representations and are not authoritative precision-preserving coordinate storage.

## Antimeridian and polar correctness

- Longitude is circular, not a linear axis. Bounds where west > east cross the antimeridian and include the interval [west, 180] union [-180, east].
- Both +180 and -180 denote the same meridian for geographic predicates; tests must cover both representations where appropriate.
- Index implementations must not assume a query is complete merely because ordinary longitude grid indices were enumerated. They must correctly handle wrap-around and document candidate expansion.
- Near the poles, longitude degrees represent very small east-west distances. Latitude/longitude cell sizes are angular, not equal-area, and cannot provide a universal distance guarantee.
- A candidate index may over-select; it must not under-select points that satisfy the exact predicate. If the adapter cannot guarantee completeness for a region or query shape, it must fail explicitly or use a correct fallback rather than silently omit matches.
- Boundary inclusion is inclusive for the existing `GeoBounds` predicate. Radius boundary behavior must be explicitly specified and tested before a query API is published.

## Result ordering, paging, and limits

Any future application query port should define these rules before implementation:

- Stable ordering for nearest results: distance ascending, then a documented deterministic secondary key supplied by the owning capability.
- Deterministic ordering for non-distance searches when paging is supported.
- Bounded page size and maximum candidate/result budgets to prevent unbounded scans.
- Explicit distinction between truncation, no matches, unsupported query, invalid input, and provider/index failure.
- No promise of globally nearest results if an adapter only searches a bounded candidate set.
- No claim that an adapter supports a predicate unless its implementation and tests establish that behavior.

Do not introduce a shared identifier type for consumer records solely for spatial search. Query result identity and safe display metadata remain owned by the consumer or by an explicit adapter contract.

## Tenancy, privacy, and authorization

- Geo primitives do not grant access to a stored location. Identifiers are not authorization tokens.
- Tenant filtering must be enforced by the owning application/repository boundary and must not be optional in multi-tenant adapters.
- Authorization and purpose-of-use checks happen before sensitive location details are returned. A shared query adapter must not become a cross-tenant discovery endpoint.
- Avoid raw coordinates in logs, traces, metric labels, and exception text. Use redacted diagnostics and bounded, low-cardinality metrics.
- Retention, consent, data residency, and external-provider sharing rules belong to the consumer and its integration policy, not to the geometry primitive.

## Adapter contract acceptance criteria

Before shipping a public spatial query port or adapter, require:

1. Named query types and explicit exact-versus-candidate semantics.
2. Validation of finite coordinates, radius, bounds, units, limits, and paging inputs.
3. Contract tests for ordinary bounds, inclusive edges, antimeridian crossing, +180/-180 equivalence, high latitudes, poles, duplicate candidates, empty results, and invalid inputs.
4. Differential tests comparing adapter results with a simple exact reference implementation for generated and adversarial points; the candidate stage must have no false negatives.
5. Tests that enforce deterministic ordering and explicit truncation/failure behavior.
6. Tenant isolation and authorization tests at the application boundary.
7. Architecture checks ensuring the Geo domain has no dependency on persistence, HTTP, CDI/runtime, Mobility, Fleet, Inventory, or a map vendor.
8. Consumer-specific tests proving existing APIs and policy behavior remain unchanged.
9. Reproducible focused reactor verification and CI evidence before marking the milestone complete.

## Deliberately out of scope

- PostGIS/SQL schema, indexes, and query plans.
- Geocoding, address resolution, map matching, route planning, traffic-aware ETA.
- Universal polygon topology or administrative-boundary datasets.
- Consumer service-area eligibility, fleet geofence lifecycle/alerts, driver dispatch, inventory storage rules, or courier coverage policy.
- Automatically migrating Mobility's fixed 0.01-degree driver grid or its legacy key format.
- Automatically converting Inventory's `BigDecimal` coordinates to Geo's `double` representation.

## Recommended implementation sequence

1. Review and approve this semantic contract.
2. Identify a concrete consumer use case and its required predicates, data volume, consistency, tenancy, and latency needs.
3. Add only the minimal provider-neutral query types/port needed by that use case.
4. Implement one adapter with contract tests and compare it against the exact reference predicate.
5. Add another adapter only when an actual deployment or consumer needs it; keep the domain contract independent of both.
