package tech.kayys.numou.security.application.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketRateLimiterTest {

    @Test
    void enforcesBurstCapacityAndRefillsAtConfiguredRate() {
        var clock = new AtomicLong(0L);
        var limiter = new TokenBucketRateLimiter(1, 2.0d, 100, clock::get);

        assertTrue(limiter.tryAcquire("client-a").allowed());

        var rejected = limiter.tryAcquire("client-a");
        assertFalse(rejected.allowed());
        assertEquals(500_000_000L, rejected.retryAfter().toNanos());

        clock.set(500_000_000L);
        assertTrue(limiter.tryAcquire("client-a").allowed());
    }

    @Test
    void keepsIndependentBudgetsForDifferentKeys() {
        var limiter = new TokenBucketRateLimiter(1, 1.0d, 100, () -> 0L);

        assertTrue(limiter.tryAcquire("client-a").allowed());
        assertFalse(limiter.tryAcquire("client-a").allowed());
        assertTrue(limiter.tryAcquire("client-b").allowed());
    }

    @Test
    void validatesConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenBucketRateLimiter(0, 1.0d, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new TokenBucketRateLimiter(1, 0.0d, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new TokenBucketRateLimiter(1, 1.0d, 0));
    }
}
