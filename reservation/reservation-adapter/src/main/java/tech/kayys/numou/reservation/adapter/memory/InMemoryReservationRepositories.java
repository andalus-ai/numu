package tech.kayys.numou.reservation.adapter.memory;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.identifier.DomainId;
import tech.kayys.numou.reservation.domain.*;
import tech.kayys.numou.reservation.spi.IdempotentReservationRepository;
import tech.kayys.numou.reservation.spi.LedgerRepository;
import tech.kayys.numou.reservation.spi.ReservationRepository;
import tech.kayys.numou.reservation.spi.WaitlistRepository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * In-memory snapshot repositories for tests and local development.
 * This adapter is not durable and does not provide a cross-repository transaction.
 */
public final class InMemoryReservationRepositories {

    private InMemoryReservationRepositories() { }

    public static class Base<A extends ReservationAggregate<ID>, ID extends DomainId<?>, S> {
        private final ConcurrentHashMap<String, Stored<S>> store = new ConcurrentHashMap<>();
        private final Function<A, S> toSnapshot;
        private final Function<S, A> fromSnapshot;

        public Base(Function<A, S> toSnapshot, Function<S, A> fromSnapshot) {
            this.toSnapshot = Objects.requireNonNull(toSnapshot);
            this.fromSnapshot = Objects.requireNonNull(fromSnapshot);
        }

        private record Stored<S>(long version, S snapshot) { }

        protected Optional<A> findNow(UUID tenantId, ID id) {
            final var stored = store.get(key(tenantId, id));
            if (stored == null) return Optional.empty();
            final var aggregate = fromSnapshot.apply(stored.snapshot());
            aggregate.markStored(stored.version());
            return Optional.of(aggregate);
        }

        public Uni<Optional<A>> find(UUID tenantId, ID id) {
            return Uni.createFrom().item(() -> findNow(tenantId, id));
        }

        protected void saveNow(UUID tenantId, A aggregate) {
            final var storageKey = key(tenantId, aggregate.id());
            final long expected = aggregate.storedVersion();
            store.compute(storageKey, (ignored, current) -> {
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
        }

        public Uni<Void> save(UUID tenantId, A aggregate) {
            return Uni.createFrom().item(() -> {
                saveNow(tenantId, aggregate);
                return null;
            });
        }

        public List<A> allOf(UUID tenantId) {
            final var prefix = tenantId.toString() + ":";
            return store.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(prefix))
                    .map(e -> {
                        final var aggregate = fromSnapshot.apply(e.getValue().snapshot());
                        aggregate.markStored(e.getValue().version());
                        return aggregate;
                    })
                    .toList();
        }

        private String key(UUID tenantId, ID id) {
            return Objects.requireNonNull(tenantId, "tenantId cannot be null") + ":" + id;
        }
    }

    public static class Reservations extends Base<Reservation, ReservationId, Reservation.Snapshot>
            implements IdempotentReservationRepository {
        private record IdempotencyRecord(String fingerprint, ReservationId reservationId) { }

        private final Map<String, IdempotencyRecord> idempotencyKeys = new HashMap<>();

        public Reservations() {
            super(Reservation::snapshot, Reservation::restore);
        }

        @Override
        public Uni<Optional<Reservation>> findByIdempotencyKey(UUID tenantId, String idempotencyKey) {
            Objects.requireNonNull(tenantId, "tenantId cannot be null");
            requireKey(idempotencyKey);
            return Uni.createFrom().item(() -> {
                synchronized (idempotencyKeys) {
                    final var record = idempotencyKeys.get(key(tenantId, idempotencyKey));
                    return record == null ? Optional.<Reservation>empty()
                            : findNow(tenantId, record.reservationId());
                }
            });
        }

        @Override
        public Uni<IdempotentSaveResult> saveIdempotently(UUID tenantId, String idempotencyKey,
                                                          String requestFingerprint, Reservation candidate) {
            Objects.requireNonNull(tenantId, "tenantId cannot be null");
            requireKey(idempotencyKey);
            Objects.requireNonNull(requestFingerprint, "requestFingerprint cannot be null");
            Objects.requireNonNull(candidate, "candidate cannot be null");
            if (requestFingerprint.isBlank()) throw new IllegalArgumentException("requestFingerprint cannot be blank");
            return Uni.createFrom().item(() -> {
                synchronized (idempotencyKeys) {
                    final var storageKey = key(tenantId, idempotencyKey);
                    final var existing = idempotencyKeys.get(storageKey);
                    if (existing != null) {
                        if (!existing.fingerprint().equals(requestFingerprint)) {
                            throw new IdempotencyKeyConflictException(
                                    "Idempotency key was already used for a different reservation request");
                        }
                        final var canonical = findNow(tenantId, existing.reservationId())
                                .orElseThrow(() -> new IllegalStateException(
                                        "Idempotency record references a missing reservation"));
                        return new IdempotentSaveResult(canonical, false);
                    }
                    saveNow(tenantId, candidate);
                    idempotencyKeys.put(storageKey, new IdempotencyRecord(requestFingerprint, candidate.id()));
                    return new IdempotentSaveResult(candidate, true);
                }
            });
        }

        @Override
        public Uni<List<Reservation>> findStartingBetween(UUID tenantId, FacilityId facilityId,
                                                           Instant from, Instant to) {
            return Uni.createFrom().item(() ->
                    allOf(tenantId).stream()
                            .filter(r -> r.facilityId().equals(facilityId))
                            .filter(r -> !r.startsAt().isBefore(from) && r.startsAt().isBefore(to))
                            .sorted(Comparator.comparing(Reservation::startsAt))
                            .toList());
        }

        private static String key(UUID tenantId, String idempotencyKey) {
            return tenantId + ":" + idempotencyKey;
        }

        private static void requireKey(String idempotencyKey) {
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("idempotencyKey cannot be blank");
            }
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
                            .filter(entry -> entry.facilityId().equals(facilityId))
                            .filter(entry -> entry.status() == WaitlistEntry.Status.WAITING
                                    || entry.status() == WaitlistEntry.Status.NOTIFIED)
                            .sorted(Comparator.comparing(WaitlistEntry::joinedAt))
                            .toList());
        }
    }

    public static class InMemoryReservationRepository extends Reservations { }
    public static class InMemoryLedgerRepository extends Ledgers { }
    public static class InMemoryWaitlistRepository extends Waitlists { }
}
