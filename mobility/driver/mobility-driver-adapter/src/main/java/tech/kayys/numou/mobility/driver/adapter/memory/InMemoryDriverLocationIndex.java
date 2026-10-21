package tech.kayys.numou.mobility.driver.adapter.memory;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.mobility.driver.spi.DriverLocationIndex;
import tech.kayys.numou.mobility.kernel.domain.DriverId;
import tech.kayys.numou.mobility.kernel.domain.VehicleType;
import tech.kayys.numou.mobility.kernel.geo.GeoCell;
import tech.kayys.numou.mobility.kernel.geo.LatLng;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Grid-indexed in-memory driver locations (tests / dev profile). Same semantics as the Postgres adapter. */
public class InMemoryDriverLocationIndex implements DriverLocationIndex {

    private record Key(UUID tenant, DriverId driver) { }

    private record CellKey(UUID tenant, String cell) { }

    private final Map<Key, Fix> fixes = new HashMap<>();
    private final Map<CellKey, Set<DriverId>> cells = new HashMap<>();

    @Override
    public synchronized Uni<Void> upsert(UUID tenantId, Fix fix) {
        return Uni.createFrom().item(() -> {
            final var key = new Key(tenantId, fix.driverId());
            final var old = fixes.put(key, fix);
            if (old != null) {
                unindex(tenantId, old);
            }
            cells.computeIfAbsent(new CellKey(tenantId, GeoCell.of(fix.at()).key()), c -> new HashSet<>()).add(fix.driverId());
            return null;
        });
    }

    @Override
    public synchronized Uni<Void> setAvailable(UUID tenantId, DriverId driverId, boolean available) {
        return Uni.createFrom().item(() -> {
            final var key = new Key(tenantId, driverId);
            final var f = fixes.get(key);
            if (f != null) {
                fixes.put(key, new Fix(f.driverId(), f.vehicle(), f.at(), f.headingDegrees(), f.speedKmh(), f.recordedAt(), available));
            }
            return null;
        });
    }

    @Override
    public synchronized Uni<Void> remove(UUID tenantId, DriverId driverId) {
        return Uni.createFrom().item(() -> {
            final var old = fixes.remove(new Key(tenantId, driverId));
            if (old != null) {
                unindex(tenantId, old);
            }
            return null;
        });
    }

    @Override
    public synchronized Uni<Optional<Fix>> last(UUID tenantId, DriverId driverId) {
        return Uni.createFrom().item(() -> Optional.ofNullable(fixes.get(new Key(tenantId, driverId))));
    }

    @Override
    public synchronized Uni<List<Nearby>> nearby(UUID tenantId, LatLng center, double radiusMeters, VehicleType vehicle, Instant freshSince, int limit) {
        return Uni.createFrom().item(() -> {
            final var out = new java.util.ArrayList<Nearby>();
            for (final var cell : GeoCell.around(center, radiusMeters)) {
                for (final var id : cells.getOrDefault(new CellKey(tenantId, cell.key()), Set.of())) {
                    final var f = fixes.get(new Key(tenantId, id));
                    if (f == null || !f.available() || f.vehicle() != vehicle || f.recordedAt().isBefore(freshSince)) continue;
                    final double d = center.distanceTo(f.at());
                    if (d <= radiusMeters) out.add(new Nearby(f, d));
                }
            }
            out.sort(Comparator.comparingDouble(Nearby::distanceMeters).thenComparing(n -> n.fix().driverId().value()));
            return List.copyOf(out.subList(0, Math.min(limit, out.size())));
        });
    }

    private void unindex(UUID tenant, Fix f) {
        final var ck = new CellKey(tenant, GeoCell.of(f.at()).key());
        final var set = cells.get(ck);
        if (set != null) {
            set.remove(f.driverId());
            if (set.isEmpty()) cells.remove(ck);
        }
    }
}
