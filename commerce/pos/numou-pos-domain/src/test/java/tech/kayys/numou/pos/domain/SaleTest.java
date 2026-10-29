package tech.kayys.numou.pos.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SaleTest {

    private static Sale openSale() {
        return Sale.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), SaleChannel.POS, "IDR",
                Instant.parse("2026-10-29T08:00:00Z"));
    }

    private static SaleLine line(String ref, String quantity, String price) {
        return new SaleLine(UUID.randomUUID(), ref, ref, ref,
                new BigDecimal(quantity), "each",
                new Money(new BigDecimal(price), "IDR"));
    }

    @Test
    void snapshotsLinesAndComputesUnroundedSubtotal() {
        Sale sale = openSale()
                .addLine(line("coffee", "2", "15000"))
                .addLine(line("cake", "1.5", "10000"));

        assertEquals(new BigDecimal("45000.0"), sale.subtotal().amount());
        assertEquals("IDR", sale.subtotal().currency());
        assertEquals(2, sale.lines().size());
        assertThrows(UnsupportedOperationException.class, () -> sale.lines().clear());
    }

    @Test
    void saleCannotMixCurrenciesOrDuplicateLineIds() {
        Sale sale = openSale().addLine(line("coffee", "1", "15000"));
        SaleLine usd = new SaleLine(UUID.randomUUID(), "tea", "Tea", "TEA",
                BigDecimal.ONE, "each", new Money(new BigDecimal("2"), "USD"));
        assertThrows(IllegalArgumentException.class, () -> sale.addLine(usd));

        SaleLine original = sale.lines().getFirst();
        SaleLine duplicate = new SaleLine(original.id(), "other", "Other", null,
                BigDecimal.ONE, "each", new Money(new BigDecimal("1"), "IDR"));
        assertThrows(IllegalArgumentException.class, () -> sale.addLine(duplicate));
    }

    @Test
    void completingRequiresLinesAndLocksFurtherMutation() {
        Sale empty = openSale();
        assertThrows(IllegalStateException.class,
                () -> empty.complete(Instant.parse("2026-10-29T08:01:00Z")));

        Sale sale = empty.addLine(line("coffee", "1", "15000"));
        Sale completed = sale.complete(Instant.parse("2026-10-29T08:01:00Z"));

        assertEquals(Sale.Status.OPEN, sale.status(), "original snapshot remains unchanged");
        assertEquals(Sale.Status.COMPLETED, completed.status());
        assertThrows(IllegalStateException.class, () -> completed.addLine(line("cake", "1", "10000")));
        assertThrows(IllegalStateException.class,
                () -> completed.complete(Instant.parse("2026-10-29T08:02:00Z")));
    }

    @Test
    void voidIsAllowedOnlyBeforeCompletion() {
        Sale sale = openSale().addLine(line("coffee", "1", "15000"));
        assertEquals(Sale.Status.VOIDED, sale.voidSale().status());
        assertThrows(IllegalStateException.class,
                () -> sale.complete(Instant.parse("2026-10-29T07:59:00Z")));
    }

    @Test
    void saleLineRequiresPositiveQuantityAndNonnegativePrice() {
        assertThrows(IllegalArgumentException.class, () -> line("coffee", "0", "15000"));
        assertThrows(IllegalArgumentException.class, () -> line("coffee", "1", "-1"));
    }

    @Test
    void saleCurrencyIsRequiredEvenBeforeFirstLine() {
        assertEquals("IDR", openSale().subtotal().currency());
        assertThrows(IllegalArgumentException.class, () -> Sale.open(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                SaleChannel.POS, "idr", Instant.parse("2026-10-29T08:00:00Z")));
    }
}
