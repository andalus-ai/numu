package tech.kayys.syirkah.restaurant.venue.domain;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.restaurant.kernel.domain.MenuItemId;
import tech.kayys.syirkah.restaurant.kernel.domain.StationId;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A dish as the guest sees it. {@code productRef} optionally links the product-catalog entry (inventory, recipe
 * costing) without copying it.
 *
 * @param modifiers add-ons and their price deltas ("extra cheese" +5,000). Orders pick modifiers <em>by name</em>
 *                  and the price comes from here: a client can never set its own price.
 */
public record MenuItem(MenuItemId id, UUID productRef, String name, String description, Money price, StationId station,
                       Duration prepTime, Set<String> allergens, List<Modifier> modifiers, boolean active) {

    public record Modifier(String name, Money priceDelta) {
        public Modifier {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Modifier name is required");
            }
            Objects.requireNonNull(priceDelta, "priceDelta cannot be null");
        }
    }

    public MenuItem {
        Objects.requireNonNull(id, "id cannot be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Menu item name is required");
        }
        Objects.requireNonNull(price, "price cannot be null");
        if (price.amount().signum() < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        Objects.requireNonNull(station, "station cannot be null");
        Objects.requireNonNull(prepTime, "prepTime cannot be null");
        description = description == null ? "" : description;
        allergens = allergens == null ? Set.of() : Set.copyOf(allergens);
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
    }

    public MenuItem withPrice(Money newPrice) {
        return new MenuItem(id, productRef, name, description, newPrice, station, prepTime, allergens, modifiers, active);
    }

    public MenuItem withActive(boolean flag) {
        return new MenuItem(id, productRef, name, description, price, station, prepTime, allergens, modifiers, flag);
    }

    public java.util.Optional<Modifier> modifier(String modifierName) {
        return modifiers.stream().filter(m -> m.name().equalsIgnoreCase(modifierName)).findFirst();
    }
}
