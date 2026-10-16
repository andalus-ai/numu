package tech.kayys.numou.reservation.adapter.memory;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.identifier.DomainId;
import tech.kayys.numou.reservation.domain.*;
import tech.kayys.numou.reservation.spi.LedgerRepository;
import tech.kayys.numou.reservation.spi.ReservationRepository;
import tech.kayys.numou.reservation.spi.WaitlistRepository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * In-memory snapshot-isolated repositories for testing and local development.
 */
public final class InMemoryReservationRepositories {

    private InMemoryReservationRepositories() { }

    public static class Base<A extends ReservationAggregate<ID>, ID extends DomainId<?>, S> {
        private final ConcurrentHashMap<String, Stored<S>> store = new ConcurrentHashMap<>();
        private final Function<A, S> toSnapshot;
        private final Function<S, A> fromSnapshot;

        public Base(Function<A, S> toSnapshot, Function<S, A> fromSnapshot) {
            this.toSnapshot = toSnapshot;
            this.fromSnapshot = fromSnapshot;
        }

        private record Stored<S>(long version, S snapshot) { }

        public Uni<Optional<A>> find(UUID tenantId, ID id) {
            return Uni.createFrom().item(() -> {
                final var s = store.get(key(tenantId, id));
                if (s == null) return Optional.empty();
                final var aggregate = fromSnapshot.apply(s.snapshot());
                aggregate.markStored(s.version());
                return Optional.of(aggregate);
            });
        }

        public Uni<Void> save(UUID tenantId, A aggregate) {
            return Uni.createFrom().item(() -> {
                final var k = key(tenantId, aggregate.id());
                final long expected = aggregate.storedVersion();
                store.compute(k, (key, current) -> {
                    if (expected == 0 && current != null) {
                        throw new IllegalStateException("Already exists: " + aggregate.id());
                    }
                    if (expected != 0 && (current == null || current.version() != expected)) {
                        throw new IllegalStateException("Concurrent modification on: " + aggregate.id());
                    }
                    final long nextVersion = expected + 1;
                    aggregate.markStored(nextVersion);
                    return new Stored<>(nextVersion, toSnapshot.apply(aggregate));
                });
                return null;
            });
        }

        public List<A> allOf(UUID tenantId) {
            final var prefix = tenantId.toString() + ":";
            return store.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(prefix))
                    .map(e -> {
                        final var agg = fromSnapshot.apply(e.getValue().snapshot());
                        agg.markStored(e.getValue().version());
                        return agg;
                    })
                    .toList();
        }

        private String key(UUID tenantId, ID id) {
            return tenantId + ":" + id;
        }
    }

    public static class Reservations extends Base<Reservation, ReservationId, Reservation.Snapshot>
            implements ReservationRepository {
        public Reservations() {
            super(Reservation::snapshot, Reservation::restore);
        }

        @Override
        public Uni<List<Reservation>> findStartingBetween(UUID tenantId, FacilityId facilityId, Instant from, Instant to) {
            return Uni.createFrom().item(() ->
                    allOf(tenantId).stream()
                            .filter(r -> r.facilityId().equals(facilityId))
                            .filter(r -> !r.startsAt().isBefore(from) && r.startsAt().isBefore(to))
                            .sorted(Comparator.comparing(Reservation::startsAt))
                            .toList()
            );
        }
    }

    public static class Ledgers extends Base<AvailabilityLedger, LedgerId, AvailabilityLedger.Snapshot>
            implements LedgerRepository {
        public Ledgers() {
            super(AvailabilityLedger::snapshot, AvailabilityLedger::restore);
        }
    }

    public static class Waitlists extends Base<WaitlistEntry, WaitlistEntryId, WaitlistEntry.Snapshot>
            implements WaitlistRepository {
        public Waitlists() {
            super(WaitlistEntry::snapshot, WaitlistEntry::restore);
        }

        @Override
        public Uni<List<WaitlistEntry>> findOpen(UUID tenantId, FacilityId facilityId) {
            return Uni.createFrom().item(() ->
                    allOf(tenantId).stream()
                            .filter(e -> e.facilityId().equals(facilityId))
                            .filter(e -> e.status() == WaitlistEntry.Status.WAITING || e.status() == WaitlistEntry.Status.NOTIFIED)
                            .sorted(Comparator.comparing(WaitlistEntry::joinedAt))
                            .toList()
            );
        }
    }

    public static class InMemoryReservationRepository extends Reservations { }
    public static class InMemoryLedgerRepository extends Ledgers { }
    public static class InMemoryWaitlistRepository extends Waitlists { }
}
