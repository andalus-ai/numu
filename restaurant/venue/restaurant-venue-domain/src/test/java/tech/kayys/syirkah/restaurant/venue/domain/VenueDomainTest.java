package tech.kayys.syirkah.restaurant.venue.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.restaurant.kernel.domain.*;
import tech.kayys.syirkah.aggregate.domain.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VenueDomainTest {

    static final Instant T0 = Instant.parse("2026-10-07T12:00:00Z");
    static final VenueId VENUE = VenueId.generate();

    // ---- venue ----

    @Test
    void indonesianDefaultsAreConfigurableNotHardcoded() {
        final var v = Venue.indonesianDefaults(VENUE, "Warung Maju", T0);
        assertThat(v.taxRate()).isEqualByComparingTo("0.11");
        assertThat(v.fractionDigits()).isZero();
        v.changeTaxPolicy(new BigDecimal("0.12"), new BigDecimal("0.07"), false, T0.plusSeconds(1));
        assertThat(v.taxRate()).isEqualByComparingTo("0.12");
        assertThatThrownBy(() -> v.changeTaxPolicy(new BigDecimal("1.5"), BigDecimal.ZERO, true, T0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stationsMustHaveUniqueNamesAndSaneParallelism() {
        final var v = Venue.indonesianDefaults(VENUE, "Warung", T0);
        v.addStation("Grill", 3, T0);
        assertThatThrownBy(() -> v.addStation("grill", 2, T0)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> v.addStation("Fry", 0, T0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void venueSnapshotRoundTrip() {
        final var v = Venue.indonesianDefaults(VENUE, "Warung", T0); v.addStation("Grill", 3, T0);
        assertThat(Venue.restore(v.snapshot()).snapshot()).isEqualTo(v.snapshot());
    }

    // ---- tables ----

    DiningTable table() { return DiningTable.create(DiningTableId.generate(), VENUE, "T5", "patio", 2, 4, Set.of(), T0); }

    @Test
    void tableLifecycleSeatReleaseClean() {
        final var t = table();
        t.seat(OrderId.generate(), 3, T0);
        assertThat(t.status()).isEqualTo(TableStatus.OCCUPIED);
        assertThatThrownBy(() -> t.seat(OrderId.generate(), 2, T0)).isInstanceOf(InvalidStateException.class); // no double seating
        t.release(T0);
        assertThat(t.status()).isEqualTo(TableStatus.DIRTY);
        assertThat(t.currentOrderId()).isNull();
        assertThatThrownBy(() -> t.seat(OrderId.generate(), 2, T0)).isInstanceOf(InvalidStateException.class); // must be cleaned first
        t.markClean(T0);
        assertThat(t.status()).isEqualTo(TableStatus.AVAILABLE);
        assertThat(t.pullDomainEvents()).extracting(e -> e.eventType())
                .containsExactly("restaurant.table.seated", "restaurant.table.released", "restaurant.table.cleaned");
    }

    @Test
    void cannotSeatMoreThanTheTableHolds() {
        assertThatThrownBy(() -> table().seat(OrderId.generate(), 5, T0)).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void blockingRules() {
        final var t = table();
        t.seat(OrderId.generate(), 2, T0);
        assertThatThrownBy(() -> t.block("broken leg", T0)).isInstanceOf(InvalidStateException.class);
        t.release(T0); t.block("broken leg", T0);
        assertThatThrownBy(() -> t.seat(OrderId.generate(), 2, T0)).isInstanceOf(InvalidStateException.class);
        t.unblock(T0);
        assertThat(t.status()).isEqualTo(TableStatus.AVAILABLE);
    }

    @Test
    void tableSnapshotRoundTripKeepsStatusAndOrder() {
        final var t = table(); t.seat(OrderId.generate(), 2, T0);
        assertThat(DiningTable.restore(t.snapshot()).snapshot()).isEqualTo(t.snapshot());
    }

    // ---- menu ----

    StationId station = StationId.generate();

    MenuItem item(String name, long price) {
        return Menu.newItem(name, "", Money.of(price, "IDR"), station, Duration.ofMinutes(10), Set.of("peanut"),
                List.of(new MenuItem.Modifier("extra spicy", Money.of(0, "IDR"))), null);
    }

    Menu menu() { return Menu.create(MenuId.generate(), VENUE, "Dinner", T0); }

    @Test
    void emptyMenuCannotBePublished() {
        final var m = menu(); m.addSection("Mains", T0);
        assertThatThrownBy(() -> m.publish(T0)).isInstanceOf(BusinessRuleViolation.class);
        m.addItem("Mains", item("rendang", 85_000), T0);
        m.publish(T0);
        assertThat(m.status()).isEqualTo(Menu.Status.PUBLISHED);
    }

    @Test
    void itemNamesAreUniquePerMenuAndSectionsMustExist() {
        final var m = menu(); m.addSection("Mains", T0); m.addSection("Drinks", T0);
        m.addItem("Mains", item("rendang", 85_000), T0);
        assertThatThrownBy(() -> m.addItem("Drinks", item("Rendang", 1), T0)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> m.addItem("Desserts", item("cake", 1), T0)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> m.addSection("mains".replace('m', 'M'), T0)).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void priceChangeEmitsOldAndNewPriceAndRejectsNegatives() {
        final var m = menu(); m.addSection("Mains", T0);
        final var it = m.addItem("Mains", item("rendang", 85_000), T0); m.pullDomainEvents();
        m.changePrice(it.id(), Money.of(90_000, "IDR"), T0);
        assertThat(m.find(it.id()).orElseThrow().price()).isEqualTo(Money.of(90_000, "IDR"));
        assertThat(m.pullDomainEvents().get(0).toString()).contains("oldPrice=85000", "newPrice=90000");
        assertThatThrownBy(() -> m.changePrice(it.id(), Money.of(5, "USD"), T0)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> item("bad", -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void archivedMenusAreReadOnly() {
        final var m = menu(); m.addSection("Mains", T0); m.addItem("Mains", item("rendang", 85_000), T0); m.archive(T0);
        assertThatThrownBy(() -> m.addSection("X", T0)).isInstanceOf(InvalidStateException.class);
        assertThatThrownBy(() -> m.publish(T0)).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void menuSnapshotRoundTrip() {
        final var m = menu(); m.addSection("Mains", T0); m.addItem("Mains", item("rendang", 85_000), T0); m.publish(T0);
        final var back = Menu.restore(m.snapshot());
        assertThat(back.snapshot()).isEqualTo(m.snapshot());
        assertThat(back.pullDomainEvents()).isEmpty();
    }
}
