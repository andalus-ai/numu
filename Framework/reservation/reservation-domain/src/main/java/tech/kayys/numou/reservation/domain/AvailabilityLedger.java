package tech.kayys.syirkah.reservation.domain;

import tech.kayys.syirkah.reservation.domain.event.ResourcesAssigned;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages resource capacity and concurrent time-slot allocations for one facility on one calendar day.
 *
 * <p>Enforces the core invariant that bookable resources are never double-booked in overlapping intervals.
 */
public final class AvailabilityLedger extends ReservationAggregate<LedgerId> {

    private static final long serialVersionUID = 1L;
    private static final int MAX_COMBINED_RESOURCES = 3;

    public record Allocation(ReservationId reservationId, Set<ResourceId> resourceIds, TimeRange blocked) {
        // Compatibility accessor
        public Set<ResourceId> tableIds() {
            return resourceIds;
        }
    }

    public record Snapshot(
            UUID ledgerId,
            UUID facilityId,
            LocalDate day,
            List<AllocationSnapshot> allocations,
            Instant createdAt,
            Instant updatedAt,
            int version
    ) { }

    public record AllocationSnapshot(UUID reservationId, List<UUID> resourceIds, Instant from, Instant to) { }

    private final FacilityId facilityId;
    private final LocalDate day;
    private final List<Allocation> allocations = new ArrayList<>();

    private AvailabilityLedger(FacilityId facilityId, LocalDate day, Instant now) {
        super(LedgerId.forFacilityDate(facilityId, day), now);
        this.facilityId = Objects.requireNonNull(facilityId, "facilityId cannot be null");
        this.day = Objects.requireNonNull(day, "day cannot be null");
    }

    public static AvailabilityLedger open(FacilityId facilityId, LocalDate day, Instant now) {
        return new AvailabilityLedger(facilityId, day, now);
    }

    public FacilityId facilityId() {
        return facilityId;
    }

    public LocalDate day() {
        return day;
    }

    public List<Allocation> allocations() {
        return List.copyOf(allocations);
    }

    public Optional<Allocation> allocationOf(ReservationId reservationId) {
        return allocations.stream().filter(a -> a.reservationId().equals(reservationId)).findFirst();
    }

    /**
     * Allocates optimal resources for a reservation. Idempotent per reservation ID.
     */
    public Allocation allocate(ReservationId reservationId, PartySize party, Instant start,
                              BookingRules rules, List<BookableResource> resources, Instant now) {
        final var existing = allocationOf(reservationId);
        if (existing.isPresent()) {
            return existing.get();
        }
        requireSameDay(start, rules);
        final var range = blockedRange(party, start, rules);
        final var fit = bestFit(party, range, resources)
                .orElseThrow(() -> new NoAvailabilityException(
                        "No resource available for " + party.value() + " capacity at " + start));
        final var allocation = new Allocation(reservationId, fit, range);
        allocations.add(allocation);
        touch(now);
        raise(ResourcesAssigned.at(now, reservationId.value(), facilityId.value(),
                fit.stream().map(ResourceId::value).sorted().toList()));
        return allocation;
    }

    /** Dry run: could this party be accommodated at {@code start}? */
    public boolean canAccommodate(PartySize party, Instant start, BookingRules rules, List<BookableResource> resources) {
        final var local = start.atZone(rules.zone()).toLocalDate();
        if (!local.equals(day)) {
            return false;
        }
        return bestFit(party, blockedRange(party, start, rules), resources).isPresent();
    }

    public void release(ReservationId reservationId, Instant now) {
        if (allocations.removeIf(a -> a.reservationId().equals(reservationId))) {
            touch(now);
        }
    }

    private void requireSameDay(Instant start, BookingRules rules) {
        final var bookingDay = start.atZone(rules.zone()).toLocalDate();
        if (!bookingDay.equals(day)) {
            throw new IllegalArgumentException("Booking is on " + bookingDay + ", this ledger is for " + day);
        }
    }

    private TimeRange blockedRange(PartySize party, Instant start, BookingRules rules) {
        return TimeRange.of(start, rules.turnTimeFor(party).plus(rules.cleanupBuffer()));
    }

