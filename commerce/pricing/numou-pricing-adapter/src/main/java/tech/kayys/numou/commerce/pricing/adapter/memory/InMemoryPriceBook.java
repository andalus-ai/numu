package tech.kayys.numou.commerce.pricing.adapter.memory;

import tech.kayys.numou.commerce.offering.domain.ProductOfferingId;
import tech.kayys.numou.commerce.pricing.spi.port.PriceBookPort;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory price book: one base price row per offering.
 */
public final class InMemoryPriceBook implements PriceBookPort {

    private final Map<ProductOfferingId, Money> basePrices = new ConcurrentHashMap<>();

    public void put(ProductOfferingId offeringId, Money basePrice) {
        Objects.requireNonNull(offeringId);
        Objects.requireNonNull(basePrice);
        basePrices.put(offeringId, basePrice);
    }

    @Override
    public CompletionStage<Optional<Money>> findBasePrice(ProductOfferingId offeringId) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(basePrices.get(offeringId)));
    }
}
