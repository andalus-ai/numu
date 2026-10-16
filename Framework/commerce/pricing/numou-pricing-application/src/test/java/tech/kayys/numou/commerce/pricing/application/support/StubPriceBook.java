package tech.kayys.numou.commerce.pricing.application.support;

import tech.kayys.numou.commerce.offering.domain.ProductOfferingId;
import tech.kayys.numou.commerce.pricing.spi.port.PriceBookPort;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Test double for the price book.
 */
public final class StubPriceBook implements PriceBookPort {

    private final Map<ProductOfferingId, Money> basePrices = new HashMap<>();

    public void put(ProductOfferingId offeringId, Money basePrice) {
        basePrices.put(offeringId, basePrice);
    }

    @Override
    public CompletionStage<Optional<Money>> findBasePrice(ProductOfferingId offeringId) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(basePrices.get(offeringId)));
    }
}
