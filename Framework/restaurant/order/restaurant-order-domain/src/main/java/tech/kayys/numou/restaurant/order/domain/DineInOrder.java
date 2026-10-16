package tech.kayys.numou.restaurant.order.domain;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.restaurant.kernel.domain.DiningTableId;
import tech.kayys.numou.restaurant.kernel.domain.OrderId;
import tech.kayys.numou.restaurant.kernel.domain.RestaurantAggregate;
import tech.kayys.numou.restaurant.kernel.domain.VenueId;
import tech.kayys.numou.restaurant.order.domain.event.BillRequested;
import tech.kayys.numou.restaurant.order.domain.event.CourseFired;
import tech.kayys.numou.restaurant.order.domain.event.OrderDiscountApplied;
import tech.kayys.numou.restaurant.order.domain.event.OrderLineVoided;
import tech.kayys.numou.restaurant.order.domain.event.OrderOpened;
import tech.kayys.numou.restaurant.order.domain.event.OrderPaid;
import tech.kayys.numou.restaurant.order.domain.event.OrderTableTransferred;
import tech.kayys.numou.restaurant.order.domain.event.OrderVoided;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A table's check. Money rules worth knowing:
 * <ul>
 *   <li>Prices are snapshots taken when a line is added; the client never supplies a price.</li>
 *   <li>Anything that reduces what the guest pays after food was sent (void, discount, cancel) needs an
 *       {@code approvedBy} (a manager). The domain demands the field; the REST layer verifies the approver's
 *       permission and the audit trail records it.</li>
 *   <li>A bill cannot be requested while items are still unsent: fire or remove them first.</li>
 * </ul>
 */
public final class DineInOrder extends RestaurantAggregate<OrderId> {

    private static final long serialVersionUID = 1L;
    private static final int MAX_LINE_QUANTITY = 50;

    public record LineSnapshot(UUID lineId, UUID menuItemId, String name, BigDecimal unitPrice, int quantity,
                               List<ModifierSnapshot> modifiers, String notes, int course, UUID stationId, long prepSeconds,
                               List<String> allergens, String status, String voidReason) { }

    public record ModifierSnapshot(String name, BigDecimal delta) { }

    public record Snapshot(UUID id, UUID venueId, List<UUID> tableIds, String tableLabel, String server, int covers, String status,
                           String currency, int fractionDigits, BigDecimal taxRate, BigDecimal serviceChargeRate, boolean taxOnServiceCharge,
                           List<LineSnapshot> lines, BigDecimal discountPercent, String discountApprovedBy, String paymentRef,
                           Instant createdAt, Instant updatedAt, int version) { }

    private final VenueId venueId;
    private final Set<DiningTableId> tableIds = new LinkedHashSet<>();
    private String tableLabel;
    private final String server;
    private final int covers;
    private OrderStatus status = OrderStatus.OPEN;
    private final PricingPolicy policy;
    private final List<OrderLine> lines = new ArrayList<>();
    private BigDecimal discountPercent = BigDecimal.ZERO;
    private String discountApprovedBy;
    private String paymentRef;

    private DineInOrder(OrderId id, VenueId venueId, String tableLabel, String server, int covers, PricingPolicy policy, Instant now) {
        super(id, now);
        this.venueId = Objects.requireNonNull(venueId, "venueId cannot be null");
        this.tableLabel = tableLabel == null ? "" : tableLabel;
        this.server = server == null ? "" : server;
        if (covers < 1) {
            throw new IllegalArgumentException("An order needs at least one guest");
        }
        this.covers = covers;
        this.policy = Objects.requireNonNull(policy, "policy cannot be null");
    }

    public static DineInOrder open(OrderId id, VenueId venueId, Set<DiningTableId> tables, String tableLabel, String server, int covers,
                                   PricingPolicy policy, Instant now) {
        if (tables == null || tables.isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one table");
        }
        final var o = new DineInOrder(id, venueId, tableLabel, server, covers, policy, now);
        o.tableIds.addAll(tables);
        o.raise(OrderOpened.at(now, id.value(), venueId.value(), o.tableUuids(), covers));
        return o;
    }

    // ---------------------------------------------------------------- lines

