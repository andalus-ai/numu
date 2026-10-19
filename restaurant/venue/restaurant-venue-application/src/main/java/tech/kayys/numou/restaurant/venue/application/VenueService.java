package tech.kayys.numou.restaurant.venue.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.cache.Cache;
import tech.kayys.numou.cache.CacheKey;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.time.DomainClock;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.restaurant.kernel.application.ConflictRetry;
import tech.kayys.numou.restaurant.kernel.domain.DiningTableId;
import tech.kayys.numou.restaurant.kernel.domain.MenuId;
import tech.kayys.numou.restaurant.kernel.domain.MenuItemId;
import tech.kayys.numou.restaurant.kernel.domain.NotFoundException;
import tech.kayys.numou.restaurant.kernel.domain.StationId;
import tech.kayys.numou.restaurant.kernel.domain.VenueId;
import tech.kayys.numou.restaurant.kernel.spi.DomainEventSink;
import tech.kayys.numou.restaurant.venue.domain.DiningTable;
import tech.kayys.numou.restaurant.venue.domain.Menu;
import tech.kayys.numou.restaurant.venue.domain.MenuItem;
import tech.kayys.numou.restaurant.venue.domain.Venue;
import tech.kayys.numou.restaurant.venue.spi.DiningTableRepository;
import tech.kayys.numou.restaurant.venue.spi.MenuRepository;
import tech.kayys.numou.restaurant.venue.spi.VenueRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Venue setup and menu management, plus the cached published-menu read.
 *
 * <p>Cache discipline: the published menu is read on every order screen, so it is cached per (tenant, venue).
 * Every menu mutation invalidates <em>after</em> the transaction commits (invalidating before would let a
 * concurrent reader re-cache the old menu from the not-yet-committed database).
 */
public final class VenueService {

    public static final String MENU_CACHE = "menu.published";
    private static final int MAX_ATTEMPTS = 3;

    private final VenueRepository venues;
    private final DiningTableRepository tables;
    private final MenuRepository menus;
    private final UnitOfWork uow;
    private final DomainEventSink events;
    private final DomainClock clock;
    private final Cache<MenuView> menuCache;

    public VenueService(VenueRepository venues, DiningTableRepository tables, MenuRepository menus, UnitOfWork uow,
                        DomainEventSink events, DomainClock clock, Cache<MenuView> menuCache) {
        this.venues = venues;
        this.tables = tables;
        this.menus = menus;
        this.uow = uow;
        this.events = events;
        this.clock = clock;
        this.menuCache = menuCache;
    }

    // ---------------------------------------------------------------- venue & tables

    public Uni<UUID> createVenue(UUID tenantId, String name) {
        return uow.execute(() -> {
            final var v = Venue.indonesianDefaults(VenueId.generate(), name, clock.now());
            return venues.save(tenantId, v).flatMap(x -> events.publish(tenantId, v.pullDomainEvents())).replaceWith(v.id().value());
        });
    }

