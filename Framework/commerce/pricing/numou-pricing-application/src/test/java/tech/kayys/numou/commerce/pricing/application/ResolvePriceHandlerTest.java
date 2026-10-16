package tech.kayys.syirkah.commerce.pricing.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.commerce.configuration.domain.ProductConfiguration;
import tech.kayys.syirkah.commerce.configuration.domain.SelectedOption;
import tech.kayys.syirkah.commerce.offering.domain.ChannelId;
import tech.kayys.syirkah.commerce.pricing.application.handler.ResolvePriceHandler;
import tech.kayys.syirkah.commerce.pricing.application.query.ResolvePriceQuery;
import tech.kayys.syirkah.commerce.pricing.application.support.StubOfferingRepository;
import tech.kayys.syirkah.commerce.pricing.application.support.StubOptionPrices;
import tech.kayys.syirkah.commerce.pricing.application.support.StubPriceBook;
import tech.kayys.syirkah.commerce.pricing.domain.PricingContext;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.Quantity;
import tech.kayys.syirkah.foundation.domain.valueobject.Unit;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ResolvePriceHandler (F&B latte breakdown)")
class ResolvePriceHandlerTest {

    private final StubOfferingRepository offerings = new StubOfferingRepository();
    private final StubPriceBook priceBook = new StubPriceBook();
    private final StubOptionPrices optionPrices = new StubOptionPrices();
    private final ResolvePriceHandler handler =
            new ResolvePriceHandler(offerings, priceBook, optionPrices);

    @Test
    void resolvesLatteWithOptionAdjustments() {
        var productId = ProductId.generate();
        var offering = offerings.activeOffering(productId);
        priceBook.put(offering.id(), Money.of(new BigDecimal("30000"), "IDR"));
        optionPrices.put(
                offering.id(), "SIZE", "LARGE", Money.of(new BigDecimal("5000"), "IDR"));
        optionPrices.put(
                offering.id(), "MILK", "OAT", Money.of(new BigDecimal("7000"), "IDR"));

        var result = handler.handle(new ResolvePriceQuery(new PricingContext(
                        offering.id(),
                        Quantity.of(1, Unit.of("CUP")),
                        null,
                        ChannelId.of("POS"),
                        ProductConfiguration.of(
                                productId,
                                List.of(
                                        new SelectedOption("SIZE", "LARGE"),
                                        new SelectedOption("MILK", "OAT"))),
                        Instant.now())))
                .await().indefinitely();

        assertTrue(result.isSuccess());
        assertEquals(new BigDecimal("42000"), result.orElseThrow().finalPrice().amount());
        assertEquals(2, result.orElseThrow().adjustments().size());
    }

    @Test
    void rejectsDraftOffering() {
        var offering = offerings.draftOffering(ProductId.generate());

        var result = handler.handle(new ResolvePriceQuery(new PricingContext(
                        offering.id(),
                        Quantity.of(1, Unit.of("CUP")),
                        null,
                        ChannelId.of("POS"),
                        ProductConfiguration.empty(offering.productId()),
                        Instant.now())))
                .await().indefinitely();

        assertTrue(result.isFailure());
    }
}
