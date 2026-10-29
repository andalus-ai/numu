package tech.kayys.numou.pos.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Persistence boundary for concurrent-safe outbox leasing and delivery outcomes. */
public interface PosOutboxDeliveryStore {
    List<PosOutboxDelivery> claimDue(Instant now, Instant leaseUntil, int limit);
    boolean markPublished(UUID eventId, UUID leaseToken, Instant publishedAt);
    boolean reschedule(UUID eventId, UUID leaseToken, Instant availableAt, String error, boolean dead);
}
