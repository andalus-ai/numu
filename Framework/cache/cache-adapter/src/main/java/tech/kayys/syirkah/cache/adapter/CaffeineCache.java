package tech.kayys.syirkah.cache.adapter;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.cache.Cache;
import tech.kayys.syirkah.cache.CacheKey;
import tech.kayys.syirkah.cache.CachePolicy;
import tech.kayys.syirkah.cache.CacheStats;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Caffeine-backed {@link Cache}.
 *
 * <p>Freshness is decided here (not by Caffeine's own expiry) so absent results
 * can have a shorter TTL than present ones; Caffeine only bounds size and acts as
 * a backstop that drops entries after the longer of the two TTLs.
 *
 * <p>Invalidation safety: every invalidation bumps {@code generation}. A load
 * remembers the generation it started under and only publishes its result if the
 * generation is unchanged, so a slow load can never resurrect data that was
 * invalidated while it was running.
 */
public final class CaffeineCache<V> implements Cache<V> {

    private record Entry<V>(V value, long expiresAtNanos) { }

    private final String namespace;
    private final CachePolicy policy;
    private final LongSupplier nanoTime;
    private final com.github.benmanes.caffeine.cache.Cache<String, Entry<V>> store;
    private final ConcurrentHashMap<String, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong loads = new AtomicLong();
    private final AtomicLong loadFailures = new AtomicLong();
    private final AtomicLong invalidations = new AtomicLong();

    public CaffeineCache(String namespace, CachePolicy policy) {
        this(namespace, policy, System::nanoTime);
    }

    /** Test seam: inject a controllable clock. */
    public CaffeineCache(String namespace, CachePolicy policy, LongSupplier nanoTime) {
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
        this.policy = Objects.requireNonNull(policy, "policy cannot be null");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime cannot be null");
        final long backstopNanos = Math.max(policy.ttl().toNanos(), policy.negativeTtl().toNanos());
        this.store = Caffeine.newBuilder()
                .maximumSize(policy.maxEntries())
                .expireAfterWrite(backstopNanos, TimeUnit.NANOSECONDS)
                .build();
    }

    @Override
    public Uni<V> getOrLoad(CacheKey key, Supplier<Uni<V>> loader) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(loader, "loader cannot be null");
        requireNamespace(key);
        final String storageKey = key.asString();

        return Uni.createFrom().deferred(() -> {
            final Entry<V> cached = store.getIfPresent(storageKey);
            if (cached != null && cached.expiresAtNanos() > nanoTime.getAsLong()) {
                hits.incrementAndGet();
                return Uni.createFrom().item(cached.value());
            }
            misses.incrementAndGet();
            return Uni.createFrom().completionStage(() -> load(storageKey, loader));
        });
    }

    private CompletableFuture<V> load(String storageKey, Supplier<Uni<V>> loader) {
        final CompletableFuture<V> mine = new CompletableFuture<>();
        final CompletableFuture<V> existing = inFlight.putIfAbsent(storageKey, mine);
        if (existing != null) {
            return existing; // single flight: join the load already running
        }
        final long startedUnder = generation.get();
        loads.incrementAndGet();
        try {
            loader.get().subscribe().asCompletionStage().whenComplete((value, failure) -> {
                try {
                    if (failure != null) {
                        loadFailures.incrementAndGet();
                    } else if (generation.get() == startedUnder) {
                        publish(storageKey, value);
                    }
                } finally {
                    inFlight.remove(storageKey, mine);
                    if (failure != null) {
                        mine.completeExceptionally(failure);
                    } else {
                        mine.complete(value);
                    }
                }
            });
        } catch (RuntimeException e) { // loader threw synchronously
            loadFailures.incrementAndGet();
            inFlight.remove(storageKey, mine);
            mine.completeExceptionally(e);
        }
        return mine;
    }

    private void publish(String storageKey, V value) {
        final var ttl = value == null ? policy.negativeTtl() : policy.ttl();
        if (ttl.isZero()) {
            return; // negative caching disabled
        }
        store.put(storageKey, new Entry<>(value, nanoTime.getAsLong() + ttl.toNanos()));
    }

    @Override
    public Uni<Void> invalidate(CacheKey key) {
        requireNamespace(key);
        return Uni.createFrom().item(() -> {
            generation.incrementAndGet();
            store.invalidate(key.asString());
            invalidations.incrementAndGet();
            return null;
        });
    }

    @Override
    public Uni<Void> invalidateTenant(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        final String prefix = CacheKey.prefix(namespace, tenantId);
        return Uni.createFrom().item(() -> {
            generation.incrementAndGet();
            store.asMap().keySet().removeIf(k -> k.startsWith(prefix)); // O(n): acceptable for rare bulk ops
            invalidations.incrementAndGet();
            return null;
        });
    }

    @Override
    public CacheStats stats() {
        return new CacheStats(hits.get(), misses.get(), loads.get(), loadFailures.get(), invalidations.get());
    }

    private void requireNamespace(CacheKey key) {
        if (!namespace.equals(key.namespace())) {
            throw new IllegalArgumentException(
                    "Key namespace '" + key.namespace() + "' does not belong to cache '" + namespace + "'");
        }
    }
}
