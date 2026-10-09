package tech.kayys.syirkah.cache.adapter;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.cache.CacheKey;
import tech.kayys.syirkah.cache.CachePolicy;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaffeineCacheTest {

    private static final UUID T1 = UUID.randomUUID();
    private static final UUID T2 = UUID.randomUUID();
    private final AtomicLong clock = new AtomicLong();
    private final CachePolicy policy = new CachePolicy(Duration.ofSeconds(10), Duration.ofSeconds(2), 100);
    private final CaffeineCache<String> cache = new CaffeineCache<>("menu", policy, clock::get);

    private static Uni<String> value(String v) { return Uni.createFrom().item(v); }

    @Test
    void secondReadIsServedFromCache() {
        final var calls = new AtomicInteger();
        final var key = CacheKey.of("menu", T1, "m1");
        for (int i = 0; i < 3; i++) {
            assertThat(cache.getOrLoad(key, () -> { calls.incrementAndGet(); return value("A"); })
                    .await().indefinitely()).isEqualTo("A");
        }
        assertThat(calls).hasValue(1);
        assertThat(cache.stats().hits()).isEqualTo(2);
        assertThat(cache.stats().misses()).isEqualTo(1);
    }

    @Test
    void tenantsNeverShareEntriesForTheSameKey() {
        final var a = cache.getOrLoad(CacheKey.of("menu", T1, "m1"), () -> value("tenant-1-data")).await().indefinitely();
        final var b = cache.getOrLoad(CacheKey.of("menu", T2, "m1"), () -> value("tenant-2-data")).await().indefinitely();
        assertThat(a).isEqualTo("tenant-1-data");
        assertThat(b).isEqualTo("tenant-2-data");
    }

    @Test
    void entriesExpireAfterTtl() {
        final var calls = new AtomicInteger();
        final var key = CacheKey.of("menu", T1, "m1");
        cache.getOrLoad(key, () -> { calls.incrementAndGet(); return value("A"); }).await().indefinitely();
        clock.addAndGet(Duration.ofSeconds(11).toNanos());
        cache.getOrLoad(key, () -> { calls.incrementAndGet(); return value("B"); }).await().indefinitely();
        assertThat(calls).hasValue(2);
    }

    @Test
    void absentResultsAreCachedBrieflyThenReloaded() {
        final var calls = new AtomicInteger();
        final var key = CacheKey.of("menu", T1, "missing");
        cache.getOrLoad(key, () -> { calls.incrementAndGet(); return Uni.createFrom().<String>nullItem(); }).await().indefinitely();
        cache.getOrLoad(key, () -> { calls.incrementAndGet(); return Uni.createFrom().<String>nullItem(); }).await().indefinitely();
        assertThat(calls).hasValue(1);
        clock.addAndGet(Duration.ofSeconds(3).toNanos()); // past negativeTtl (2s)
        cache.getOrLoad(key, () -> { calls.incrementAndGet(); return Uni.createFrom().<String>nullItem(); }).await().indefinitely();
        assertThat(calls).hasValue(2);
    }

    @Test
    void failuresAreNotCached() {
        final var key = CacheKey.of("menu", T1, "m1");
        assertThatThrownBy(() -> cache.getOrLoad(key,
                () -> Uni.<String>createFrom().failure(new IllegalStateException("db down"))).await().indefinitely())
                .isInstanceOf(IllegalStateException.class);
        assertThat(cache.getOrLoad(key, () -> value("recovered")).await().indefinitely()).isEqualTo("recovered");
        assertThat(cache.stats().loadFailures()).isEqualTo(1);
    }

    @Test
    void invalidateForcesReload() {
        final var key = CacheKey.of("menu", T1, "m1");
        cache.getOrLoad(key, () -> value("old")).await().indefinitely();
        cache.invalidate(key).await().indefinitely();
        assertThat(cache.getOrLoad(key, () -> value("new")).await().indefinitely()).isEqualTo("new");
    }

    @Test
    void invalidateTenantOnlyAffectsThatTenant() {
        final var k1 = CacheKey.of("menu", T1, "m1");
        final var k2 = CacheKey.of("menu", T2, "m1");
        cache.getOrLoad(k1, () -> value("t1")).await().indefinitely();
        cache.getOrLoad(k2, () -> value("t2")).await().indefinitely();
        cache.invalidateTenant(T1).await().indefinitely();
        assertThat(cache.getOrLoad(k1, () -> value("t1-new")).await().indefinitely()).isEqualTo("t1-new");
        assertThat(cache.getOrLoad(k2, () -> value("t2-new")).await().indefinitely()).isEqualTo("t2");
    }

    @Test
    void loadStartedBeforeInvalidationDoesNotRepopulateStaleValue() {
        final var key = CacheKey.of("menu", T1, "m1");
        final var slowLoad = new CompletableFuture<String>();
        // Start a slow load: the loader's Uni completes only when we release it.
        final var running = cache.stats().loads();
        final var pending = CompletableFuture.supplyAsync(() ->
                cache.getOrLoad(key, () -> Uni.createFrom().completionStage(() -> slowLoad)).await().indefinitely());
        waitUntil(() -> cache.stats().loads() == running + 1);

        cache.invalidate(key).await().indefinitely();   // data changed while the load was in flight
        slowLoad.complete("stale");                       // the old read finishes afterwards
        assertThat(pending.join()).isEqualTo("stale");    // that caller still gets its answer...

        // ...but the stale value was not published: next call reloads.
        assertThat(cache.getOrLoad(key, () -> value("fresh")).await().indefinitely()).isEqualTo("fresh");
    }

    @Test
    void concurrentMissesRunTheLoaderOnce() throws Exception {
        final var key = CacheKey.of("menu", T1, "m1");
        final var calls = new AtomicInteger();
        final var gate = new CompletableFuture<String>();
        final var f1 = CompletableFuture.supplyAsync(() -> cache.getOrLoad(key,
                () -> { calls.incrementAndGet(); return Uni.createFrom().completionStage(() -> gate); }).await().indefinitely());
        waitUntil(() -> calls.get() == 1);
        final var f2 = CompletableFuture.supplyAsync(() -> cache.getOrLoad(key,
                () -> { calls.incrementAndGet(); return value("second-loader"); }).await().indefinitely());
        Thread.sleep(50);
        gate.complete("shared");
        assertThat(f1.get()).isEqualTo("shared");
        assertThat(f2.get()).isEqualTo("shared");
        assertThat(calls).hasValue(1);
    }

    @Test
    void foreignNamespaceKeyIsRejected() {
        assertThatThrownBy(() -> cache.getOrLoad(CacheKey.of("other", T1, "x"), () -> value("v")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tenantlessKeyRequiresExplicitPlatformFactory() {
        assertThatThrownBy(() -> CacheKey.of("menu", null, "k")).isInstanceOf(NullPointerException.class);
        assertThat(CacheKey.platform("menu", "k").asString()).isEqualTo("menu|platform|k");
    }

    private static void waitUntil(java.util.function.BooleanSupplier c) {
        final long end = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!c.getAsBoolean()) {
            if (System.nanoTime() > end) throw new AssertionError("condition not reached");
            Thread.onSpinWait();
        }
    }
}
