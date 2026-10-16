package tech.kayys.syirkah.commerce.pricing.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.configuration.domain.ProductConfiguration;
import tech.kayys.syirkah.commerce.configuration.domain.SelectedOption;
import tech.kayys.syirkah.commerce.offering.domain.ChannelId;
import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.Quantity;
import tech.kayys.syirkah.foundation.domain.valueobject.Unit;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PriceResult invariant and PricingContext shape")
class PriceResultTest {

    @Test
    void latteBreakdownMatchesBlueprintExample() {
        var base = Money.of(new BigDecimal("30000"), "IDR");
        var adjustments = List.of(
                new PriceAdjustment(Money.of(new BigDecimal("5000"), "IDR"), "SIZE:LARGE"),
                new PriceAdjustment(Money.of(new BigDecimal("7000"), "IDR"), "MILK:OAT"));

        var result = PriceResult.of(base, adjustments);

        assertEquals(new BigDecimal("42000"), result.finalPrice().amount());
        assertEquals(2, result.adjustments().size());
    }

    @Test
    void rejectsInconsistentFinalPrice() {
        var base = Money.of(new BigDecimal("30000"), "IDR");

        assertThrows(IllegalArgumentException.class, () -> new PriceResult(
                base,
                List.of(new PriceAdjustment(
                        Money.of(new BigDecimal("5000"), "IDR"), "SIZE:LARGE")),
                Money.of(new BigDecimal("99999"), "IDR")));
    }

    @Test
    void pricingContextCarriesConfiguration() {
        var configuration = ProductConfiguration.of(
                ProductId.generate(),
                List.of(new SelectedOption("SIZE", "LARGE")));

        var context = new PricingContext(
                ProductOfferingId.generate(),
                Quantity.of(2, Unit.of("CUP")),
                "CUST-1",
                ChannelId.of("POS"),
                configuration,
                Instant.now());

        assertEquals(1, context.configuration().options().size());
    }
}
