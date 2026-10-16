package tech.kayys.numou.restaurant.venue.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record MenuItemPriceChanged(
        UUID eventId,
        Instant occurredAt,
        UUID menuId,
        UUID venueId,
        UUID menuItemId,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        String currency) implements DomainEvent {

    public static MenuItemPriceChanged at(Instant now, UUID menuId, UUID venueId, UUID menuItemId, BigDecimal oldPrice, BigDecimal newPrice, String currency) {
        return new MenuItemPriceChanged(UUID.randomUUID(), now, menuId, venueId, menuItemId, oldPrice, newPrice, currency);
    }

    @Override
    public String eventType() {
        return "restaurant.menu.item-price-changed";
    }
}
