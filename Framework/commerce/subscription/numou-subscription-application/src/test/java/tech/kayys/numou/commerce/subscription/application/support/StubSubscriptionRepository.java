package tech.kayys.syirkah.commerce.subscription.application.support;

import tech.kayys.syirkah.commerce.subscription.domain.Subscription;
import tech.kayys.syirkah.commerce.subscription.domain.SubscriptionId;
import tech.kayys.syirkah.commerce.subscription.spi.port.SubscriptionRepositoryPort;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Test double for the subscription repository.
 */
public final class StubSubscriptionRepository implements SubscriptionRepositoryPort {

    private final Map<SubscriptionId, Subscription> store = new LinkedHashMap<>();

    @Override
    public CompletionStage<Subscription> save(Subscription aggregate) {
        store.put(aggregate.id(), aggregate);
        return CompletableFuture.completedFuture(aggregate);
    }

    @Override
    public CompletionStage<Optional<Subscription>> findById(SubscriptionId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id)));
    }

    @Override
    public CompletionStage<Boolean> existsById(SubscriptionId id) {
        return CompletableFuture.completedFuture(store.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(Subscription aggregate) {
        store.remove(aggregate.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(SubscriptionId id) {
        store.remove(id);
        return CompletableFuture.completedFuture(null);
    }

    public Optional<Subscription> get(SubscriptionId id) {
        return Optional.ofNullable(store.get(id));
    }
}
