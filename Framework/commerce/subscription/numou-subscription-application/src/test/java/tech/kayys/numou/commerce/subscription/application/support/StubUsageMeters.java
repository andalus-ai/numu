package tech.kayys.syirkah.commerce.subscription.application.support;

import tech.kayys.syirkah.commerce.subscription.domain.SubscriptionId;
import tech.kayys.syirkah.commerce.subscription.domain.UsageMeter;
import tech.kayys.syirkah.commerce.subscription.spi.port.UsageMeterPort;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Test double for period-scoped usage meters.
 */
public final class StubUsageMeters implements UsageMeterPort {

    private record Key(SubscriptionId subscriptionId, DateRange period) {
    }

    private final Map<Key, UsageMeter> store = new LinkedHashMap<>();

    @Override
    public CompletionStage<Optional<UsageMeter>> find(
            SubscriptionId subscriptionId, DateRange period) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(store.get(new Key(subscriptionId, period))));
    }

    @Override
    public CompletionStage<Void> save(UsageMeter meter) {
        store.put(new Key(meter.subscriptionId(), meter.period()), meter);
        return CompletableFuture.completedFuture(null);
    }
}
