package tech.kayys.numou.foundation.testing.time;

import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Deterministic {@link DomainClock} test double.
 *
 * Lets tests control "now" instead of depending on wall-clock time,
 * and advance/rewind it to exercise time-dependent domain behavior
 * (subscription renewal, promotion expiry, accounting periods,
 * deadlines) without Thread.sleep or flaky timing assumptions.
 */
public final class FixedDomainClock implements DomainClock {

    private Instant current;

    private FixedDomainClock(Instant current) {
        this.current = Objects.requireNonNull(current, "current cannot be null");
    }

    public static FixedDomainClock at(Instant instant) {
        return new FixedDomainClock(instant);
    }

    public static FixedDomainClock epoch() {
        return new FixedDomainClock(Instant.EPOCH);
    }

    @Override
    public Instant now() {
        return current;
    }

    public void advanceBy(Duration duration) {
        Objects.requireNonNull(duration, "duration cannot be null");
        current = current.plus(duration);
    }

    public void setTo(Instant instant) {
        current = Objects.requireNonNull(instant, "instant cannot be null");
    }

}
