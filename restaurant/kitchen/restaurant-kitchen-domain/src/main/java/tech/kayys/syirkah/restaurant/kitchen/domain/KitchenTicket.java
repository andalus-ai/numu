package tech.kayys.syirkah.restaurant.kitchen.domain;

import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.restaurant.kernel.domain.OrderId;
import tech.kayys.syirkah.aggregate.domain.VersionedAggregate;
import tech.kayys.syirkah.restaurant.kernel.domain.StationId;
import tech.kayys.syirkah.restaurant.kernel.domain.TicketId;
import tech.kayys.syirkah.restaurant.kernel.domain.VenueId;
import tech.kayys.syirkah.restaurant.kitchen.domain.event.TicketBumped;
import tech.kayys.syirkah.restaurant.kitchen.domain.event.TicketCancelled;
import tech.kayys.syirkah.restaurant.kitchen.domain.event.TicketCreated;
import tech.kayys.syirkah.restaurant.kitchen.domain.event.TicketItemReady;
import tech.kayys.syirkah.restaurant.kitchen.domain.event.TicketItemStarted;
import tech.kayys.syirkah.restaurant.kitchen.domain.event.TicketItemVoided;
import tech.kayys.syirkah.restaurant.kitchen.domain.event.TicketPriorityChanged;
import tech.kayys.syirkah.restaurant.kitchen.domain.event.TicketReady;
import tech.kayys.syirkah.restaurant.kitchen.domain.event.TicketRecalled;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What one station has to cook for one fired course of one order.
 *
 * <p>The ticket status is <em>derived</em> from its items rather than stored as a free field, so it cannot
 * disagree with them ("READY" while an item is still preparing).
 */
public final class KitchenTicket extends VersionedAggregate<TicketId> {

    private static final long serialVersionUID = 1L;

    public record ItemSnapshot(UUID lineId, UUID menuItemId, String name, int quantity, List<String> modifiers, String notes,
                               List<String> allergens, long prepSeconds, String state) { }

    public record Snapshot(UUID id, UUID orderId, UUID venueId, UUID stationId, int course, String tableLabel, String priority,
                           Instant firedAt, Instant promisedAt, Instant bumpedAt, boolean bumped, boolean cancelled,
                           List<ItemSnapshot> items, Instant createdAt, Instant updatedAt, int version) { }

    private final OrderId orderId;
    private final VenueId venueId;
    private final StationId stationId;
    private final int course;
    private final String tableLabel;
    private Priority priority;
    private final Instant firedAt;
    private final Instant promisedAt;
    private Instant bumpedAt;
    private boolean bumped;
    private boolean cancelled;
    private final List<TicketItem> items = new ArrayList<>();

    private KitchenTicket(TicketId id, OrderId orderId, VenueId venueId, StationId stationId, int course, String tableLabel,
                          Priority priority, Instant firedAt, Instant promisedAt) {
        super(id, firedAt);
        this.orderId = Objects.requireNonNull(orderId, "orderId cannot be null");
        this.venueId = Objects.requireNonNull(venueId, "venueId cannot be null");
        this.stationId = Objects.requireNonNull(stationId, "stationId cannot be null");
        this.course = course;
        this.tableLabel = tableLabel == null ? "" : tableLabel;
        this.priority = Objects.requireNonNull(priority, "priority cannot be null");
        this.firedAt = firedAt;
        this.promisedAt = promisedAt;
    }

