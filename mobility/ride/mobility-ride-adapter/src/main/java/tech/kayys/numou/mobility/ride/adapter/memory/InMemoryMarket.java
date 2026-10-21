package tech.kayys.numou.mobility.ride.adapter.memory;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.mobility.kernel.geo.GeoCell;
import tech.kayys.numou.mobility.pricing.Promo;
import tech.kayys.numou.mobility.ride.spi.DemandCounter;
import tech.kayys.numou.mobility.ride.spi.PromoCatalog;
import tech.kayys.numou.mobility.ride.spi.SurgeStateStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory demand counter, surge state and promo catalog (tests / dev profile). */
public final class InMemoryMarket {

    private InMemoryMarket() { }

    public static class Demand implements DemandCounter {
        private record Key(UUID tenant, String cell) { }
        private final Map<Key, List<Instant>> events = new ConcurrentHashMap<>();

        @Override
        public Uni<Void> record(UUID tenantId, GeoCell cell, Instant at) {
            return Uni.createFrom().item(() -> {
                events.computeIfAbsent(new Key(tenantId, cell.key()), k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(at);
                return null;
            });
        }

        @Override
        public Uni<Integer> count(UUID tenantId, Collection<GeoCell> cells, Instant since) {
            return Uni.createFrom().item(() -> {
                int n = 0;
                for (final var c : cells) {
                    for (final var t : events.getOrDefault(new Key(tenantId, c.key()), List.of())) {
                        if (!t.isBefore(since)) n++;
                    }
                }
                return n;
            });
        }
    }

    public static class SurgeState implements SurgeStateStore {
        private final Map<String, State> map = new ConcurrentHashMap<>();

        @Override
        public Uni<Optional<State>> get(UUID tenantId, GeoCell cell) {
            return Uni.createFrom().item(() -> Optional.ofNullable(map.get(tenantId + "/" + cell.key())));
        }

        @Override
        public Uni<Void> put(UUID tenantId, GeoCell cell, State state) {
            return Uni.createFrom().item(() -> { map.put(tenantId + "/" + cell.key(), state); return null; });
        }
    }

    public static class Promos implements PromoCatalog {
        private final Map<String, Promo> promos = new ConcurrentHashMap<>();
        private final Map<String, UUID> redemptions = new ConcurrentHashMap<>();

        public Promos add(UUID tenantId, Promo p) {
            promos.put(tenantId + "/" + p.code(), p);
            return this;
        }

        @Override
        public Uni<Optional<Promo>> find(UUID tenantId, String code, Instant now) {
            return Uni.createFrom().item(() -> Optional.ofNullable(promos.get(tenantId + "/" + code)));
        }

        @Override
        public Uni<Boolean> redeem(UUID tenantId, UUID riderId, String code, UUID rideId) {
            return Uni.createFrom().item(() -> {
                final var prev = redemptions.putIfAbsent(tenantId + "/" + riderId + "/" + code, rideId);
                return prev == null || prev.equals(rideId);
            });
        }

        @Override
        public Uni<Void> refund(UUID tenantId, UUID riderId, String code, UUID rideId) {
            return Uni.createFrom().item(() -> { redemptions.remove(tenantId + "/" + riderId + "/" + code, rideId); return null; });
        }
    }
}
