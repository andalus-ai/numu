package tech.kayys.numou.commerce.pricing.spi.port;

import tech.kayys.numou.commerce.offering.domain.ProductOfferingId;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Base price lookup per offering (the price-list row).
 * Same product, different offering → different base price.
 */
public interface PriceBookPort {

    CompletionStage<Optional<Money>> findBasePrice(ProductOfferingId offeringId);
}
