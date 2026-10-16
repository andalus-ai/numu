package tech.kayys.numou.restaurant.order.domain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** What the order context needs to know about a dish (translated from the menu by the application layer). */
public record LineSpec(UUID menuItemId, String name, Money unitPrice, UUID stationId, long prepSeconds, Set<String> allergens,
                       List<OrderLine.Modifier> modifiers) { }
