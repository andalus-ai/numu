package tech.kayys.numou.identity.application.support;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.cache.Cache;
import tech.kayys.numou.cache.CacheKey;
import tech.kayys.numou.cache.CacheStats;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * In-memory {@link Cache} test double implementing the port contract
 * (read-through, failures are never cached, invalidate wins) without TTLs -
 * expiry is exercised by the cache-adapter's own tests, invalidation logic
 * by the services under test.
 */
public final class RecordingCache<V> implements Cache<V> {

    private final String namespace;
    private final Map<String, V> store = new ConcurrentHashMap<>();
    private int loads;
    private int invalidations;

    public RecordingCache(String namespace) {
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
    }

    @Override
    public Uni<V> getOrLoad(CacheKey key, Supplier<Uni<V>> loader) {
        return Uni.createFrom().deferred(() -> {
            V cached = store.get(key.asString());
            if (cached != null) {
                return Uni.createFrom().item(cached);
            }
            loads++;
            return loader.get().onItem().invoke(value -> {
                if (value != null) {
                    store.put(key.asString(), value);
                }
            });
        });
    }

    @Override
    public Uni<Void> invalidate(CacheKey key) {
        invalidations++;
        store.remove(key.asString());
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Void> invalidateTenant(UUID tenantId) {
        invalidations++;
        String prefix = CacheKey.prefix(namespace, tenantId);
        store.keySet().removeIf(stored -> stored.startsWith(prefix));
        return Uni.createFrom().voidItem();
    }

    @Override
    public CacheStats stats() {
        return new CacheStats(0, 0, loads, 0, invalidations);
    }

    /** Number of loader invocations - how a test proves a value was (not) served from cache. */
    public int loads() {
        return loads;
    }

    public int invalidations() {
        return invalidations;
    }

    public boolean containsKey(UUID tenantId, String key) {
        return store.containsKey(CacheKey.prefix(namespace, tenantId) + key);
    }
}
