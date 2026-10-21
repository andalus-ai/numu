package tech.kayys.syirkah.restaurant.venue.domain;

import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.restaurant.kernel.domain.DiningTableId;
import tech.kayys.syirkah.restaurant.kernel.domain.OrderId;
import tech.kayys.syirkah.aggregate.domain.VersionedAggregate;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.venue.domain.event.TableBlocked;
import tech.kayys.syirkah.restaurant.venue.domain.event.TableCleaned;
import tech.kayys.syirkah.restaurant.venue.domain.event.TableReleased;
import tech.kayys.syirkah.restaurant.venue.domain.event.TableSeated;
import tech.kayys.syirkah.restaurant.venue.domain.event.TableUnblocked;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A physical table and its live state: AVAILABLE -> OCCUPIED -> DIRTY -> AVAILABLE, or BLOCKED (broken,
 * private event). Its own aggregate (not a child of Venue) so seating one table never contends with another.
 */
public final class DiningTable extends VersionedAggregate<DiningTableId> {

    private static final long serialVersionUID = 1L;

    public record Snapshot(UUID id, UUID venueId, String label, String section, int minCovers, int maxCovers, Set<UUID> combinableWith,
                           String status, UUID currentOrderId, String blockReason, Instant createdAt, Instant updatedAt, int version) { }

    private final VenueId venueId;
    private final String label;
    private final String section;
    private final int minCovers;
    private final int maxCovers;
    private final Set<DiningTableId> combinableWith = new LinkedHashSet<>();
    private TableStatus status = TableStatus.AVAILABLE;
    private OrderId currentOrderId;
    private String blockReason;

    private DiningTable(DiningTableId id, VenueId venueId, String label, String section, int minCovers, int maxCovers, Instant now) {
        super(id, now);
        this.venueId = Objects.requireNonNull(venueId, "venueId cannot be null");
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Table label is required");
        }
        if (minCovers < 1 || maxCovers < minCovers || maxCovers > 100) {
            throw new IllegalArgumentException("Table covers must satisfy 1 <= min <= max <= 100");
        }
        this.label = label.trim();
        this.section = section == null ? "" : section.trim();
        this.minCovers = minCovers;
        this.maxCovers = maxCovers;
    }

    public static DiningTable create(DiningTableId id, VenueId venueId, String label, String section, int minCovers, int maxCovers,
                                     Set<DiningTableId> combinableWith, Instant now) {
        final var t = new DiningTable(id, venueId, label, section, minCovers, maxCovers, now);
        if (combinableWith != null) {
            t.combinableWith.addAll(combinableWith);
            t.combinableWith.remove(id);
        }
        return t;
    }

    public void seat(OrderId orderId, int covers, Instant now) {
        if (status != TableStatus.AVAILABLE) {
            throw new InvalidStateException("Table " + label + " is " + status + " and cannot be seated");
        }
        if (covers < 1 || covers > maxCovers) {
            throw new BusinessRuleViolation("Table " + label + " seats at most " + maxCovers);
        }
        this.status = TableStatus.OCCUPIED;
        this.currentOrderId = Objects.requireNonNull(orderId, "orderId cannot be null");
        touch(now);
        raise(TableSeated.at(now, id().value(), venueId.value(), orderId.value(), covers));
    }

    /** Guests left (bill paid or order moved): the table needs cleaning before reuse. */
    public void release(Instant now) {
        if (status != TableStatus.OCCUPIED) {
            throw new InvalidStateException("Table " + label + " is not occupied");
        }
        this.status = TableStatus.DIRTY;
        this.currentOrderId = null;
        touch(now);
        raise(TableReleased.at(now, id().value(), venueId.value()));
    }

    public void markClean(Instant now) {
        if (status != TableStatus.DIRTY) {
            throw new InvalidStateException("Table " + label + " is not waiting to be cleaned");
        }
        this.status = TableStatus.AVAILABLE;
        touch(now);
        raise(TableCleaned.at(now, id().value(), venueId.value(), maxCovers));
    }

    public void block(String reason, Instant now) {
        if (status == TableStatus.OCCUPIED) {
            throw new InvalidStateException("Cannot block an occupied table");
        }
        this.status = TableStatus.BLOCKED;
        this.blockReason = reason == null ? "" : reason;
        touch(now);
        raise(TableBlocked.at(now, id().value(), venueId.value(), blockReason));
    }

    public void unblock(Instant now) {
        if (status != TableStatus.BLOCKED) {
            throw new InvalidStateException("Table " + label + " is not blocked");
        }
        this.status = TableStatus.AVAILABLE;
        this.blockReason = null;
        touch(now);
        raise(TableUnblocked.at(now, id().value(), venueId.value()));
    }

    public VenueId venueId() { return venueId; }
    public String label() { return label; }
    public String section() { return section; }
    public int minCovers() { return minCovers; }
    public int maxCovers() { return maxCovers; }
    public Set<DiningTableId> combinableWith() { return Collections.unmodifiableSet(combinableWith); }
    public TableStatus status() { return status; }
    public OrderId currentOrderId() { return currentOrderId; }

    public Snapshot snapshot() {
        return new Snapshot(id().value(), venueId.value(), label, section, minCovers, maxCovers,
                combinableWith.stream().map(DiningTableId::value).collect(Collectors.toCollection(TreeSet::new)), status.name(),
                currentOrderId == null ? null : currentOrderId.value(), blockReason, createdAt, updatedAt, version);
    }

    public static DiningTable restore(Snapshot s) {
        final var t = new DiningTable(DiningTableId.of(s.id()), VenueId.of(s.venueId()), s.label(), s.section(), s.minCovers(), s.maxCovers(), s.createdAt());
        s.combinableWith().forEach(c -> t.combinableWith.add(DiningTableId.of(c)));
        t.status = TableStatus.valueOf(s.status());
        t.currentOrderId = s.currentOrderId() == null ? null : OrderId.of(s.currentOrderId());
        t.blockReason = s.blockReason();
        t.restoreAudit(s.createdAt(), s.updatedAt(), s.version());
        return t;
    }
}
