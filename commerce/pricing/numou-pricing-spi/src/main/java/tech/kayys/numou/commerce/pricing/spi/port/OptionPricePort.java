package tech.kayys.numou.commerce.pricing.spi.port;

import tech.kayys.numou.commerce.offering.domain.ProductOfferingId;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Per-offering commercial effect of one selected option
 * (e.g. OAT_MILK = +7,000 at Shop A, +5,000 at Shop B).
 */
public interface OptionPricePort {

    CompletionStage<Optional<Money>> findAdjustment(
            ProductOfferingId offeringId,
            String groupCode,
            String optionCode);
}
