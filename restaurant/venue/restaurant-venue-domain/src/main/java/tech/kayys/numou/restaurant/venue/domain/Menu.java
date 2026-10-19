package tech.kayys.numou.restaurant.venue.domain;

import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.restaurant.kernel.domain.MenuId;
import tech.kayys.numou.restaurant.kernel.domain.MenuItemId;
import tech.kayys.numou.restaurant.kernel.domain.RestaurantAggregate;
import tech.kayys.numou.restaurant.kernel.domain.StationId;
import tech.kayys.numou.restaurant.kernel.domain.VenueId;
import tech.kayys.numou.restaurant.venue.domain.event.MenuArchived;
import tech.kayys.numou.restaurant.venue.domain.event.MenuItemPriceChanged;
import tech.kayys.numou.restaurant.venue.domain.event.MenuPublished;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** A venue's menu: sections of items. Only a PUBLISHED menu can be ordered from. */
public final class Menu extends RestaurantAggregate<MenuId> {

    private static final long serialVersionUID = 1L;

    public enum Status { DRAFT, PUBLISHED, ARCHIVED }

    public record Snapshot(UUID id, UUID venueId, String name, String status, Map<String, List<ItemSnapshot>> sections,
                           Instant createdAt, Instant updatedAt, int version) { }

    public record ItemSnapshot(UUID id, UUID productRef, String name, String description, BigDecimal price, String currency,
                               UUID station, long prepSeconds, List<String> allergens, List<ModifierSnapshot> modifiers, boolean active) { }

    public record ModifierSnapshot(String name, BigDecimal delta) { }

    private final VenueId venueId;
    private String name;
    private Status status = Status.DRAFT;
    private final Map<String, List<MenuItem>> sections = new LinkedHashMap<>();

    private Menu(MenuId id, VenueId venueId, String name, Instant now) {
        super(id, now);
        this.venueId = venueId;
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Menu name is required");
        }
        this.name = name.trim();
    }

    public static Menu create(MenuId id, VenueId venueId, String name, Instant now) {
        return new Menu(id, venueId, name, now);
    }

    public void addSection(String section, Instant now) {
        requireEditable();
        if (sections.putIfAbsent(section.trim(), new ArrayList<>()) != null) {
            throw new BusinessRuleViolation("Section already exists: " + section);
        }
        touch(now);
    }

    public MenuItem addItem(String section, MenuItem item, Instant now) {
        requireEditable();
        final var items = sections.get(section);
        if (items == null) {
            throw new BusinessRuleViolation("No such section: " + section);
        }
        if (allItems().stream().anyMatch(i -> i.name().equalsIgnoreCase(item.name()))) {
            throw new BusinessRuleViolation("An item named '" + item.name() + "' already exists on this menu");
        }
        items.add(item);
        touch(now);
        return item;
    }

    public static MenuItem newItem(String name, String description, Money price, StationId station, Duration prepTime,
                                   Set<String> allergens, List<MenuItem.Modifier> modifiers, UUID productRef) {
        return new MenuItem(MenuItemId.generate(), productRef, name, description, price, station, prepTime, allergens, modifiers, true);
    }

    public void changePrice(MenuItemId itemId, Money newPrice, Instant now) {
        if (status == Status.ARCHIVED) {
            throw new InvalidStateException("An archived menu cannot be changed");
        }
        final var old = require(itemId);
        if (old.price().currency().equals(newPrice.currency()) == false) {
            throw new BusinessRuleViolation("Price currency cannot change");
        }
        replace(itemId, old.withPrice(newPrice));
        touch(now);
        raise(MenuItemPriceChanged.at(now, id().value(), venueId.value(), itemId.value(), old.price().amount(), newPrice.amount(), newPrice.currency().code()));
    }

    /** Turns an item off the menu without deleting history (seasonal, discontinued). Distinct from the kitchen's 86. */
    public void setActive(MenuItemId itemId, boolean active, Instant now) {
        replace(itemId, require(itemId).withActive(active));
        touch(now);
    }

    public void publish(Instant now) {
        if (status == Status.ARCHIVED) {
            throw new InvalidStateException("An archived menu cannot be published");
        }
        if (allItems().stream().noneMatch(MenuItem::active)) {
            throw new BusinessRuleViolation("A menu needs at least one active item to be published");
        }
        this.status = Status.PUBLISHED;
        touch(now);
        raise(MenuPublished.at(now, id().value(), venueId.value()));
    }

    public void archive(Instant now) {
        this.status = Status.ARCHIVED;
        touch(now);
        raise(MenuArchived.at(now, id().value(), venueId.value()));
    }

    public Optional<MenuItem> find(MenuItemId itemId) {
        return allItems().stream().filter(i -> i.id().equals(itemId)).findFirst();
    }

    public List<MenuItem> allItems() {
        return sections.values().stream().flatMap(List::stream).toList();
    }

    public Map<String, List<MenuItem>> sections() {
        final var copy = new LinkedHashMap<String, List<MenuItem>>();
        sections.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        return copy;
    }

    public VenueId venueId() { return venueId; }
    public String name() { return name; }
    public Status status() { return status; }

    private MenuItem require(MenuItemId itemId) {
        return find(itemId).orElseThrow(() -> new BusinessRuleViolation("No such menu item: " + itemId));
    }

    private void replace(MenuItemId itemId, MenuItem next) {
        sections.values().forEach(list -> list.replaceAll(i -> i.id().equals(itemId) ? next : i));
    }

    private void requireEditable() {
        if (status == Status.ARCHIVED) {
            throw new InvalidStateException("An archived menu cannot be changed");
        }
    }

    public Snapshot snapshot() {
        final var out = new LinkedHashMap<String, List<ItemSnapshot>>();
        sections.forEach((s, items) -> out.put(s, items.stream().map(i -> new ItemSnapshot(i.id().value(), i.productRef(), i.name(),
                i.description(), i.price().amount(), i.price().currency().code(), i.station().value(), i.prepTime().getSeconds(),
                i.allergens().stream().sorted().toList(),
                i.modifiers().stream().map(m -> new ModifierSnapshot(m.name(), m.priceDelta().amount())).toList(), i.active())).toList()));
        return new Snapshot(id().value(), venueId.value(), name, status.name(), out, createdAt, updatedAt, version);
    }

    public static Menu restore(Snapshot s) {
        final var m = new Menu(MenuId.of(s.id()), VenueId.of(s.venueId()), s.name(), s.createdAt());
        m.status = Status.valueOf(s.status());
        s.sections().forEach((sec, items) -> m.sections.put(sec, new ArrayList<>(items.stream().map(i -> new MenuItem(MenuItemId.of(i.id()),
                i.productRef(), i.name(), i.description(), Money.of(i.price(), i.currency()), StationId.of(i.station()),
                Duration.ofSeconds(i.prepSeconds()), Set.copyOf(i.allergens()),
                i.modifiers().stream().map(x -> new MenuItem.Modifier(x.name(), Money.of(x.delta(), i.currency()))).toList(), i.active())).toList())));
        m.restoreAudit(s.createdAt(), s.updatedAt(), s.version());
        return m;
    }
}
