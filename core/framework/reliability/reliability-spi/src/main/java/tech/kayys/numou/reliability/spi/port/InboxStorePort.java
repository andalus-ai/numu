package tech.kayys.numou.reliability.spi.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.reliability.inbox.InboxArrival;
import tech.kayys.numou.reliability.inbox.InboxEntry;
import java.time.Instant;
import java.util.UUID;

/** Durable inbox contract with atomic claims and token-fenced completion. */
public interface InboxStorePort {
    Uni<InboxArrival> recordArrival(String eventId, String eventType);
    Uni<Boolean> alreadyHandled(String eventId);
    Uni<Boolean> claim(String eventId, UUID claimToken, Instant now, Instant leaseUntil);
    Uni<Void> releaseClaim(String eventId, UUID claimToken);
    Uni<Void> markHandled(String eventId, UUID claimToken);
    Uni<InboxEntry> findById(UUID entryId);
}
