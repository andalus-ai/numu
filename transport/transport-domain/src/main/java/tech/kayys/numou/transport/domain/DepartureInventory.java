package tech.kayys.numou.transport.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Capacity policy for one published departure.
 *
 * <p>This aggregate is immutable: callers persist the returned instance as a single unit.
 * A persistence adapter must use a transaction and optimistic version check (or equivalent
 * serialization) when saving it; this class alone does not provide cross-process locking.</p>
 *
 * <p>Capacity is shared by every journey segment. A hold consumes seats only on the legs it
 * traverses, not on unrelated legs of the same route.</p>
 */
public final class DepartureInventory {
    private final String tenantId;
    private final UUID departureId;
    private final int capacity;
    private final int stopCount;
    private final int version;
    private final List<PassengerHold> holds;

    private DepartureInventory(String tenantId, UUID departureId, int capacity, int stopCount,
                               int version, List<PassengerHold> holds) {
        this.tenantId = requireText(tenantId, "tenantId");
        this.departureId = Objects.requireNonNull(departureId, "departureId");
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        if (stopCount < 2) throw new IllegalArgumentException("stopCount must be at least two");
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        this.capacity = capacity;
        this.stopCount = stopCount;
        this.version = version;
        this.holds = List.copyOf(Objects.requireNonNull(holds, "holds"));
    }

    public static DepartureInventory open(ScheduledDeparture departure) {
        Objects.requireNonNull(departure, "departure");
        if (departure.status() != ScheduledDepartureStatus.PUBLISHED) {
            throw new IllegalArgumentException("inventory can only be opened for a published departure");
        }
        return new DepartureInventory(departure.tenantId(), departure.id(), departure.passengerCapacity(),
                departure.stopTimes().size(), 0, List.of());
    }

    /**
     * Reconstitutes a persisted snapshot after validating its identity and hold keys.
     * Infrastructure must still use optimistic concurrency when saving the snapshot.
     */
    public static DepartureInventory reconstitute(String tenantId, UUID departureId, int capacity,
                                                  int stopCount, int version, List<PassengerHold> holds) {
        Objects.requireNonNull(holds, "holds");
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (PassengerHold hold : holds) {
            Objects.requireNonNull(hold, "hold");
            if (hold.toSequence() > stopCount) {
                throw new IllegalArgumentException("persisted hold segment exceeds inventory stop count");
            }
            if (!ids.add(hold.id())) {
                throw new IllegalArgumentException("duplicate passenger hold id in persisted inventory");
            }
            if (!keys.add(hold.idempotencyKey())) {
                throw new IllegalArgumentException("duplicate idempotency key in persisted inventory");
            }
        }
        List<PassengerHold> ordered = new ArrayList<>(holds);
        ordered.sort(Comparator.comparing(PassengerHold::createdAt).thenComparing(PassengerHold::id));
        return new DepartureInventory(tenantId, departureId, capacity, stopCount, version, ordered);
    }

