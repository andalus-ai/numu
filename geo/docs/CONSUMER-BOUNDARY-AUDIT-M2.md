# Geo consumer and boundary audit (M2)

- **Status:** Repository inspection completed; no consumer migration approved yet
- **Repository baseline inspected:** `main`, 2026-10-27
- **Scope:** Existing geographic concepts found in Mobility, Fleet, Inventory and Asset, plus the new `geo/geo-domain` M1 artifact

## Findings

| Existing location | Observed semantics | Recommendation |
|---|---|---|
| `mobility/mobility-kernel/.../geo/LatLng.java` | Validated coordinate record with intentionally rounded string output and a `distanceTo` convenience method | Strong candidate for a compatibility-preserving adapter/conversion to GeoPoint; do not replace public signatures in-place without checking all callers |
| `mobility/mobility-kernel/.../geo/GeoMath.java` | Haversine distance, path length, small-offset math, point-to-segment distance and speed calculation | Share only stable general geographic operations. Movement plausibility and vehicle-speed policy remain Mobility-owned; local planar point-to-segment approximation needs explicit accuracy limits before becoming a public Geo contract |
| `mobility/mobility-kernel/.../geo/GeoCell.java` | Fixed 0.01-degree grid cells used for driver lookup and surge zones | Keep as Mobility indexing policy unless another consumer demonstrates identical grid semantics. It is not a universal geographic grid |
| `mobility/mobility-kernel/.../geo/HaversineRouting.java` | RoutingPort fallback that multiplies straight-line distance by a detour factor and estimates time using vehicle-type speeds | Keep routing estimate and fare-safety assumptions outside Geo. Its output is an estimate, not a real road route |
| `mobility/mobility-kernel/.../geo/Polyline.java` | Encoded GPS path format | Candidate for a future path-encoding adapter only if multiple consumers need the same format; preserve format and malformed-input behavior under tests |
| `mobility/mobility-kernel/.../geo/ServiceArea.java` | Circular service coverage with a radius and business rule to reject pickups outside configured areas | Geo may eventually supply a circle/bounds primitive; coverage acceptance and operating policy belong to the consuming capability |
| `logistics/numou-fleet/.../domain/model/Geofence.java` | Fleet aggregate includes geofence lifecycle, vehicle/driver membership, alerts/events, circle/polygon data and bounds | Fleet must continue to own geofence lifecycle, fleet membership and alerting. Extract only geometry/value-object logic after inspecting GeofencePoint, alert behavior, persistence mappings and tests |
| `supply/inventory/inventory-domain/.../location/GeoPoint.java` | BigDecimal latitude/longitude value object with range checks | Do not assume it is equivalent to double-based GeoPoint for all persistence/precision contracts. Add explicit conversions and precision tests before any migration |
| `asset/numou-asset-domain/.../location/AssetLocation.java` | External location-master identifier and human-readable name, not geographic coordinates | Not a GeoPoint duplicate. Keep it as an asset-to-location reference; a future optional coordinate reference can be added only through an explicit contract |

## Proposed boundary

### Geo domain owns

- WGS84 coordinate validation and documented coordinate units/reference system.
- Deterministic spherical distance and simple geographic bounds.
- Future generic spatial primitives only when semantics are shared and tests define boundary behavior.

### Consumers retain ownership

- Mobility: GPS plausibility, driver grid/index, vehicle-dependent ETA fallback, fare estimates and ride path lifecycle.
- Fleet: geofence aggregate lifecycle, target vehicles/drivers, alerts, events and authorization.
- Inventory: storage-location lifecycle and its precision/persistence compatibility.
- Asset: external location-master references and asset-location relationships.
- Transport: stop ordering, journey segments and segment capacity.

### Not yet justified

No evidence from this focused audit alone justifies creating all of the following now: a universal Location aggregate, a Geo service, a routing service, a geocoding SPI, a generic polygon engine, or an administrative-boundary persistence schema. These should be introduced only after concrete consumers, semantics, provider obligations, tenancy and data licensing are specified.

## Migration sequence and acceptance criteria

1. Inventory all callers/tests of Mobility `LatLng`, `GeoMath`, `ServiceArea` and Fleet `GeofencePoint`; capture current behavior before changing public signatures.
2. Add explicit conversion tests between Mobility `LatLng`, Inventory `GeoPoint` and Geo `GeoPoint`, including coordinate edges and round-trip precision expectations.
3. Extract only shared pure math or primitives while preserving existing APIs through adapters/deprecation where appropriate.
4. Add architecture tests that prevent Geo domain from depending on Mobility, Fleet, Inventory, persistence or runtime packages.
5. Only then design application/SPI contracts for geocoding, spatial queries or geofences. Include tenant context and authorization at application boundaries; do not expose private saved locations based on ID possession alone.
6. Validate the focused Geo reactor and affected consumer reactors independently. A failure in a consumer reactor must be reported separately from Geo domain verification.

## Verification note

The repository CI run observed during this audit failed in the existing Mobility API test phase (`numou-mobility-api` Surefire). This audit did not establish that the failure was caused by Geo; the run's log identifies the failing reactor but the individual Surefire report still needs inspection before assigning root cause. The separate module-topology/parent-resolution job passed. Geo's own focused Maven verification has not yet been independently confirmed.
