package tech.kayys.syirkah.construction.domain.boq;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.*;

class BoqCalculationPolicyTest {
    private final BoqCalculationPolicy policy = new BoqCalculationPolicy("boq-calc-v1", 2, RoundingMode.HALF_UP);

    @Test void calculatesAndRoundsLineAmount() {
        var quantity = new BoqQuantity(new BigDecimal("1.235"), "m3");
        var rate = new BoqRate(new BigDecimal("10.00"), Currency.getInstance("IDR"), " M3 ");
        assertEquals(new BigDecimal("12.35"), policy.calculateLineAmount(quantity, rate));
    }

    @Test void scalarAdapterOverloadUsesTheSameRoundingAndUnitRules() {
        assertEquals(new BigDecimal("12.35"), policy.calculateLineAmount(
                new BigDecimal("1.235"), "m3", new BigDecimal("10.00"), " M3 "));
        assertThrows(IllegalArgumentException.class, () -> policy.calculateLineAmount(
                new BigDecimal("1"), "m3", new BigDecimal("10"), "kg"));
    }

    @Test void rejectsMismatchedUnits() {
        var quantity = new BoqQuantity(new BigDecimal("2"), "m3");
        var rate = new BoqRate(new BigDecimal("10"), Currency.getInstance("IDR"), "kg");
        assertThrows(IllegalArgumentException.class, () -> policy.calculateLineAmount(quantity, rate));
    }
}
