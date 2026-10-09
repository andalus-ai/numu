package tech.kayys.syirkah.restaurant.reservation.domain;

import tech.kayys.syirkah.restaurant.kernel.domain.DiningTableId;
import tech.kayys.syirkah.restaurant.kernel.domain.PartySize;
import tech.kayys.syirkah.restaurant.kernel.domain.ReservationId;
import tech.kayys.syirkah.restaurant.kernel.domain.RestaurantAggregate;
import tech.kayys.syirkah.restaurant.kernel.domain.TimeRange;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.reservation.domain.event.TablesAssigned;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Who sits where, when, for one venue on one calendar day.
 *
 * <p><b>Why a ledger aggregate:</b> "never double-book a table" is an invariant across <em>many</em>
 * reservations, so it needs one consistency boundary. Every booking for (venue, day) goes through this
 * aggregate, and its optimistic version makes two simultaneous bookings for the last table collide at save time:
 * one wins, the other reloads, sees the table taken, and gets {@link NoAvailabilityException}.
 *
 * <p>Allocation is best-fit: the single table with the least spare seats; failing that the smallest
 * combination of up to three joinable tables. Each allocation blocks the tables for
 * {@code turn time + cleanup buffer}.
 */
public final class AvailabilityLedger extends RestaurantAggregate<LedgerId> {

    private static final long serialVersionUID = 1L;
    private static final int MAX_COMBINED_TABLES = 3;

    /** @param blocked the interval during which these tables are unavailable to others */
    public record Allocation(ReservationId reservationId, Set<DiningTableId> tableIds, TimeRange blocked) { }

    public record Snapshot(UUID ledgerId, UUID venueId, LocalDate day, List<AllocationSnapshot> allocations,
                           Instant createdAt, Instant updatedAt, int version) { }

    public record AllocationSnapshot(UUID reservationId, List<UUID> tableIds, Instant from, Instant to) { }

    private final VenueId venueId;
    private final LocalDate day;
    private final List<Allocation> allocations = new ArrayList<>();

    private AvailabilityLedger(VenueId venueId, LocalDate day, Instant now) {
        super(LedgerId.forVenueDay(venueId, day), now);
        this.venueId = venueId;
        this.day = day;
    }

    public static AvailabilityLedger open(VenueId venueId, LocalDate day, Instant now) {
        return new AvailabilityLedger(venueId, day, now);
    }

