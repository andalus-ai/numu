package tech.kayys.syirkah.restaurant.kitchen.application;

import tech.kayys.syirkah.restaurant.kitchen.domain.KitchenTicket;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** What a KDS screen renders for one ticket. */
public record TicketView(UUID id, UUID orderId, UUID stationId, int course, String tableLabel, String priority, String status,
                         boolean late, long waitingSeconds, Instant promisedAt, boolean hasAllergens, List<Item> items) {

    public record Item(UUID lineId, String name, int quantity, List<String> modifiers, String notes, Set<String> allergens, String state) { }

    public static TicketView of(KitchenTicket t, Instant now) {
        return new TicketView(t.id().value(), t.orderId().value(), t.stationId().value(), t.course(), t.tableLabel(),
                t.priority().name(), t.status().name(), t.isLate(now), t.waiting(now).getSeconds(), t.promisedAt(), t.hasAllergens(),
                t.items().stream().map(i -> new Item(i.lineId(), i.name(), i.quantity(), i.modifiers(), i.notes(), i.allergens(), i.state().name())).toList());
    }
}
