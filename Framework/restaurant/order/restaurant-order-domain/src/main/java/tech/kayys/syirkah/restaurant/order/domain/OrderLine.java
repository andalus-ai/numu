package tech.kayys.syirkah.restaurant.order.domain;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** One ordered dish. {@code unitPrice} is a snapshot of the menu price at order time (prices change; checks must not). */
public record OrderLine(UUID lineId, UUID menuItemId, String name, Money unitPrice, int quantity, List<Modifier> modifiers, String notes,
                        int course, UUID stationId, long prepSeconds, Set<String> allergens, Status status, String voidReason) {

    public enum Status { PENDING, FIRED, READY, SERVED, VOID }

    public record Modifier(String name, Money priceDelta) { }

    /** Price of one unit including modifiers. */
    public Money unitTotal() {
        Money t = unitPrice;
        for (final var m : modifiers) {
            t = t.add(m.priceDelta());
        }
        return t;
    }

    public Money lineTotal() {
        return unitTotal().multiply(java.math.BigDecimal.valueOf(quantity));
    }

    public boolean isLive() {
        return status != Status.VOID;
    }

    OrderLine with(Status next, String reason) {
        return new OrderLine(lineId, menuItemId, name, unitPrice, quantity, modifiers, notes, course, stationId, prepSeconds, allergens, next, reason);
    }

    OrderLine withQuantity(int q) {
        return new OrderLine(lineId, menuItemId, name, unitPrice, q, modifiers, notes, course, stationId, prepSeconds, allergens, status, voidReason);
    }
}