    public VenueId venueId() {
        return venueId;
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
     * Reserves tables for a booking. Idempotent per reservation: re-allocating an already allocated
     * reservation returns the existing allocation (a retried command must not take a second table).
     */
    public Allocation allocate(ReservationId reservationId, PartySize party, Instant start,
                               BookingRules rules, List<TableInfo> tables, Instant now) {
        final var existing = allocationOf(reservationId);
        if (existing.isPresent()) {
            return existing.get();
        }
        requireSameDay(start, rules);
        final var range = blockedRange(party, start, rules);
        final var fit = bestFit(party, range, tables)
                .orElseThrow(() -> new NoAvailabilityException(
                        "No table available for " + party.value() + " guests at " + start));
        final var allocation = new Allocation(reservationId, fit, range);
        allocations.add(allocation);
        touch(now);
        raise(TablesAssigned.at(now, reservationId.value(), venueId.value(),
                fit.stream().map(DiningTableId::value).sorted().toList()));
        return allocation;
    }

    /** Dry run: could this party be seated at {@code start}? Does not change the ledger. */
    public boolean canAllocate(PartySize party, Instant start, BookingRules rules, List<TableInfo> tables) {
        requireSameDay(start, rules);
        return bestFit(party, blockedRange(party, start, rules), tables).isPresent();
    }

    /** Frees a booking's tables. Returns false if the reservation held none here. */
    public boolean release(ReservationId reservationId, Instant now) {
        final boolean removed = allocations.removeIf(a -> a.reservationId().equals(reservationId));
        if (removed) {
            touch(now);
        }
        return removed;
    }

    /** Start times (aligned to the slot interval inside service periods) at which the party can be seated. */
    public List<Instant> availableStarts(PartySize party, BookingRules rules, List<TableInfo> tables, Instant now) {
        final var earliest = now.plus(rules.minLeadTime());
        final var out = new ArrayList<Instant>();
        for (final var period : rules.periodsOn(day.getDayOfWeek())) {
            var cursor = day.atTime(period.opens()).atZone(rules.zone()).toInstant();
            final var last = day.atTime(period.lastSeating()).atZone(rules.zone()).toInstant();
            while (!cursor.isAfter(last)) {
                if (!cursor.isBefore(earliest) && canAllocate(party, cursor, rules, tables)) {
                    out.add(cursor);
                }
                cursor = cursor.plus(rules.slotInterval());
            }
        }
        return out;
    }

    // ---- allocation algorithm ----

    private TimeRange blockedRange(PartySize party, Instant start, BookingRules rules) {
        return TimeRange.of(start, rules.turnTimeFor(party).plus(rules.cleanupBuffer()));
    }

    private boolean isFree(DiningTableId table, TimeRange range) {
        return allocations.stream().noneMatch(a -> a.tableIds().contains(table) && a.blocked().overlaps(range));
    }

    private Optional<Set<DiningTableId>> bestFit(PartySize party, TimeRange range, List<TableInfo> tables) {
        final var free = tables.stream()
                .filter(t -> isFree(t.id(), range))
                .sorted(Comparator.comparing(TableInfo::label))
                .toList();
        final int p = party.value();

        final var single = free.stream()
                .filter(t -> t.minCovers() <= p && p <= t.maxCovers())
                .min(Comparator.comparingInt((TableInfo t) -> t.maxCovers() - p).thenComparing(TableInfo::label));
        if (single.isPresent()) {
            return Optional.of(Set.of(single.get().id()));
        }
        return bestCombination(free, p);
    }

    private Optional<Set<DiningTableId>> bestCombination(List<TableInfo> free, int party) {
        List<TableInfo> best = null;
        for (int size = 2; size <= MAX_COMBINED_TABLES && best == null; size++) {
            for (final var combo : combinations(free, size)) {
                final int capacity = combo.stream().mapToInt(TableInfo::maxCovers).sum();
                final int largestSingle = combo.stream().mapToInt(TableInfo::maxCovers).max().orElse(0);
                // joining must be necessary (no member fits alone), big enough, and physically possible
                if (capacity < party || party <= largestSingle || !connected(combo)) {
                    continue;
                }
                if (best == null || capacity < best.stream().mapToInt(TableInfo::maxCovers).sum()) {
                    best = combo; // first (label-ordered) combo wins ties => deterministic
                }
            }
        }
        return best == null
                ? Optional.empty()
                : Optional.of(best.stream().map(TableInfo::id).collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    private static boolean connected(List<TableInfo> combo) {
        final var reached = new ArrayList<TableInfo>();
        reached.add(combo.get(0));
        boolean grew = true;
        while (grew) {
            grew = false;
            for (final var t : combo) {
                if (!reached.contains(t) && reached.stream().anyMatch(r -> r.canJoin(t))) {
                    reached.add(t);
                    grew = true;
                }
            }
        }
        return reached.size() == combo.size();
    }

    private static List<List<TableInfo>> combinations(List<TableInfo> items, int k) {
        final var result = new ArrayList<List<TableInfo>>();
        build(items, k, 0, new ArrayList<>(), result);
        return result;
    }

    private static void build(List<TableInfo> items, int k, int from, List<TableInfo> acc, List<List<TableInfo>> out) {
        if (acc.size() == k) {
            out.add(List.copyOf(acc));
            return;
        }
        for (int i = from; i < items.size(); i++) {
            acc.add(items.get(i));
            build(items, k, i + 1, acc, out);
            acc.remove(acc.size() - 1);
        }
    }

    private void requireSameDay(Instant start, BookingRules rules) {
        if (!start.atZone(rules.zone()).toLocalDate().equals(day)) {
            throw new IllegalArgumentException("Start " + start + " is not on ledger day " + day);
        }
    }

    // ---- persistence ----

    public Snapshot snapshot() {
        return new Snapshot(id().value(), venueId.value(), day,
                allocations.stream().map(a -> new AllocationSnapshot(a.reservationId().value(),
                        a.tableIds().stream().map(DiningTableId::value).sorted().toList(),
                        a.blocked().start(), a.blocked().end())).toList(),
                createdAt, updatedAt, version);
    }

    public static AvailabilityLedger restore(Snapshot s) {
        final var ledger = new AvailabilityLedger(VenueId.of(s.venueId()), s.day(), s.createdAt());
        s.allocations().forEach(a -> ledger.allocations.add(new Allocation(ReservationId.of(a.reservationId()),
                a.tableIds().stream().map(DiningTableId::of).collect(Collectors.toCollection(LinkedHashSet::new)),
                new TimeRange(a.from(), a.to()))));
        ledger.restoreAudit(s.createdAt(), s.updatedAt(), s.version());
        return ledger;
    }
}
