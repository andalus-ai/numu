package tech.kayys.syirkah.commerce.subscription.adapter.memory;

import tech.kayys.syirkah.commerce.subscription.domain.SubscriptionId;
import tech.kayys.syirkah.commerce.subscription.domain.UsageMeter;
import tech.kayys.syirkah.commerce.subscription.spi.port.UsageMeterPort;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory usage meters keyed by (subscription, period) — renewal
 * creates a new period, so quotas reset naturally.
 */
public final class InMemoryUsageMeterStore implements UsageMeterPort {

    private record Key(SubscriptionId subscriptionId, DateRange period) {
    }

    private final Map<Key, UsageMeter> store = new ConcurrentHashMap<>();

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