    public UUID addLine(LineSpec spec, int quantity, List<String> modifierNames, String notes, int course, Instant now) {
        requireOpen();
        if (quantity < 1 || quantity > MAX_LINE_QUANTITY) {
            throw new BusinessRuleViolation("Quantity must be between 1 and " + MAX_LINE_QUANTITY);
        }
        if (course < 1 || course > 9) {
            throw new BusinessRuleViolation("Course must be between 1 and 9");
        }
        if (!spec.unitPrice().currency().code().equals(policy.currency())) {
            throw new BusinessRuleViolation("Item currency does not match the venue currency");
        }
        final var chosen = new ArrayList<OrderLine.Modifier>();
        for (final String wanted : modifierNames == null ? List.<String>of() : modifierNames) {
            chosen.add(spec.modifiers().stream().filter(m -> m.name().equalsIgnoreCase(wanted)).findFirst()
                    .orElseThrow(() -> new BusinessRuleViolation("'" + spec.name() + "' has no option '" + wanted + "'")));
        }
        final var line = new OrderLine(UUID.randomUUID(), spec.menuItemId(), spec.name(), spec.unitPrice(), quantity, chosen,
                notes == null ? "" : notes, course, spec.stationId(), spec.prepSeconds(), spec.allergens(), OrderLine.Status.PENDING, "");
        lines.add(line);
        touch(now);
        return line.lineId();
    }

    public void changeQuantity(UUID lineId, int quantity, Instant now) {
        requireOpen();
        final var line = requireLine(lineId);
        if (line.status() != OrderLine.Status.PENDING) {
            throw new InvalidStateException("Only an unsent line can be changed; void it instead");
        }
        if (quantity < 1 || quantity > MAX_LINE_QUANTITY) {
            throw new BusinessRuleViolation("Quantity must be between 1 and " + MAX_LINE_QUANTITY);
        }
        replace(line.withQuantity(quantity));
        touch(now);
    }

    public void removePendingLine(UUID lineId, Instant now) {
        requireOpen();
        final var line = requireLine(lineId);
        if (line.status() != OrderLine.Status.PENDING) {
            throw new InvalidStateException("Only an unsent line can be removed; void it instead");
        }
        lines.remove(line);
        touch(now);
    }

    /** Sends every unsent line of a course to the kitchen. */
    public void fireCourse(int course, Instant now) {
        requireOpen();
        final var toFire = lines.stream().filter(l -> l.course() == course && l.status() == OrderLine.Status.PENDING).toList();
        if (toFire.isEmpty()) {
            throw new BusinessRuleViolation("Nothing to fire for course " + course);
        }
        toFire.forEach(l -> replace(l.with(OrderLine.Status.FIRED, "")));
        touch(now);
        raise(CourseFired.at(now, id().value(), venueId.value(), course, tableLabel, toFire.stream().map(l -> new CourseFired.Line(
                l.lineId(), l.menuItemId(), l.name(), l.quantity(),
                l.modifiers().stream().map(OrderLine.Modifier::name).toList(), l.notes(),
                l.allergens().stream().sorted().toList(), l.prepSeconds(), l.stationId())).toList()));
    }

    /** Kitchen finished the line. Idempotent: events are delivered at-least-once. */
    public void markLineReady(UUID lineId, Instant now) {
        final var line = requireLine(lineId);
        if (line.status() == OrderLine.Status.READY || line.status() == OrderLine.Status.SERVED || line.status() == OrderLine.Status.VOID) {
            return;
        }
        if (line.status() != OrderLine.Status.FIRED) {
            throw new InvalidStateException("Line was never sent to the kitchen");
        }
        replace(line.with(OrderLine.Status.READY, ""));
        touch(now);
    }

    public void markLineServed(UUID lineId, Instant now) {
        final var line = requireLine(lineId);
        if (line.status() == OrderLine.Status.SERVED || line.status() == OrderLine.Status.VOID) {
            return;
        }
        if (line.status() != OrderLine.Status.READY && line.status() != OrderLine.Status.FIRED) {
            throw new InvalidStateException("Line cannot be served from " + line.status());
        }
        replace(line.with(OrderLine.Status.SERVED, ""));
        touch(now);
    }

