package tech.kayys.numou.restaurant.kitchen.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One line on a kitchen ticket.
 *
 * @param lineId    id of the originating order line (links the item back to the order)
 * @param modifiers human-readable modifiers as the cook should read them ("no onion", "medium rare")
 * @param allergens allergens the guest declared / the dish contains, shown prominently on the KDS
 */
public record TicketItem(UUID lineId, UUID menuItemId, String name, int quantity, List<String> modifiers, String notes,
                         Set<String> allergens, Duration prepTime, ItemState state) {

    public TicketItem {
        Objects.requireNonNull(lineId, "lineId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(prepTime, "prepTime cannot be null");
        Objects.requireNonNull(state, "state cannot be null");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        notes = notes == null ? "" : notes;
        allergens = allergens == null ? Set.of() : Set.copyOf(allergens);
    }

    public TicketItem with(ItemState next) {
        return new TicketItem(lineId, menuItemId, name, quantity, modifiers, notes, allergens, prepTime, next);
    }
}