    public Uni<UUID> addStation(UUID tenantId, UUID venueId, String name, int parallelism) {
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> loadVenue(tenantId, venueId).flatMap(v -> {
            final var s = v.addStation(name, parallelism, clock.now());
            return venues.save(tenantId, v).replaceWith(s.id().value());
        })));
    }

    public Uni<UUID> addTable(UUID tenantId, UUID venueId, String label, String section, int min, int max, Set<UUID> combinableWith) {
        return uow.execute(() -> loadVenue(tenantId, venueId).flatMap(v -> {
            final var t = DiningTable.create(DiningTableId.generate(), v.id(), label, section, min, max,
                    combinableWith == null ? Set.of() : combinableWith.stream().map(DiningTableId::of).collect(java.util.stream.Collectors.toSet()), clock.now());
            return tables.save(tenantId, t).replaceWith(t.id().value());
        }));
    }

    public Uni<Void> markTableClean(UUID tenantId, UUID tableId) {
        return changeTable(tenantId, tableId, t -> t.markClean(clock.now()));
    }

    public Uni<Void> blockTable(UUID tenantId, UUID tableId, String reason) {
        return changeTable(tenantId, tableId, t -> t.block(reason, clock.now()));
    }

    public Uni<Void> unblockTable(UUID tenantId, UUID tableId) {
        return changeTable(tenantId, tableId, t -> t.unblock(clock.now()));
    }

    public Uni<List<DiningTable>> listTables(UUID tenantId, UUID venueId) {
        return tables.findByVenue(tenantId, VenueId.of(venueId));
    }

    private Uni<Void> changeTable(UUID tenantId, UUID tableId, Consumer<DiningTable> change) {
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> tables.find(tenantId, DiningTableId.of(tableId))
                .map(o -> o.orElseThrow(() -> new NotFoundException("Table", tableId))).flatMap(t -> {
                    change.accept(t);
                    return tables.save(tenantId, t).flatMap(x -> events.publish(tenantId, t.pullDomainEvents()));
                })));
    }

    private Uni<Venue> loadVenue(UUID tenantId, UUID venueId) {
        return venues.find(tenantId, VenueId.of(venueId)).map(o -> o.orElseThrow(() -> new NotFoundException("Venue", venueId)));
    }

    // ---------------------------------------------------------------- menu administration

    public Uni<UUID> createMenu(UUID tenantId, UUID venueId, String name) {
        return uow.execute(() -> loadVenue(tenantId, venueId).flatMap(v -> {
            final var m = Menu.create(MenuId.generate(), v.id(), name, clock.now());
            return menus.save(tenantId, m).replaceWith(m.id().value());
        }));
    }

    public Uni<Void> addSection(UUID tenantId, UUID menuId, String section) {
        return changeMenu(tenantId, menuId, m -> m.addSection(section, clock.now()));
    }

    public Uni<UUID> addItem(UUID tenantId, UUID menuId, String section, String name, String description, Money price, UUID station,
                             Duration prepTime, Set<String> allergens, List<MenuItem.Modifier> modifiers) {
        final var holder = new UUID[1];
        return changeMenu(tenantId, menuId, m -> holder[0] = m.addItem(section,
                Menu.newItem(name, description, price, StationId.of(station), prepTime, allergens, modifiers, null), clock.now()).id().value())
                .map(x -> holder[0]);
    }

    public Uni<Void> changePrice(UUID tenantId, UUID menuId, UUID itemId, Money price) {
        return changeMenu(tenantId, menuId, m -> m.changePrice(MenuItemId.of(itemId), price, clock.now()));
    }

    public Uni<Void> setItemActive(UUID tenantId, UUID menuId, UUID itemId, boolean active) {
        return changeMenu(tenantId, menuId, m -> m.setActive(MenuItemId.of(itemId), active, clock.now()));
    }

    public Uni<Void> publishMenu(UUID tenantId, UUID menuId) {
        return changeMenu(tenantId, menuId, m -> m.publish(clock.now()));
    }

    public Uni<Void> archiveMenu(UUID tenantId, UUID menuId) {
        return changeMenu(tenantId, menuId, m -> m.archive(clock.now()));
    }

    private <T> Uni<Void> changeMenu(UUID tenantId, UUID menuId, Consumer<Menu> change) {
        return ConflictRetry.run(MAX_ATTEMPTS, () -> uow.execute(() -> menus.find(tenantId, MenuId.of(menuId))
                .map(o -> o.orElseThrow(() -> new NotFoundException("Menu", menuId))).flatMap(m -> {
                    change.accept(m);
                    final List<DomainEvent> raised = new ArrayList<>(m.pullDomainEvents());
                    return menus.save(tenantId, m)
                            .flatMap(x -> raised.isEmpty() ? Uni.createFrom().voidItem() : events.publish(tenantId, raised))
                            .replaceWith(m.venueId());
                }))).flatMap(venue -> menuCache.invalidate(CacheKey.of(MENU_CACHE, tenantId, venue.value().toString())));
    }

    // ---------------------------------------------------------------- reads

    /** The one PUBLISHED menu of a venue (most recently updated if several), served through the cache. */
    public Uni<MenuView> publishedMenu(UUID tenantId, UUID venueId) {
        return menuCache.getOrLoad(CacheKey.of(MENU_CACHE, tenantId, venueId.toString()), () ->
                menus.findByVenue(tenantId, VenueId.of(venueId)).map(list -> list.stream()
                        .filter(m -> m.status() == Menu.Status.PUBLISHED)
                        .max(java.util.Comparator.comparing(m -> m.getUpdatedAt()))
                        .map(MenuView::of).orElse(null)));
    }

    public Uni<Menu> menu(UUID tenantId, UUID menuId) {
        return menus.find(tenantId, MenuId.of(menuId)).map(o -> o.orElseThrow(() -> new NotFoundException("Menu", menuId)));
    }
}