    /** Voids a line that already went to the kitchen. Needs a manager's approval. */
    public void voidLine(UUID lineId, String reason, String approvedBy, Instant now) {
        requireOpen();
        final var line = requireLine(lineId);
        if (line.status() == OrderLine.Status.PENDING) {
            throw new InvalidStateException("An unsent line is removed, not voided");
        }
        if (line.status() == OrderLine.Status.VOID) {
            return;
        }
        requireApproval(approvedBy);
        replace(line.with(OrderLine.Status.VOID, reason == null ? "" : reason));
        touch(now);
        raise(OrderLineVoided.at(now, id().value(), venueId.value(), lineId, reason == null ? "" : reason, approvedBy));
    }

    // ---------------------------------------------------------------- bill

    public void applyDiscount(BigDecimal percent, String approvedBy, Instant now) {
        if (status != OrderStatus.OPEN && status != OrderStatus.BILL_REQUESTED) {
            throw new InvalidStateException("Cannot discount a " + status + " order");
        }
        if (percent == null || percent.signum() <= 0 || percent.compareTo(new BigDecimal("100")) > 0) {
            throw new BusinessRuleViolation("Discount must be above 0 and at most 100 percent");
        }
        requireApproval(approvedBy);
        this.discountPercent = percent;
        this.discountApprovedBy = approvedBy;
        touch(now);
        raise(OrderDiscountApplied.at(now, id().value(), venueId.value(), percent, approvedBy));
    }

    public void requestBill(Instant now) {
        requireOpen();
        if (lines.stream().noneMatch(OrderLine::isLive)) {
            throw new BusinessRuleViolation("There is nothing to bill");
        }
        if (lines.stream().anyMatch(l -> l.status() == OrderLine.Status.PENDING)) {
            throw new BusinessRuleViolation("Unsent items must be fired or removed before the bill");
        }
        this.status = OrderStatus.BILL_REQUESTED;
        touch(now);
        final var t = totals();
        raise(BillRequested.at(now, id().value(), venueId.value(), t.total().amount(), policy.currency()));
    }

    /** Re-opens a requested bill (guest wants to add a coffee). */
    public void reopen(Instant now) {
        if (status != OrderStatus.BILL_REQUESTED) {
            throw new InvalidStateException("Only a requested bill can be re-opened");
        }
        this.status = OrderStatus.OPEN;
        touch(now);
    }

    public void settle(String paymentRef, Instant now) {
        if (status != OrderStatus.BILL_REQUESTED) {
            throw new InvalidStateException("Only a requested bill can be settled, was " + status);
        }
        this.paymentRef = Objects.requireNonNull(paymentRef, "paymentRef cannot be null");
        this.status = OrderStatus.PAID;
        touch(now);
        raise(OrderPaid.at(now, id().value(), venueId.value(), tableUuids(), totals().total().amount(), policy.currency(), paymentRef));
    }

    /** Cancels the whole check. Free if nothing was sent; otherwise a manager must approve. */
    public void voidOrder(String reason, String approvedBy, Instant now) {
        if (status == OrderStatus.PAID || status == OrderStatus.VOIDED) {
            throw new InvalidStateException("Order is already " + status);
        }
        if (lines.stream().anyMatch(l -> l.status() != OrderLine.Status.PENDING && l.status() != OrderLine.Status.VOID)) {
            requireApproval(approvedBy);
        }
        this.status = OrderStatus.VOIDED;
        touch(now);
        raise(OrderVoided.at(now, id().value(), venueId.value(), tableUuids(), reason == null ? "" : reason, approvedBy == null ? "" : approvedBy));
    }

    public void transferTable(Set<DiningTableId> newTables, String newLabel, Instant now) {
        requireOpen();
        if (newTables == null || newTables.isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one table");
        }
        final var from = tableUuids();
        this.tableIds.clear();
        this.tableIds.addAll(newTables);
        this.tableLabel = newLabel == null ? "" : newLabel;
        touch(now);
        raise(OrderTableTransferred.at(now, id().value(), venueId.value(), from, tableUuids()));
    }

