package tech.kayys.numou.notification.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.notification.application.port.NotificationDeliveryProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ResilientNotificationDeliveryProviderTest {
    private static final NotificationDeliveryProvider.DeliveryRequest REQUEST =
            new NotificationDeliveryProvider.DeliveryRequest(UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), "secret-device-token", "title", "body", "source");

    @Test void opensCircuitAfterConsecutiveRetryableFailuresAndAllowsOneRecoveryProbe() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        AtomicInteger calls = new AtomicInteger();
        NotificationDeliveryProvider delegate = provider(request -> {
            calls.incrementAndGet();
            return NotificationDeliveryProvider.DeliveryResult.retryable("upstream_unavailable");
        });
        var resilient = new ResilientNotificationDeliveryProvider(
                delegate, clock, 2, Duration.ofSeconds(30), 100, Duration.ofMinutes(1));

        assertEquals("upstream_unavailable", resilient.deliver(REQUEST).safeErrorCode());
        assertEquals("upstream_unavailable", resilient.deliver(REQUEST).safeErrorCode());
        assertEquals(2, calls.get());
        assertEquals("provider_circuit_open", resilient.deliver(REQUEST).safeErrorCode());
        assertEquals(2, calls.get());

        clock.advance(Duration.ofSeconds(31));
        assertEquals("upstream_unavailable", resilient.deliver(REQUEST).safeErrorCode());
        assertEquals(3, calls.get());
        assertEquals("provider_circuit_open", resilient.deliver(REQUEST).safeErrorCode());
        assertEquals(3, calls.get());
    }

    @Test void successfulHalfOpenProbeClosesCircuit() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        AtomicInteger calls = new AtomicInteger();
        NotificationDeliveryProvider delegate = provider(request -> calls.incrementAndGet() == 1
                ? NotificationDeliveryProvider.DeliveryResult.retryable("temporary")
                : NotificationDeliveryProvider.DeliveryResult.success());
        var resilient = new ResilientNotificationDeliveryProvider(
                delegate, clock, 1, Duration.ofSeconds(5), 100, Duration.ofMinutes(1));

        assertTrue(resilient.deliver(REQUEST).retryable());
        clock.advance(Duration.ofSeconds(6));
        assertTrue(resilient.deliver(REQUEST).accepted());
        assertTrue(resilient.deliver(REQUEST).accepted());
        assertEquals(3, calls.get());
    }

    @Test void enforcesBoundedLocalRequestWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        AtomicInteger calls = new AtomicInteger();
        var resilient = new ResilientNotificationDeliveryProvider(
                provider(request -> { calls.incrementAndGet(); return NotificationDeliveryProvider.DeliveryResult.success(); }),
                clock, 3, Duration.ofSeconds(10), 2, Duration.ofSeconds(1));

        assertTrue(resilient.deliver(REQUEST).accepted());
        assertTrue(resilient.deliver(REQUEST).accepted());
        assertEquals("provider_rate_limited", resilient.deliver(REQUEST).safeErrorCode());
        assertEquals(2, calls.get());
        clock.advance(Duration.ofSeconds(1));
        assertTrue(resilient.deliver(REQUEST).accepted());
        assertEquals(3, calls.get());
    }

    @Test void sanitizesThrownProviderExceptions() {
        var resilient = new ResilientNotificationDeliveryProvider(
                provider(request -> { throw new IllegalStateException("contains credentials"); }),
                Clock.systemUTC(), 2, Duration.ofSeconds(10), 10, Duration.ofSeconds(1));
        var result = resilient.deliver(REQUEST);
        assertEquals("provider_error", result.safeErrorCode());
        assertTrue(result.retryable());
        assertFalse(result.safeErrorCode().contains("credentials"));
    }

    private static NotificationDeliveryProvider provider(
            java.util.function.Function<NotificationDeliveryProvider.DeliveryRequest,
                    NotificationDeliveryProvider.DeliveryResult> action) {
        return new NotificationDeliveryProvider() {
            @Override public String providerCode() { return "test-provider"; }
            @Override public DeliveryResult deliver(DeliveryRequest request) { return action.apply(request); }
        };
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
