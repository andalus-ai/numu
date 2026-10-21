package tech.kayys.numou.purchasing.domain.procurement;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PoLineTest {

    @Test
    void accepts_valid_partial_receipt_and_completes_line() {
        PoLine line = PoLine.of("1", "SKU-1", "Widget",
                new BigDecimal("10"), Money.zero("IDR"));

        PoLine partial = line.withReceived(new BigDecimal("4"));
        assertEquals(new BigDecimal("4"), partial.receivedQuantity());
        assertFalse(partial.isFullyReceived());

        PoLine complete = partial.withReceived(new BigDecimal("6"));
        assertEquals(new BigDecimal("10"), complete.receivedQuantity());
        assertTrue(complete.isFullyReceived());
        assertEquals(BigDecimal.ZERO, line.receivedQuantity());
    }

    @Test
    void rejects_zero_or_negative_receipt_delta() {
        PoLine line = PoLine.of("1", "SKU-1", "Widget",
                new BigDecimal("10"), Money.zero("IDR"));

        assertThrows(IllegalArgumentException.class, () -> line.withReceived(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> line.withReceived(new BigDecimal("-1")));
    }

    @Test
    void rejects_over_receipt_without_mutating_original_line() {
        PoLine line = PoLine.of("1", "SKU-1", "Widget",
                new BigDecimal("10"), Money.zero("IDR"));
        PoLine partial = line.withReceived(new BigDecimal("9"));

        assertThrows(IllegalArgumentException.class,
                () -> partial.withReceived(new BigDecimal("2")));
        assertEquals(new BigDecimal("9"), partial.receivedQuantity());
    }

    @Test
    void rejects_invalid_ordered_and_received_quantities() {
        assertThrows(IllegalArgumentException.class, () ->
                PoLine.of("1", "SKU-1", "Widget", BigDecimal.ZERO, Money.zero("IDR")));
        assertThrows(IllegalArgumentException.class, () ->
                new PoLine("1", "SKU-1", "Widget", new BigDecimal("10"),
                        new BigDecimal("-1"), Money.zero("IDR")));
        assertThrows(IllegalArgumentException.class, () ->
                new PoLine("1", "SKU-1", "Widget", new BigDecimal("10"),
                        new BigDecimal("11"), Money.zero("IDR")));
    }
}