    private Optional<Set<ResourceId>> bestFit(PartySize party, TimeRange window, List<BookableResource> all) {
        final var busy = allocations.stream()
                .filter(a -> a.blocked().overlaps(window))
                .flatMap(a -> a.resourceIds().stream())
                .collect(Collectors.toSet());

        final var available = all.stream().filter(r -> !busy.contains(r.id())).toList();

        // 1. Single resource best fit
        final var single = available.stream()
                .filter(r -> r.minCapacity() <= party.value() && r.maxCapacity() >= party.value())
                .min(Comparator.comparingInt(BookableResource::maxCapacity));
        if (single.isPresent()) {
            return Optional.of(Set.of(single.get().id()));
        }

        // 2. Combinable multi-resource fit
        return combine(available, party.value());
    }

    private Optional<Set<ResourceId>> combine(List<BookableResource> available, int targetCapacity) {
        List<Set<BookableResource>> candidates = new ArrayList<>();
        for (BookableResource first : available) {
            final var cluster = new LinkedHashSet<BookableResource>();
            cluster.add(first);
            grow(cluster, available, targetCapacity, candidates);
        }
        return candidates.stream()
                .filter(c -> totalCapacity(c) >= targetCapacity)
                .min(Comparator.comparingInt(this::totalCapacity).thenComparingInt(Set::size))
                .map(set -> set.stream().map(BookableResource::id).collect(Collectors.toSet()));
    }

    private void grow(Set<BookableResource> current, List<BookableResource> pool, int target, List<Set<BookableResource>> out) {
        if (totalCapacity(current) >= target) {
            out.add(Set.copyOf(current));
            return;
        }
        if (current.size() >= MAX_COMBINED_RESOURCES) {
            return;
        }
        for (BookableResource candidate : pool) {
            if (!current.contains(candidate) && current.stream().anyMatch(candidate::canJoin)) {
                current.add(candidate);
                grow(current, pool, target, out);
                current.remove(candidate);
            }
        }
    }

    private int totalCapacity(Set<BookableResource> resources) {
        return resources.stream().mapToInt(BookableResource::maxCapacity).sum();
    }

    // Compatibility methods for restaurant contexts
    public boolean canAllocate(PartySize party, Instant start, BookingRules rules, List<BookableResource> resources) {
        return canAccommodate(party, start, rules, resources);
    }

    public List<Instant> availableStarts(PartySize party, BookingRules rules, List<BookableResource> resources, Instant now) {
        final var earliest = now.plus(rules.minLeadTime());
        final var out = new ArrayList<Instant>();
        for (final var window : rules.periodsOn(day.getDayOfWeek())) {
            var cursor = day.atTime(window.opens()).atZone(rules.zone()).toInstant();
            final var last = day.atTime(window.lastBooking()).atZone(rules.zone()).toInstant();
            while (!cursor.isAfter(last)) {
                if (!cursor.isBefore(earliest) && canAccommodate(party, cursor, rules, resources)) {
                    out.add(cursor);
                }
                cursor = cursor.plus(rules.slotInterval());
            }
        }
        return out;
    }

    public FacilityId venueId() {
        return facilityId;
    }

    // ---- Snapshot & Restore ----

    public Snapshot snapshot() {
        final var list = allocations.stream().map(a -> new AllocationSnapshot(
                a.reservationId().value(),
                a.resourceIds().stream().map(ResourceId::value).toList(),
                a.blocked().start(),
                a.blocked().end())).toList();
        return new Snapshot(id().value(), facilityId.value(), day, list, createdAt, updatedAt, version);
    }

    public static AvailabilityLedger restore(Snapshot s) {
        final var ledger = new AvailabilityLedger(FacilityId.of(s.facilityId()), s.day(), s.createdAt());
        if (s.allocations() != null) {
            for (AllocationSnapshot snap : s.allocations()) {
                final var rIds = snap.resourceIds().stream().map(ResourceId::of).collect(Collectors.toSet());
                ledger.allocations.add(new Allocation(ReservationId.of(snap.reservationId()), rIds, TimeRange.of(snap.from(), snap.to())));
            }
        }
        ledger.restoreAudit(s.createdAt(), s.updatedAt(), s.version());
        return ledger;
    }
}
