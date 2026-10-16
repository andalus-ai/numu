package tech.kayys.syirkah.commerce.subscription.adapter.memory;

import tech.kayys.syirkah.commerce.subscription.domain.Subscription;
import tech.kayys.syirkah.commerce.subscription.domain.SubscriptionId;
import tech.kayys.syirkah.commerce.subscription.spi.port.SubscriptionRepositoryPort;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory subscription store for tests, demos and single-process
 * deployments; swap for a JDBC adapter without touching domain or
 * application layers.
 */
public final class InMemorySubscriptionRepository
        implements SubscriptionRepositoryPort {

    private final Map<SubscriptionId, Subscription> store = new ConcurrentHashMap<>();

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
}