    public static KitchenTicket create(TicketId id, OrderId orderId, VenueId venueId, StationId stationId, int course,
                                       String tableLabel, Priority priority, List<TicketItem> items, Instant firedAt,
                                       Instant promisedAt) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("A ticket needs at least one item");
        }
        final var t = new KitchenTicket(id, orderId, venueId, stationId, course, tableLabel, priority, firedAt, promisedAt);
        items.forEach(i -> t.items.add(i.with(ItemState.QUEUED)));
        t.raise(TicketCreated.at(firedAt, id.value(), orderId.value(), venueId.value(), stationId.value(), course, items.size(), promisedAt));
        return t;
    }

    // ---- derived status ----

    public TicketStatus status() {
        if (cancelled) {
            return TicketStatus.CANCELLED;
        }
        if (bumped) {
            return TicketStatus.BUMPED;
        }
        final var live = items.stream().filter(i -> i.state() != ItemState.VOID).toList();
        if (live.isEmpty()) {
            return TicketStatus.CANCELLED;
        }
        if (live.stream().allMatch(i -> i.state() == ItemState.READY)) {
            return TicketStatus.READY;
        }
        return live.stream().anyMatch(i -> i.state() != ItemState.QUEUED) ? TicketStatus.IN_PROGRESS : TicketStatus.NEW;
    }

    // ---- cooking ----

    public void start(UUID lineId, Instant now) {
        requireOnScreen();
        replace(lineId, ItemState.QUEUED, ItemState.PREPARING);
        touch(now);
        raise(TicketItemStarted.at(now, id().value(), orderId.value(), lineId));
    }

    /** Starts every queued item, e.g. when the cook calls "fire all". */
    public void startAll(Instant now) {
        items.stream().filter(i -> i.state() == ItemState.QUEUED).map(TicketItem::lineId).toList().forEach(l -> start(l, now));
    }

    public void markReady(UUID lineId, Instant now) {
        requireOnScreen();
        final var before = status();
        final var item = find(lineId);
        if (item.state() != ItemState.PREPARING && item.state() != ItemState.QUEUED) {
            throw new InvalidStateException("Item cannot be marked ready from " + item.state());
        }
        set(lineId, item.with(ItemState.READY));
        touch(now);
        raise(TicketItemReady.at(now, id().value(), orderId.value(), lineId));
        if (before != TicketStatus.READY && status() == TicketStatus.READY) {
            raise(TicketReady.at(now, id().value(), orderId.value(), venueId.value(), stationId.value(), course));
        }
    }

    /** Hands the ticket off. Only when every remaining item is ready: food never leaves half-cooked. */
    public void bump(Instant now) {
        if (status() != TicketStatus.READY) {
            throw new BusinessRuleViolation("Cannot bump a ticket that is " + status() + "; every item must be ready");
        }
        this.bumped = true;
        this.bumpedAt = now;
        touch(now);
        raise(TicketBumped.at(now, id().value(), orderId.value(), venueId.value(), Duration.between(firedAt, now).getSeconds()));
    }

    /** Brings a bumped ticket back to the screen (wrong plate, dropped dish). */
    public void recall(Instant now) {
        if (!bumped) {
            throw new InvalidStateException("Only a bumped ticket can be recalled");
        }
        this.bumped = false;
        this.bumpedAt = null;
        touch(now);
        raise(TicketRecalled.at(now, id().value(), orderId.value()));
    }

    /** Removes an item (guest changed their mind, 86'd mid-service). Voiding the last live item cancels the ticket. */
    public void voidItem(UUID lineId, String reason, Instant now) {
        requireOnScreen();
        final var item = find(lineId);
        if (item.state() == ItemState.VOID) {
            return; // idempotent: the order may re-send the void
        }
        set(lineId, item.with(ItemState.VOID));
        touch(now);
        raise(TicketItemVoided.at(now, id().value(), orderId.value(), lineId, reason == null ? "" : reason));
        if (items.stream().allMatch(i -> i.state() == ItemState.VOID)) {
            this.cancelled = true;
            raise(TicketCancelled.at(now, id().value(), orderId.value()));
        } else if (status() == TicketStatus.READY) {
            raise(TicketReady.at(now, id().value(), orderId.value(), venueId.value(), stationId.value(), course));
        }
    }

    public void changePriority(Priority next, Instant now) {
        requireOnScreen();
        if (next == priority) {
            return;
        }
        this.priority = next;
        touch(now);
        raise(TicketPriorityChanged.at(now, id().value(), orderId.value(), next.name()));
    }

    // ---- views ----

    public boolean isLate(Instant now) {
        return status().isCooking() && now.isAfter(promisedAt);
    }

    public Duration waiting(Instant now) {
        return Duration.between(firedAt, now);
    }

    public boolean hasAllergens() {
        return items.stream().anyMatch(i -> !i.allergens().isEmpty());
    }

    public int activeItemCount() {
        return status().isCooking() ? (int) items.stream().filter(i -> i.state() == ItemState.QUEUED || i.state() == ItemState.PREPARING).count() : 0;
    }

    public OrderId orderId() { return orderId; }
    public VenueId venueId() { return venueId; }
    public StationId stationId() { return stationId; }
    public int course() { return course; }
    public String tableLabel() { return tableLabel; }
    public Priority priority() { return priority; }
    public Instant firedAt() { return firedAt; }
    public Instant promisedAt() { return promisedAt; }
    public List<TicketItem> items() { return List.copyOf(items); }

    // ---- internals ----

    private void requireOnScreen() {
        if (!status().isOnScreen()) {
            throw new InvalidStateException("Ticket is " + status() + " and cannot be changed");
        }
    }

    private TicketItem find(UUID lineId) {
        return items.stream().filter(i -> i.lineId().equals(lineId)).findFirst()
                .orElseThrow(() -> new InvalidStateException("No such item on this ticket: " + lineId));
    }

    private void set(UUID lineId, TicketItem next) {
        items.replaceAll(i -> i.lineId().equals(lineId) ? next : i);
    }

    private void replace(UUID lineId, ItemState expected, ItemState next) {
        final var item = find(lineId);
        if (item.state() != expected) {
            throw new InvalidStateException("Item must be " + expected + " to become " + next + ", was " + item.state());
        }
        set(lineId, item.with(next));
    }

    // ---- persistence ----

    public Snapshot snapshot() {
        return new Snapshot(id().value(), orderId.value(), venueId.value(), stationId.value(), course, tableLabel, priority.name(),
                firedAt, promisedAt, bumpedAt, bumped, cancelled,
                items.stream().map(i -> new ItemSnapshot(i.lineId(), i.menuItemId(), i.name(), i.quantity(), i.modifiers(), i.notes(),
                        i.allergens().stream().sorted().toList(), i.prepTime().getSeconds(), i.state().name())).toList(),
                createdAt, updatedAt, version);
    }

    public static KitchenTicket restore(Snapshot s) {
        final var t = new KitchenTicket(TicketId.of(s.id()), OrderId.of(s.orderId()), VenueId.of(s.venueId()), StationId.of(s.stationId()),
                s.course(), s.tableLabel(), Priority.valueOf(s.priority()), s.firedAt(), s.promisedAt());
        t.bumpedAt = s.bumpedAt();
        t.bumped = s.bumped();
        t.cancelled = s.cancelled();
        s.items().forEach(i -> t.items.add(new TicketItem(i.lineId(), i.menuItemId(), i.name(), i.quantity(), i.modifiers(), i.notes(),
                new java.util.TreeSet<>(i.allergens()), Duration.ofSeconds(i.prepSeconds()), ItemState.valueOf(i.state()))));
        t.restoreAudit(s.createdAt(), s.updatedAt(), s.version());
        return t;
    }
}
