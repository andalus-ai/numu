package tech.kayys.syirkah.restaurant.order.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.restaurant.kernel.domain.*;
import tech.kayys.syirkah.aggregate.domain.*;
import tech.kayys.syirkah.restaurant.order.domain.event.CourseFired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderDomainTest {

    static final Instant T0 = Instant.parse("2026-10-07T12:00:00Z");
    static final UUID GRILL = UUID.randomUUID();
    static final PricingPolicy IDR = new PricingPolicy("IDR", 0, new BigDecimal("0.11"), new BigDecimal("0.05"), true);

    static LineSpec dish(String name, long price, OrderLine.Modifier... mods) {
        return new LineSpec(UUID.randomUUID(), name, Money.of(price, "IDR"), GRILL, 600, Set.of(), List.of(mods));
    }

    static DineInOrder order(PricingPolicy p) {
        return DineInOrder.open(OrderId.generate(), VenueId.generate(), Set.of(DiningTableId.generate()), "T5", "Sari", 2, p, T0);
    }

    @Test
    void indonesianBillAddsServiceChargeThenTaxOnBoth() {
        final var o = order(IDR);
        o.addLine(dish("nasi goreng", 50_000), 2, null, "", 1, T0);
        final var t = o.totals();
        assertThat(t.subtotal()).isEqualTo(Money.of(100_000, "IDR"));
        assertThat(t.serviceCharge()).isEqualTo(Money.of(5_000, "IDR"));
        assertThat(t.tax()).isEqualTo(Money.of(11_550, "IDR"));   // 11% of (100,000 + 5,000)
        assertThat(t.total()).isEqualTo(Money.of(116_550, "IDR"));
    }

    @Test
    void taxCanExcludeTheServiceCharge() {
        final var o = order(new PricingPolicy("IDR", 0, new BigDecimal("0.10"), new BigDecimal("0.05"), false));
        o.addLine(dish("satay", 100_000), 1, null, "", 1, T0);
        assertThat(o.totals().tax()).isEqualTo(Money.of(10_000, "IDR"));
        assertThat(o.totals().total()).isEqualTo(Money.of(115_000, "IDR"));
    }

    @Test
    void discountReducesTheBaseBeforeChargesAndTax() {
        final var o = order(IDR);
        o.addLine(dish("steak", 200_000), 1, null, "", 1, T0);
        o.fireCourse(1, T0);
        o.applyDiscount(new BigDecimal("10"), "manager-1", T0);
        final var t = o.totals();
        assertThat(t.discount()).isEqualTo(Money.of(20_000, "IDR"));
        assertThat(t.serviceCharge()).isEqualTo(Money.of(9_000, "IDR"));  // 5% of 180,000
        assertThat(t.tax()).isEqualTo(Money.of(20_790, "IDR"));           // 11% of 189,000
        assertThat(t.total()).isEqualTo(Money.of(209_790, "IDR"));
    }

    @Test
    void modifiersArePricedFromTheMenuNotTheCaller() {
        final var o = order(IDR);
        final var spec = dish("burger", 60_000, new OrderLine.Modifier("extra cheese", Money.of(8_000, "IDR")));
        final var id = o.addLine(spec, 2, List.of("Extra Cheese"), "", 1, T0);
        assertThat(o.line(id).unitTotal()).isEqualTo(Money.of(68_000, "IDR"));
        assertThat(o.totals().subtotal()).isEqualTo(Money.of(136_000, "IDR"));
        assertThatThrownBy(() -> o.addLine(spec, 1, List.of("free truffle"), "", 1, T0)).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void roundingIsHalfUpPerComponentAndTotalIsSumOfRoundedParts() {
        final var o = order(new PricingPolicy("IDR", 0, new BigDecimal("0.11"), new BigDecimal("0.05"), true));
        o.addLine(dish("odd", 33_333), 1, null, "", 1, T0);
        final var t = o.totals();
        assertThat(t.serviceCharge().amount()).isEqualByComparingTo("1667");   // 1666.65 -> 1667
        assertThat(t.tax().amount()).isEqualByComparingTo("3850");             // 11% of 35,000 = 3850
        assertThat(t.total().amount()).isEqualByComparingTo(t.subtotal().amount().add(t.serviceCharge().amount()).add(t.tax().amount()));
    }

    @Test
    void twoDecimalCurrenciesKeepCents() {
        final var o = order(new PricingPolicy("USD", 2, new BigDecimal("0.0825"), BigDecimal.ZERO, false));
        o.addLine(new LineSpec(UUID.randomUUID(), "burger", Money.of(new BigDecimal("12.99"), "USD"), GRILL, 600, Set.of(), List.of()), 3, null, "", 1, T0);
        assertThat(o.totals().subtotal().amount()).isEqualByComparingTo("38.97");
        assertThat(o.totals().tax().amount()).isEqualByComparingTo("3.22"); // 38.97 x 8.25% = 3.215025 -> rounds up
    }

    // ---- bill splitting ----

    @Test
    void splitSharesAlwaysAddUpExactly() {
        final var o = order(IDR);
        o.addLine(dish("feast", 99_999), 1, null, "", 1, T0);
        final var total = o.totals().total();
        for (int ways = 1; ways <= 13; ways++) {
            final var shares = o.totals().splitEvenly(ways, 0);
            assertThat(shares).hasSize(ways);
            assertThat(shares.stream().map(Money::amount).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(total.amount());
            final var min = shares.stream().map(Money::amount).min(Comparator.naturalOrder()).orElseThrow();
            final var max = shares.stream().map(Money::amount).max(Comparator.naturalOrder()).orElseThrow();
            assertThat(max.subtract(min)).isLessThanOrEqualTo(BigDecimal.ONE); // fair: no payer is more than one rupiah off
        }
        assertThatThrownBy(() -> o.totals().splitEvenly(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- lifecycle ----

    @Test
    void fireCoursePublishesKitchenPayloadAndOnlyFiresThatCourse() {
        final var o = order(IDR); o.pullDomainEvents();
        final var a = o.addLine(dish("soup", 30_000), 1, null, "no pepper", 1, T0);
        o.addLine(dish("steak", 120_000), 1, null, "", 2, T0);
        o.fireCourse(1, T0);
        final var fired = (CourseFired) o.pullDomainEvents().get(0);
        assertThat(fired.lines()).hasSize(1);
        assertThat(fired.lines().get(0).lineId()).isEqualTo(a);
        assertThat(fired.lines().get(0).notes()).isEqualTo("no pepper");
        assertThat(fired.tableLabel()).isEqualTo("T5");
        assertThat(o.line(a).status()).isEqualTo(OrderLine.Status.FIRED);
        assertThatThrownBy(() -> o.fireCourse(1, T0)).isInstanceOf(BusinessRuleViolation.class); // nothing left
    }

    @Test
    void sentLinesCannotBeEditedOnlyVoidedWithApproval() {
        final var o = order(IDR);
        final var id = o.addLine(dish("steak", 120_000), 1, null, "", 1, T0);
        o.fireCourse(1, T0);
        assertThatThrownBy(() -> o.changeQuantity(id, 2, T0)).isInstanceOf(InvalidStateException.class);
        assertThatThrownBy(() -> o.removePendingLine(id, T0)).isInstanceOf(InvalidStateException.class);
        assertThatThrownBy(() -> o.voidLine(id, "wrong dish", null, T0)).isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("approval");
        o.voidLine(id, "wrong dish", "manager-1", T0);
        assertThat(o.totals().subtotal().amount().signum()).isZero();
        o.voidLine(id, "wrong dish", "manager-1", T0); // idempotent
    }

    @Test
    void pendingLinesAreRemovedNotVoided() {
        final var o = order(IDR);
        final var id = o.addLine(dish("steak", 120_000), 1, null, "", 1, T0);
        assertThatThrownBy(() -> o.voidLine(id, "x", "m", T0)).isInstanceOf(InvalidStateException.class);
        o.changeQuantity(id, 3, T0);
        assertThat(o.line(id).quantity()).isEqualTo(3);
        o.removePendingLine(id, T0);
        assertThat(o.lines()).isEmpty();
    }

    @Test
    void kitchenCallbacksAreIdempotent() {
        final var o = order(IDR);
        final var id = o.addLine(dish("steak", 120_000), 1, null, "", 1, T0);
        assertThatThrownBy(() -> o.markLineReady(id, T0)).isInstanceOf(InvalidStateException.class); // not sent yet
        o.fireCourse(1, T0);
        o.markLineReady(id, T0); o.markLineReady(id, T0);
        assertThat(o.line(id).status()).isEqualTo(OrderLine.Status.READY);
        o.markLineServed(id, T0); o.markLineServed(id, T0); o.markLineReady(id, T0); // late duplicate must not regress SERVED
        assertThat(o.line(id).status()).isEqualTo(OrderLine.Status.SERVED);
    }

    @Test
    void billNeedsSentItemsAndFreezesTheOrder() {
        final var o = order(IDR);
        assertThatThrownBy(() -> o.requestBill(T0)).hasMessageContaining("nothing to bill");
        o.addLine(dish("steak", 120_000), 1, null, "", 1, T0);
        assertThatThrownBy(() -> o.requestBill(T0)).hasMessageContaining("Unsent");
        o.fireCourse(1, T0);
        o.requestBill(T0);
        assertThatThrownBy(() -> o.addLine(dish("tea", 10_000), 1, null, "", 1, T0)).isInstanceOf(InvalidStateException.class);
        o.reopen(T0);
        o.addLine(dish("tea", 10_000), 1, null, "", 1, T0); // guest wants tea after all
    }

    @Test
    void settleOnlyAfterBillAndPublishesPaidWithTables() {
        final var o = order(IDR);
        o.addLine(dish("steak", 120_000), 1, null, "", 1, T0); o.fireCourse(1, T0);
        assertThatThrownBy(() -> o.settle("pay-1", T0)).isInstanceOf(InvalidStateException.class);
        o.requestBill(T0); o.pullDomainEvents();
        o.settle("pay-1", T0);
        assertThat(o.status()).isEqualTo(OrderStatus.PAID);
        assertThat(o.pullDomainEvents()).extracting(e -> e.eventType()).containsExactly("restaurant.order.paid");
        assertThatThrownBy(() -> o.settle("pay-2", T0)).isInstanceOf(InvalidStateException.class);
        assertThatThrownBy(() -> o.voidOrder("x", "m", T0)).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void voidingAnOrderThatWentToTheKitchenNeedsApproval() {
        final var fresh = order(IDR);
        fresh.addLine(dish("steak", 120_000), 1, null, "", 1, T0);
        fresh.voidOrder("guest left", null, T0); // nothing sent: no approval needed
        assertThat(fresh.status()).isEqualTo(OrderStatus.VOIDED);

        final var sent = order(IDR);
        sent.addLine(dish("steak", 120_000), 1, null, "", 1, T0); sent.fireCourse(1, T0);
        assertThatThrownBy(() -> sent.voidOrder("guest left", null, T0)).hasMessageContaining("approval");
        sent.voidOrder("guest left", "manager-1", T0);
    }

    @Test
    void discountRules() {
        final var o = order(IDR);
        o.addLine(dish("steak", 120_000), 1, null, "", 1, T0);
        assertThatThrownBy(() -> o.applyDiscount(new BigDecimal("10"), "", T0)).hasMessageContaining("approval");
        assertThatThrownBy(() -> o.applyDiscount(new BigDecimal("101"), "m", T0)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> o.applyDiscount(BigDecimal.ZERO, "m", T0)).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void quantityAndCourseAreBounded() {
        final var o = order(IDR);
        assertThatThrownBy(() -> o.addLine(dish("x", 1_000), 0, null, "", 1, T0)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> o.addLine(dish("x", 1_000), 51, null, "", 1, T0)).isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> o.addLine(dish("x", 1_000), 1, null, "", 10, T0)).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void wrongCurrencyItemIsRejected() {
        final var o = order(IDR);
        assertThatThrownBy(() -> o.addLine(new LineSpec(UUID.randomUUID(), "x", Money.of(5, "USD"), GRILL, 60, Set.of(), List.of()), 1, null, "", 1, T0))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void tableTransferEmitsEvent() {
        final var o = order(IDR); o.pullDomainEvents();
        o.transferTable(Set.of(DiningTableId.generate()), "T9", T0);
        assertThat(o.tableLabel()).isEqualTo("T9");
        assertThat(o.pullDomainEvents()).extracting(e -> e.eventType()).containsExactly("restaurant.order.table-transferred");
    }

    @Test
    void policyIsFrozenAtOpeningSoLaterRateChangesDoNotReprice() {
        final var o = order(IDR);
        o.addLine(dish("steak", 100_000), 1, null, "", 1, T0);
        assertThat(o.policy().taxRate()).isEqualByComparingTo("0.11");
        assertThat(DineInOrder.restore(o.snapshot()).totals()).isEqualTo(o.totals());
    }

    @Test
    void snapshotRoundTripPreservesEverythingIncludingEventFreeRestore() {
        final var o = order(IDR);
        final var spec = dish("burger", 60_000, new OrderLine.Modifier("extra cheese", Money.of(8_000, "IDR")));
        o.addLine(spec, 2, List.of("extra cheese"), "well done", 1, T0); o.addLine(dish("tea", 10_000), 1, null, "", 2, T0);
        o.fireCourse(1, T0); o.applyDiscount(new BigDecimal("5"), "m1", T0);
        final var back = DineInOrder.restore(o.snapshot());
        assertThat(back.snapshot()).isEqualTo(o.snapshot());
        assertThat(back.totals()).isEqualTo(o.totals());
        assertThat(back.pullDomainEvents()).isEmpty();
    }
}
