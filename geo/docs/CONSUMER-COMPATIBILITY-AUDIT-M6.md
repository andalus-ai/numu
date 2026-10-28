# Geo M6: consumer coordinate compatibility audit

**Status:** Initial compatibility guardrails added; cross-reactor CI still required.

## Findings

- `numou-geo-domain` represents WGS84 coordinates as finite `double` values in decimal degrees and validates latitude/longitude ranges.
- Mobility's `LatLng` is an established compatibility facade. Its conversion to/from Geo is explicit, does not intentionally round coordinates, and has round-trip, edge, antimeridian-distance, grid-key, and polyline regression tests in `LatLngGeoCompatibilityTest`.
- Inventory's `tech.kayys.numou.inventory.domain.location.GeoPoint` uses `BigDecimal`. It validates the same numeric WGS84 ranges, but its decimal representation can retain more precision and scale information than a binary `double`.
- Therefore this milestone does **not** add an automatic Inventory-to-Geo conversion or migrate Inventory's public contract. Such a conversion would be a potentially lossy boundary and must be explicit at the consumer that needs spatial calculations.
- Fleet geofence bounds and Geo antimeridian bounds are separate contracts. A rectangular Geo bounds check does not replace Fleet's distance policy or a database-backed spatial query.

## Guardrails added

Inventory domain tests now cover:
- exact preservation of high-precision decimal coordinate values by the existing value object;
- accepted WGS84 edge coordinates;
- null coordinate rejection; and
- out-of-range decimal coordinates.

These tests preserve the current BigDecimal model and make its precision expectations visible without forcing a new dependency from Inventory onto Geo.

## Next acceptance criteria

1. Run the Geo reactor and Mobility kernel/consumer reactor using the repository's documented Maven reactor roots and confirm the Geo artifact is installed before consumer tests.
2. Run Inventory domain tests independently.
3. Verify Fleet geofence behavior with negative-only/mixed-sign bounds and antimeridian-adjacent cases.
4. Before adding a shared spatial-query adapter, define query semantics for longitude wrap, polar coverage, precision, candidate filtering, and index ownership. Geo grid buckets are candidate generators, not exact radius predicates.
5. If an Inventory consumer needs Geo distance/bounds, add an explicit adapter at the application/integration boundary and define its precision policy; do not silently replace `BigDecimal` persistence values with `double`.

## Verification status

This change adds regression tests and documentation only. No test execution is claimed here; CI must validate the relevant reactor builds and tests.
