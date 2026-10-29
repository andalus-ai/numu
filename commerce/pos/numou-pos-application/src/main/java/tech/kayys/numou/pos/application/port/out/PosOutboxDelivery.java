package tech.kayys.numou.pos.application.port.out;

import java.util.Objects;
import java.util.UUID;

/** A leased event. The lease token fences stale workers after lease expiry. */
public record PosOutboxDelivery(PosOutboxEvent event, int attemptCount, UUID leaseToken) {
    public PosOutboxDelivery {
        Objects.requireNonNull(event, "event cannot be null");
        Objects.requireNonNull(leaseToken, "leaseToken cannot be null");
        if (attemptCount < 1) throw new IllegalArgumentException("attemptCount must be positive");
    }
}
