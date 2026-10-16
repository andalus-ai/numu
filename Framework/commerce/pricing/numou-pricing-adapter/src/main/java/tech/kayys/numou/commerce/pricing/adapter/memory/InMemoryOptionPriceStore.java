package tech.kayys.numou.commerce.pricing.adapter.memory;

import tech.kayys.numou.commerce.offering.domain.ProductOfferingId;
import tech.kayys.numou.commerce.pricing.spi.port.OptionPricePort;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory option adjustment store, keyed by
 * offering + group + option (per-offering commercial rules).
 */
public final class InMemoryOptionPriceStore implements OptionPricePort {

    private record OptionKey(
            ProductOfferingId offeringId,
            String groupCode,
            String optionCode) {
    }

    private final Map<OptionKey, Money> adjustments = new ConcurrentHashMap<>();

    public void put(
            ProductOfferingId offeringId,
            String groupCode,
            String optionCode,
            Money delta
    ) {
        Objects.requireNonNull(offeringId);
        Objects.requireNonNull(groupCode);
        Objects.requireNonNull(optionCode);
        Objects.requireNonNull(delta);
        adjustments.put(new OptionKey(offeringId, groupCode, optionCode), delta);
    }

    @Override
    public CompletionStage<Optional<Money>> findAdjustment(
            ProductOfferingId offeringId,
            String groupCode,
            String optionCode
    ) {
        return CompletableFuture.completedFuture(Optional.ofNullable(
                adjustments.get(new OptionKey(offeringId, groupCode, optionCode))));
    }
}
