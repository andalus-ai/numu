package tech.kayys.syirkah.restaurant.kitchen.domain;

import tech.kayys.syirkah.restaurant.kernel.domain.RestaurantAggregate;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.kitchen.domain.event.ItemEightySixed;
import tech.kayys.syirkah.restaurant.kitchen.domain.event.ItemRestored;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The "86 board": dishes the kitchen has run out of. One per venue, owned by the kitchen because that is who
 * knows. An 86 may carry an expiry ("out until tomorrow's delivery") and then lifts itself.
 */
public final class AvailabilityBoard extends RestaurantAggregate<VenueId> {

    private static final long serialVersionUID = 1L;

    public record Entry(UUID menuItemId, String reason, Instant since, Instant until) {
        boolean activeAt(Instant now) {
            return until == null || now.isBefore(until);
        }
    }

    public record Snapshot(UUID venueId, List<Entry> entries, Instant createdAt, Instant updatedAt, int version) { }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    private AvailabilityBoard(VenueId venueId, Instant now) {
        super(venueId, now);
    }

    public static AvailabilityBoard openFor(VenueId venueId, Instant now) {
        return new AvailabilityBoard(venueId, now);
    }

    /** @param until when the dish is expected back; null = until someone restores it */
    public void eightySix(UUID menuItemId, String reason, Instant until, Instant now) {
        if (until != null && !until.isAfter(now)) {
            throw new IllegalArgumentException("until must be in the future");
        }
        entries.put(menuItemId, new Entry(menuItemId, reason == null ? "" : reason, now, until));
        touch(now);
        raise(ItemEightySixed.at(now, id().value(), menuItemId, reason == null ? "" : reason, until));
    }

    public boolean restore(UUID menuItemId, Instant now) {
        if (entries.remove(menuItemId) == null) {
            return false;
        }
        touch(now);
        raise(ItemRestored.at(now, id().value(), menuItemId));
        return true;
    }

    public boolean isAvailable(UUID menuItemId, Instant now) {
        final var e = entries.get(menuItemId);
        return e == null || !e.activeAt(now);
    }

    public List<Entry> unavailable(Instant now) {
        return entries.values().stream().filter(e -> e.activeAt(now)).sorted(Comparator.comparing(Entry::since)).toList();
    }

    public Snapshot snapshot() {
        return new Snapshot(id().value(), new ArrayList<>(entries.values()), createdAt, updatedAt, version);
    }

    public static AvailabilityBoard restore(Snapshot s) {
        final var b = new AvailabilityBoard(VenueId.of(s.venueId()), s.createdAt());
        s.entries().forEach(e -> b.entries.put(e.menuItemId(), e));
        b.restoreAudit(s.createdAt(), s.updatedAt(), s.version());
        return b;
    }
}