    /**
     * Subtotal - discount, then service charge on that, then tax (on service charge too when the policy says so).
     * Each component is rounded HALF_UP to the currency's minor unit; the total is the sum of the rounded parts, so
     * the printed bill always adds up.
     */
    public OrderTotals totals() {
        final var currency = tech.kayys.numou.foundation.domain.valueobject.Currency.of(policy.currency());
        BigDecimal subtotal = BigDecimal.ZERO;
        for (final var l : lines) {
            if (l.isLive()) {
                subtotal = subtotal.add(l.lineTotal().amount());
            }
        }
        subtotal = round(subtotal);
        final BigDecimal discount = round(subtotal.multiply(discountPercent).divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
        final BigDecimal base = subtotal.subtract(discount);
        final BigDecimal service = round(base.multiply(policy.serviceChargeRate()));
        final BigDecimal taxBase = policy.taxOnServiceCharge() ? base.add(service) : base;
        final BigDecimal tax = round(taxBase.multiply(policy.taxRate()));
        final BigDecimal total = base.add(service).add(tax);
        return new OrderTotals(new Money(subtotal, currency), new Money(discount, currency), new Money(service, currency),
                new Money(tax, currency), new Money(total, currency));
    }

    private BigDecimal round(BigDecimal v) {
        return v.setScale(policy.fractionDigits(), RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- accessors / internals

    public VenueId venueId() { return venueId; }
    public Set<DiningTableId> tableIds() { return java.util.Collections.unmodifiableSet(tableIds); }
    public String tableLabel() { return tableLabel; }
    public String server() { return server; }
    public int covers() { return covers; }
    public OrderStatus status() { return status; }
    public PricingPolicy policy() { return policy; }
    public List<OrderLine> lines() { return List.copyOf(lines); }
    public BigDecimal discountPercent() { return discountPercent; }

    public OrderLine line(UUID lineId) {
        return requireLine(lineId);
    }

    private List<UUID> tableUuids() {
        return tableIds.stream().map(DiningTableId::value).sorted().toList();
    }

    private void requireOpen() {
        if (status != OrderStatus.OPEN) {
            throw new InvalidStateException("Order is " + status + " and cannot be changed");
        }
    }

    private static void requireApproval(String approvedBy) {
        if (approvedBy == null || approvedBy.isBlank()) {
            throw new BusinessRuleViolation("A manager's approval is required");
        }
    }

    private OrderLine requireLine(UUID lineId) {
        return lines.stream().filter(l -> l.lineId().equals(lineId)).findFirst()
                .orElseThrow(() -> new InvalidStateException("No such line on this order: " + lineId));
    }

    private void replace(OrderLine next) {
        lines.replaceAll(l -> l.lineId().equals(next.lineId()) ? next : l);
    }

    // ---------------------------------------------------------------- persistence

    public Snapshot snapshot() {
        return new Snapshot(id().value(), venueId.value(), tableUuids(), tableLabel, server, covers, status.name(), policy.currency(),
                policy.fractionDigits(), policy.taxRate(), policy.serviceChargeRate(), policy.taxOnServiceCharge(),
                lines.stream().map(l -> new LineSnapshot(l.lineId(), l.menuItemId(), l.name(), l.unitPrice().amount(), l.quantity(),
                        l.modifiers().stream().map(m -> new ModifierSnapshot(m.name(), m.priceDelta().amount())).toList(), l.notes(),
                        l.course(), l.stationId(), l.prepSeconds(), l.allergens().stream().sorted().toList(), l.status().name(), l.voidReason())).toList(),
                discountPercent, discountApprovedBy, paymentRef, createdAt, updatedAt, version);
    }

    public static DineInOrder restore(Snapshot s) {
        final var policy = new PricingPolicy(s.currency(), s.fractionDigits(), s.taxRate(), s.serviceChargeRate(), s.taxOnServiceCharge());
        final var o = new DineInOrder(OrderId.of(s.id()), VenueId.of(s.venueId()), s.tableLabel(), s.server(), s.covers(), policy, s.createdAt());
        s.tableIds().forEach(t -> o.tableIds.add(DiningTableId.of(t)));
        o.status = OrderStatus.valueOf(s.status());
        s.lines().forEach(l -> o.lines.add(new OrderLine(l.lineId(), l.menuItemId(), l.name(), Money.of(l.unitPrice(), s.currency()), l.quantity(),
                l.modifiers().stream().map(m -> new OrderLine.Modifier(m.name(), Money.of(m.delta(), s.currency()))).toList(), l.notes(),
                l.course(), l.stationId(), l.prepSeconds(), new TreeSet<>(l.allergens()), OrderLine.Status.valueOf(l.status()), l.voidReason())));
        o.discountPercent = s.discountPercent();
        o.discountApprovedBy = s.discountApprovedBy();
        o.paymentRef = s.paymentRef();
        o.restoreAudit(s.createdAt(), s.updatedAt(), s.version());
        return o;
    }
}