    /**
     * Attempts a hold, returning the updated aggregate and the canonical hold.
     * Replaying the same key/fingerprint returns the original hold without allocating twice.
     */
    public HoldResult hold(UUID holdId, String idempotencyKey, String requestFingerprint,
                           int fromSequence, int toSequence, int passengerCount,
                           Instant expiresAt, Instant now) {
        Objects.requireNonNull(holdId, "holdId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(now, "now");
        String key = requireText(idempotencyKey, "idempotencyKey");
        String fingerprint = requireText(requestFingerprint, "requestFingerprint");
        validateSegment(fromSequence, toSequence);
        if (passengerCount < 1) throw new IllegalArgumentException("passengerCount must be positive");
        if (!expiresAt.isAfter(now)) throw new IllegalArgumentException("expiresAt must be after now");

        DepartureInventory current = expireDue(now);
        Optional<PassengerHold> replay = current.holds.stream()
                .filter(h -> h.idempotencyKey().equals(key)).findFirst();
        if (replay.isPresent()) {
            PassengerHold existing = replay.get();
            if (!existing.requestFingerprint().equals(fingerprint)) {
                throw new IdempotencyConflictException("idempotency key was already used for a different hold request");
            }
            return new HoldResult(current, existing, false);
        }
        if (current.holds.stream().anyMatch(existing -> existing.id().equals(holdId))) {
            throw new IllegalArgumentException("passenger hold id is already in use");
        }

        int occupied = current.maxOccupiedAcrossSegment(fromSequence, toSequence, now);
        if (occupied + passengerCount > capacity) {
            throw new CapacityUnavailableException(capacity, occupied, passengerCount,
                    fromSequence, toSequence);
        }

        PassengerHold created = new PassengerHold(holdId, key, fingerprint, fromSequence, toSequence,
                passengerCount, expiresAt, PassengerHoldStatus.HELD, now, now);
        List<PassengerHold> next = new ArrayList<>(current.holds);
        next.add(created);
        next.sort(Comparator.comparing(PassengerHold::createdAt).thenComparing(PassengerHold::id));
        DepartureInventory updated = current.copy(Math.incrementExact(current.version), next);
        return new HoldResult(updated, created, true);
    }

    public DepartureInventory confirm(UUID holdId, Instant now) {
        Objects.requireNonNull(holdId, "holdId");
        Objects.requireNonNull(now, "now");
        DepartureInventory current = expireDue(now);
        PassengerHold hold = current.findHold(holdId);
        if (hold.status() == PassengerHoldStatus.CONFIRMED) return current;
        if (hold.status() != PassengerHoldStatus.HELD) {
            throw new HoldNotActiveException("only an active hold can be confirmed");
        }
        return current.replace(hold.transition(PassengerHoldStatus.CONFIRMED, now));
    }

    /** Releases capacity without deleting the historical hold record. */
    public DepartureInventory release(UUID holdId, Instant now) {
        Objects.requireNonNull(holdId, "holdId");
        Objects.requireNonNull(now, "now");
        DepartureInventory current = expireDue(now);
        PassengerHold hold = current.findHold(holdId);
        if (hold.status() == PassengerHoldStatus.RELEASED || hold.status() == PassengerHoldStatus.EXPIRED) {
            return current;
        }
        return current.replace(hold.transition(PassengerHoldStatus.RELEASED, now));
    }

    /** Expires due HELD claims. Confirmed claims never expire implicitly. */
    public DepartureInventory expireDue(Instant now) {
        Objects.requireNonNull(now, "now");
        List<PassengerHold> next = new ArrayList<>(holds.size());
        boolean changed = false;
        for (PassengerHold hold : holds) {
            if (hold.status() == PassengerHoldStatus.HELD && !hold.expiresAt().isAfter(now)) {
                next.add(hold.transition(PassengerHoldStatus.EXPIRED, now));
                changed = true;
            } else {
                next.add(hold);
            }
        }
        return changed ? copy(Math.incrementExact(version), next) : this;
    }

    public int remainingCapacity(int fromSequence, int toSequence, Instant now) {
        Objects.requireNonNull(now, "now");
        validateSegment(fromSequence, toSequence);
        DepartureInventory current = expireDue(now);
        int occupied = current.maxOccupiedAcrossSegment(fromSequence, toSequence, now);
        return capacity - occupied;
    }

    public Optional<PassengerHold> holdByIdempotencyKey(String key) {
        String normalized = requireText(key, "idempotencyKey");
        return holds.stream().filter(h -> h.idempotencyKey().equals(normalized)).findFirst();
    }

    private int maxOccupiedAcrossSegment(int from, int to, Instant now) {
        int maximum = 0;
        for (int leg = from; leg < to; leg++) {
            int legStart = leg;
            int occupiedOnLeg = holds.stream()
                    .filter(hold -> hold.overlaps(legStart, legStart + 1))
                    .filter(hold -> hold.consumesCapacityAt(now))
                    .mapToInt(PassengerHold::passengerCount)
                    .sum();
            maximum = Math.max(maximum, occupiedOnLeg);
        }
        return maximum;
    }

    private PassengerHold findHold(UUID id) {
        return holds.stream().filter(h -> h.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown passenger hold: " + id));
    }

    private DepartureInventory replace(PassengerHold replacement) {
        List<PassengerHold> next = new ArrayList<>(holds.size());
        boolean found = false;
        for (PassengerHold hold : holds) {
            if (hold.id().equals(replacement.id())) {
                next.add(replacement);
                found = true;
            } else {
                next.add(hold);
            }
        }
        if (!found) throw new IllegalArgumentException("unknown passenger hold: " + replacement.id());
        return copy(Math.incrementExact(version), next);
    }

    private DepartureInventory copy(int nextVersion, List<PassengerHold> nextHolds) {
        return new DepartureInventory(tenantId, departureId, capacity, stopCount, nextVersion, nextHolds);
    }

    private void validateSegment(int from, int to) {
        if (from < 1 || to <= from || to > stopCount) {
            throw new IllegalArgumentException("journey segment must satisfy 1 <= from < to <= " + stopCount);
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        if ("tenantId".equals(field) && normalized.length() > 128) {
            throw new IllegalArgumentException("tenantId must not exceed 128 characters");
        }
        return normalized;
    }

    public String tenantId() { return tenantId; }
    public UUID departureId() { return departureId; }
    public int capacity() { return capacity; }
    public int stopCount() { return stopCount; }
    public int version() { return version; }
    public List<PassengerHold> holds() { return holds; }

    public record HoldResult(DepartureInventory inventory, PassengerHold hold, boolean created) {
        public HoldResult {
            Objects.requireNonNull(inventory, "inventory");
            Objects.requireNonNull(hold, "hold");
        }
    }

    public static final class CapacityUnavailableException extends RuntimeException {
        private final int capacity;
        private final int occupied;
        private final int requested;
        private final int fromSequence;
        private final int toSequence;

        public CapacityUnavailableException(int capacity, int occupied, int requested, int fromSequence, int toSequence) {
            super("insufficient passenger capacity for segment " + fromSequence + "-" + toSequence
                    + ": capacity=" + capacity + ", occupied=" + occupied + ", requested=" + requested);
            this.capacity = capacity;
            this.occupied = occupied;
            this.requested = requested;
            this.fromSequence = fromSequence;
            this.toSequence = toSequence;
        }

        public int capacity() { return capacity; }
        public int occupied() { return occupied; }
        public int requested() { return requested; }
        public int fromSequence() { return fromSequence; }
        public int toSequence() { return toSequence; }
    }

    public static final class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String message) { super(message); }
    }
}
