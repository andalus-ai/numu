package tech.kayys.numou.cache;

import io.smallrye.mutiny.Uni;

import java.util.function.Supplier;

/**
 * Reactive, tenant-safe read-through cache port.
 *
 * <p>Contract every implementation must honour:
 * <ul>
 *   <li><b>single flight</b> - concurrent misses for one key run the loader once;</li>
 *   <li><b>failures are never cached</b> - the next call retries the loader;</li>
 *   <li><b>invalidate wins</b> - a load that started before an invalidation must
 *       not repopulate the cache with the value it read before that invalidation.</li>
 * </ul>
 *
 * @param <V> cached value type
 */
public interface Cache<V> {

    /** Returns the cached value or loads, caches and returns it. A null load result is a cacheable "absent". */
    Uni<V> getOrLoad(CacheKey key, Supplier<Uni<V>> loader);

    Uni<Void> invalidate(CacheKey key);

    /** Drops every entry of one tenant in this cache (e.g. after a bulk import). */
    Uni<Void> invalidateTenant(java.util.UUID tenantId);

    CacheStats stats();
}
