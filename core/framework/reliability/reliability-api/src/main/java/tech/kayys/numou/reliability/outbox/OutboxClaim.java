package tech.kayys.numou.reliability.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A durable outbox entry acquired by one relay worker for a bounded lease. */
public record OutboxClaim(OutboxEntry entry, UUID token, Instant leaseUntil) {
    public OutboxClaim {
        Objects.requireNonNull(entry, "entry cannot be null");
        Objects.requireNonNull(token, "token cannot be null");
        Objects.requireNonNull(leaseUntil, "leaseUntil cannot be null");
    }
}
