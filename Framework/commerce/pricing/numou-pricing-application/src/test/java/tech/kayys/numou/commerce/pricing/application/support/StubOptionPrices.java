package tech.kayys.syirkah.commerce.pricing.application.support;

import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.commerce.pricing.spi.port.OptionPricePort;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Test double for per-offering option adjustments.
 */
public final class StubOptionPrices implements OptionPricePort {

    private final Map<String, Money> adjustments = new HashMap<>();

    public void put(
            ProductOfferingId offeringId,
            String groupCode,
            String optionCode,
            Money delta
    ) {
        adjustments.put(key(offeringId, groupCode, optionCode), delta);
    }

    @Override
    public CompletionStage<Optional<Money>> findAdjustment(
            ProductOfferingId offeringId,
            String groupCode,
            String optionCode
    ) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(adjustments.get(key(offeringId, groupCode, optionCode))));
    }

    private static String key(
            ProductOfferingId offeringId,
            String groupCode,
            String optionCode
    ) {
        return offeringId.value() + "|" + groupCode + "|" + optionCode;
    }
}
