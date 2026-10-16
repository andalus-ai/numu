package tech.kayys.numou.restaurant.venue.application;

import tech.kayys.numou.restaurant.venue.domain.Menu;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Immutable read model of a published menu: safe to cache and to hand to many readers. */
public record MenuView(UUID menuId, String name, String currency, Map<String, List<Item>> sections) {

    public record Item(UUID id, String name, String description, BigDecimal price, List<String> allergens, List<Option> options) { }

    public record Option(String name, BigDecimal priceDelta) { }

    public static MenuView of(Menu m) {
        final var out = new LinkedHashMap<String, List<Item>>();
        m.sections().forEach((section, items) -> out.put(section, items.stream().filter(i -> i.active()).map(i -> new Item(
                i.id().value(), i.name(), i.description(), i.price().amount(), i.allergens().stream().sorted().toList(),
                i.modifiers().stream().map(x -> new Option(x.name(), x.priceDelta().amount())).toList())).toList()));
        final String currency = m.allItems().isEmpty() ? "" : m.allItems().get(0).price().currency().code();
        return new MenuView(m.id().value(), m.name(), currency, Map.copyOf(out));
    }
}
