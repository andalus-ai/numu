package tech.kayys.numou.notification.application;

import tech.kayys.numou.notification.application.port.NotificationDeliveryProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Per-provider-instance protection against sustained provider outages and local request bursts.
 *
 * <p>This decorator is deliberately process-local. Hosts with multiple replicas or shared
 * provider quotas must additionally enforce distributed/provider-account limits at the
 * gateway or provider-adapter boundary.</p>
 */
public final class ResilientNotificationDeliveryProvider implements NotificationDeliveryProvider {
    private final NotificationDeliveryProvider delegate;
    private final Clock clock;
    private final int failureThreshold;
    private final Duration openDuration;
    private final int maxRequestsPerWindow;
    private final Duration rateWindow;

    private int consecutiveFailures;
    private CircuitState state = CircuitState.CLOSED;
    private Instant openedAt;
    private boolean probeInFlight;
    private Instant windowStartedAt;
    private int requestsInWindow;

    public ResilientNotificationDeliveryProvider(
            NotificationDeliveryProvider delegate,
            Clock clock,
            int failureThreshold,
            Duration openDuration,
            int maxRequestsPerWindow,
            Duration rateWindow) {
        this.delegate = Objects.requireNonNull(delegate, "delegate is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        if (delegate.providerCode() == null || delegate.providerCode().isBlank())
            throw new IllegalArgumentException("providerCode is required");
        if (failureThreshold < 1) throw new IllegalArgumentException("failureThreshold must be positive");
        this.openDuration = positive(openDuration, "openDuration");
        this.rateWindow = positive(rateWindow, "rateWindow");
        if (maxRequestsPerWindow < 1) throw new IllegalArgumentException("maxRequestsPerWindow must be positive");
        this.failureThreshold = failureThreshold;
        this.maxRequestsPerWindow = maxRequestsPerWindow;
    }

    @Override public String providerCode() { return delegate.providerCode(); }

    @Override
    public DeliveryResult deliver(DeliveryRequest request) {
        Objects.requireNonNull(request, "request is required");
        Instant now = Instant.now(clock);
        synchronized (this) {
            if (!reserveRateSlot(now)) return DeliveryResult.retryable("provider_rate_limited");
            if (state == CircuitState.OPEN) {
                if (now.isBefore(openedAt.plus(openDuration)))
                    return DeliveryResult.retryable("provider_circuit_open");
                state = CircuitState.HALF_OPEN;
            }
            if (state == CircuitState.HALF_OPEN) {
                if (probeInFlight) return DeliveryResult.retryable("provider_circuit_open");
                probeInFlight = true;
            }
        }

        final DeliveryResult result;
        try {
            result = Objects.requireNonNull(delegate.deliver(request), "provider result is required");
        } catch (RuntimeException failure) {
            recordFailure();
            return DeliveryResult.retryable("provider_error");
        }
        if (result.retryable()) recordFailure();
        else recordHealthyResponse();
        return result;
    }

    private synchronized boolean reserveRateSlot(Instant now) {
        if (windowStartedAt == null || !now.isBefore(windowStartedAt.plus(rateWindow))) {
            windowStartedAt = now;
            requestsInWindow = 0;
        }
        if (requestsInWindow >= maxRequestsPerWindow) return false;
        requestsInWindow++;
        return true;
    }

    private synchronized void recordFailure() {
        if (state == CircuitState.HALF_OPEN) {
            open();
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) open();
    }

    private synchronized void recordHealthyResponse() {
        consecutiveFailures = 0;
        if (state == CircuitState.HALF_OPEN || state == CircuitState.OPEN) {
            state = CircuitState.CLOSED;
            openedAt = null;
        }
        probeInFlight = false;
    }

    private void open() {
        state = CircuitState.OPEN;
        openedAt = Instant.now(clock);
        probeInFlight = false;
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isZero() || value.isNegative() || value.toMillis() < 1)
            throw new IllegalArgumentException(name + " must be at least one millisecond");
        return value;
    }

    private enum CircuitState { CLOSED, OPEN, HALF_OPEN }
}
